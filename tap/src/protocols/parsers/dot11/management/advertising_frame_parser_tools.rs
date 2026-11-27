use std::sync::{Arc, Mutex};
use anyhow::{bail, Error};
use crate::wireless::dot11::frames::{CipherSuite, CipherSuites, CountryInformation, Dot11Capabilities, Dot11Frame, EncryptionProtocol, InfraStructureType, KeyManagementMode, PmfMode, PwnagotchiData, RegulatoryEnvironment, SecurityInformation, TaggedParameters};
use bitvec::{view::BitView, order::Lsb0};
use byteorder::{ByteOrder, LittleEndian};
use log::{debug, trace, warn};
use sha2::{Digest, Sha256};
use crate::messagebus::bus::Bus;
use crate::metrics::Metrics;
use crate::protocols::detection::dot11_tagged_parameters_tagger::tag_advertisement_frame_tags;
use crate::protocols::parsers::dot11::management::channel_switch_element_parser;
use crate::protocols::parsers::dot11::management::channel_switch_element_parser::ChannelElements;
use crate::wireless::tags::Tag;

pub fn parse_capabilities(mask: &[u8]) -> Result<Dot11Capabilities, Error> {
    if mask.len() != 2 {
        bail!("Capabilities flags must be 2 bytes, provided <{}>.", mask.len())
    }

    let bmask = mask.view_bits::<Lsb0>();

    /*
     * We are using unwrap() here because the bounds length
     * above ensures enough bits for the access ops.
     */

    let ess = *bmask.get(0).unwrap();
    let ibss = *bmask.get(1).unwrap();

    let infrastructure_type: InfraStructureType;
    if ess && !ibss {
        infrastructure_type = InfraStructureType::AccessPoint;
    } else if !ess && ibss {
        infrastructure_type = InfraStructureType::AdHoc;
    } else {
        infrastructure_type = InfraStructureType::Invalid;
        trace!("Invalid beacon infrastructure type.")
    }

    Ok(Dot11Capabilities {
        infrastructure_type,
        privacy: *bmask.get(4).unwrap(),
        short_preamble: *bmask.get(5).unwrap(),
        pbcc: *bmask.get(6).unwrap(),
        channel_agility: *bmask.get(7).unwrap(),
        short_slot_time: *bmask.get(10).unwrap(),
        dsss_ofdm: *bmask.get(13).unwrap(),
    })
}

pub struct TaggedParameterParserData {
    pub tagged_parameters: TaggedParameters,
    pub security: Vec<SecurityInformation>,
    pub security_bytes: Vec<u8>,
    pub has_wps: bool,
    pub tags: Vec<Tag>
}

// None for a hidden SSID. Some access points send it empty, others as NULL bytes of its length.
fn read_ssid(data: &[u8]) -> Option<String> {
    let ssid = String::from_utf8_lossy(data).to_string();

    if ssid.chars().all(|c| c.is_whitespace() || c.is_control()) {
        None
    } else {
        Some(ssid)
    }
}

// The SSID of a frame is that of its first SSID element. Clients ignore any further one, and so does the tap.
pub(crate) fn read_first_ssid(elements: &[u8]) -> Option<String> {
    let mut cursor = 0;

    while cursor + 2 <= elements.len() {
        let number = elements[cursor];
        let length = elements[cursor + 1] as usize;
        cursor += 2;

        if elements.len() < cursor + length {
            return None;
        }

        if number == 0 {
            return read_ssid(&elements[cursor..cursor + length]);
        }

        cursor += length;
    }

    None
}

#[allow(clippy::single_match)]
pub fn parse_tagged_parameters(payload: &[u8],
                               bssid: String,
                               rssi: Option<i8>,
                               frequency: Option<u16>,
                               bus: Arc<Bus>,
                               metrics: Arc<Mutex<Metrics>>) -> Result<TaggedParameterParserData, Error> {
    let ssid = payload.get(36..).and_then(read_first_ssid);
    let mut supported_rates: Option<Vec<f32>> = Option::None;
    let mut extended_supported_rates: Option<Vec<f32>> = Option::None;
    let mut country_information: Option<CountryInformation> = Option::None;
    let mut ht_capabilities: Option<Vec<u8>> = Option::None;
    let mut extended_capabilities: Option<Vec<u8>> = Option::None;
    let mut security: Vec<SecurityInformation> = Vec::new();
    let mut security_bytes: Vec<u8> = Vec::new(); // Raw bytes for quick fingerprint calculation.
    let mut pwnagotchi_parts: Vec<String> = Vec::new();
    let mut tags: Vec<Tag> = Vec::new();

    // WPS.
    let mut has_wps = false;

    let mut cursor: usize = 36;
    if payload.len() > cursor+2 {
        loop {
            // we need at least 2 bytes for the tag number and length. current and next byte.
            if cursor + 1 >= payload.len() {
                break;
            }

            let number = &payload[cursor];
            cursor += 1;
            let length = payload[cursor] as usize;
            cursor += 1;

            if payload.len() < cursor+length {
                trace!("Invalid tag length reported. Not calculating any more tagged parameters for this frame.");
                break;
            }

            let data = &payload[cursor..cursor+length];
            cursor += length;

            match number {
                1 => {
                    // Supported rates.
                    supported_rates = Option::Some(parse_supported_rates(data));
                }
                7 => {
                    // Country information.
                    match parse_country_information(data) {
                        Ok(ci) => {
                            country_information = Option::Some(ci);
                        },
                        Err(e) => trace!("Could not parse country information: {}", e)
                    }
                }
                45 => {
                    // HT capabilities.
                    ht_capabilities = Option::Some(data.to_vec());
                },
                48 => {
                    // RSN. (WPA 2/3)
                    let suites: CipherSuites = match parse_wpa_security(data) {
                        Ok(suites) => suites,
                        Err(e) => {
                            warn!("Could not parse RSN information: {}", e);
                            continue;
                        }
                    };

                    if data.len() < suites.cursor+2 {
                        warn!("Could not parse PMF information: Payload too short.");
                        continue;
                    }

                    let pmf = match parse_pmf_mode(&data[suites.cursor..data.len()]) {
                        Ok(pmf) => pmf,
                        Err(e) => {
                            warn!("Could not parse PMF mode: {}", e);
                            continue;
                        }
                    };

                    let operating_channel_validation = parse_operating_channel_validation(
                        &data[suites.cursor..data.len()]
                    );

                    // A suite combination that is no known protocol must not make the network look open.
                    let protocols = match decide_wpa_identifier(&suites, &pmf) {
                        Ok(protocol) => vec![protocol],
                        Err(e) => {
                            trace!("Could not decide RSN protocol: {}", e);
                            vec![EncryptionProtocol::Invalid]
                        }
                    };

                    security.push(SecurityInformation {
                        protocols,
                        pmf,
                        suites: Option::Some(suites),
                        operating_channel_validation,
                        beacon_protection: false
                    });
                    security_bytes.extend(data);
                }
                50 => {
                    // Extended supported rates.
                    extended_supported_rates = Option::Some(parse_extended_supported_rates(data));
                },
                127 => {
                    // Extended capabilities.
                    extended_capabilities = Option::Some(data.to_vec());
                }
                221 => {
                    // Vendor tags.
                    if data.len() < 4 {
                        trace!("Vendor tag too short to hold even OUI tag. Skipping.");
                        continue;
                    }

                    match &data[0..3] {
                        // Microsoft Corp.
                        [0x00,0x50,0xf2] => {
                            match &data[3] {
                                // WPS.
                                0x04 => has_wps = true,

                                // WPA 1.
                                0x01 => {
                                    let suites: CipherSuites = match parse_wpa_security(&data[4..data.len()]) {
                                        Ok(suites) => suites,
                                        Err(e) => {
                                            trace!("Could not parse WPA1 information: {}", e);
                                            continue;
                                        }
                                    };

                                    security.push(SecurityInformation {
                                        protocols: vec![EncryptionProtocol::WPA1],
                                        suites: Option::Some(suites),
                                        pmf: PmfMode::Unavailable,
                                        operating_channel_validation: false,
                                        beacon_protection: false
                                    });
                                    security_bytes.extend(data);
                                }

                                _ => {}
                            }
                        },
                        _ => {
                            // Potentially tag anything else / anything unknown.
                            tags.extend(tag_advertisement_frame_tags(
                                data, bssid.clone(), rssi, frequency, bus.clone(), metrics.clone())
                            );
                        }
                    }
                },
                222 => {
                    // Pwnagotchi announcement parasite protocol.
                    tags.push(Tag::Pwnagotchi);
                    pwnagotchi_parts.push(String::from_utf8_lossy(data).to_string());
                }
                _ => {}
            };
        }
    } else {
        trace!("Tagged parameters are too short. Not calculating for this frame.");
    }

    let mut channel_elements = match payload.len() > 36 {
        true => channel_switch_element_parser::parse_elements(&payload[36..]),
        false => ChannelElements::default()
    };

    // With the order bit set, a receiver may expect an HT Control field before the body, so both places are read.
    if channel_elements.announcement.is_none() && payload.len() > 40 && payload[1] & 0b1000_0000 != 0 {
        let shifted = channel_switch_element_parser::parse_elements(&payload[40..]);
        if shifted.announcement.is_some() {
            channel_elements = shifted;
        }
    }

    let beacon_protection = parse_beacon_protection(&extended_capabilities);
    for sec in security.iter_mut() {
        sec.beacon_protection = beacon_protection;
    }

    let pwnagotchi_data = match pwnagotchi_parts.is_empty() {
        false => {
            match serde_json::from_str::<PwnagotchiData>(&pwnagotchi_parts.join("")) {
                Ok(json) => Some(json),
                Err(e) => {
                    warn!("Could not deserialize Pwnagotchi payload: {}", e);
                    None
                }
            }
        }
        true => None
    };

    let tagged_parameters = TaggedParameters {
        ssid,
        supported_rates,
        extended_supported_rates,
        country_information,
        ht_capabilities,
        extended_capabilities,
        pwnagotchi_data,
        channel_switch: channel_elements.announcement,
        current_frequency: channel_elements.current_frequency
    };

    // Mark as open network when no security mechanisms were detected.
    if security.is_empty() {
        security.push(SecurityInformation {
            protocols: vec![EncryptionProtocol::None],
            suites: Option::None,
            pmf: PmfMode::Unavailable,
            operating_channel_validation: false,
            beacon_protection
        });
    }

    Ok(TaggedParameterParserData {
        tagged_parameters,
        security,
        security_bytes,
        has_wps,
        tags
    })
}

fn parse_country_information(data: &[u8]) -> Result<CountryInformation, Error> {
    if !data.len() != 6 {
        bail!("Country information must be 8 bytes, provided <{}>.", data.len());
    }

    let country_code = String::from_utf8_lossy(&data[0..2]).to_string();

    let environment = match &data[2] {
        32 => RegulatoryEnvironment::All,
        73 => RegulatoryEnvironment::Indoors,
        79 => RegulatoryEnvironment::Outdoors,
        _ => RegulatoryEnvironment::Unknown
    };

    let first_channel = data[3];
    let channel_count = data[4];
    let max_transmit_power = data[5];

    Ok(CountryInformation {
        country_code,
        environment,
        first_channel,
        channel_count,
        max_transmit_power,
    })
}


fn parse_supported_rates(data: &[u8]) -> Vec<f32> {
    let mut rates: Vec<f32> = Vec::new();

    for rate in data {
        match rate ^ 0b1000_0000 {
            2   => rates.push(1.0),
            4   => rates.push(2.0),
            11  => rates.push(5.5),
            12  => rates.push(6.0),
            18  => rates.push(9.0),
            22  => rates.push(11.0),
            24  => rates.push(12.0),
            36  => rates.push(18.0),
            44  => rates.push(22.0),
            48  => rates.push(24.0),
            66  => rates.push(33.0),
            72  => rates.push(36.0),
            96  => rates.push(48.0),
            108 => rates.push(54.0),
            _   => trace!("Invalid supported rate <{}>", rate)
        }
    }

    rates
}

fn parse_extended_supported_rates(data: &[u8]) -> Vec<f32> {
    let mut rates: Vec<f32> = Vec::new();

    for rate in data {
        rates.push((rate >> 1) as f32);
    }

    rates
}

fn parse_wpa_security(data: &[u8]) -> Result<CipherSuites, Error> {
    if data.len() < 2 {
        bail!("Not enough data to read RSN/WPA version.")
    }

    if LittleEndian::read_u16(&data[0..2]) != 1 {
        bail!("Unsupported RSN/WPA version <{:?}>.", &data)
    }

    if data.len() < 6 {
        bail!{"RSN/WPA info doesn't fit group cipher suite."}
    }

    // Group cipher.
    let group_cipher = match parse_cipher_suite(&data[2..6]) {
        Ok(cs) => cs,
        Err(e) => bail!("Could not parse group cipher suite: {}", e)
    };

    if data.len() < 8 {
        bail!{"RSN doesn't fit pairwise cipher suite count."};
    }

    let mut pairwise_ciphers: Vec<CipherSuite> = Vec::new();
    let pair_suite_count = LittleEndian::read_u16(&data[6..8]) as usize;
    let mut cursor: usize = 8;
    for _ in 0..pair_suite_count {
        if data.len() < cursor+4 {
            bail!{"RSN doesn't fit pairwise cipher suites."};
        }

        match parse_cipher_suite(&data[cursor..cursor+4]) {
            Ok(cipher) => pairwise_ciphers.push(cipher),
            Err(e) => bail!("Could not parse pairwise cipher: {}", e)
        }
        cursor += 4;
    }

    if data.len() < cursor+2 {
        bail!{"RSN doesn't fit key management suite count."};
    }

    let mut key_management_modes: Vec<KeyManagementMode> = Vec::new();
    let key_management_suite_count = LittleEndian::read_u16(&data[cursor..cursor+2]) as usize;
    cursor+=2;
    for _ in 0..key_management_suite_count {
        if data.len() < cursor+4 {
            bail!{"RSN doesn't fit key management suites."};
        }

        match parse_key_management_suite(&data[cursor..cursor+4]) {
            Ok(cipher) => key_management_modes.push(cipher),
            Err(e) => bail!("Could not parse key management cipher: {}", e)
        }
        cursor += 4;
    }

    Ok (CipherSuites {
        cursor,
        group_cipher,
        pairwise_ciphers,
        key_management_modes
    })
}

fn parse_key_management_suite(data: &[u8]) -> Result<KeyManagementMode, Error> {
    if data.len() != 4 {
        bail!("Invalid cipher suite length: <{}>.", data.len())
    }

    if data[0..3] != [0x00, 0x0f, 0xac] && data[0..3] != [0x00, 0x50, 0xf2] {
        bail!("Invalid cipher suite OUI: <{:02x?}>.", data)
    }

    match data[3] {
        1 => Ok(KeyManagementMode::DOT1X),
        2 => Ok(KeyManagementMode::PSK),
        3 => Ok(KeyManagementMode::DOT1X_FT),
        4 => Ok(KeyManagementMode::PSK_FT),
        5 => Ok(KeyManagementMode::DOT1X_SHA256),
        6 => Ok(KeyManagementMode::PSK_SHA256),
        7 => Ok(KeyManagementMode::TDLS),
        8 => Ok(KeyManagementMode::SAE),
        9 => Ok(KeyManagementMode::SAE_FT),
        10 => Ok(KeyManagementMode::AP_PEER),
        11 => Ok(KeyManagementMode::DOT1X_B_EAP_SHA256),
        12 => Ok(KeyManagementMode::DOT1X_B_EAP_SHA384), // CNSA
        13 => Ok(KeyManagementMode::DOT1X_FT_SHA384), // CNSA
        _ => {
            debug!("Unknown key management mode: {}", data[3]);
            Ok(KeyManagementMode::Unknown)
        }
    }
}

fn parse_pmf_mode(data: &[u8]) -> Result<PmfMode, Error> {
    if data.len() < 2 {
        bail!("Invalid PMF mode length: <{}>", data.len())
    }

    let bmask = data[0..2].view_bits::<Lsb0>();

    let required = *bmask.get(6).unwrap();
    let capable = *bmask.get(7).unwrap();

    if required {
        Ok(PmfMode::Required)
    } else if capable {
        Ok(PmfMode::Optional)
    } else {
        Ok(PmfMode::Disabled)
    }
}

// OCVC is bit 14 of the RSN capabilities.
fn parse_operating_channel_validation(rsn_capabilities: &[u8]) -> bool {
    rsn_capabilities.len() >= 2 && rsn_capabilities[1] & 0b0100_0000 != 0
}

// Beacon Protection Enabled is bit 84 of the extended capabilities.
fn parse_beacon_protection(extended_capabilities: &Option<Vec<u8>>) -> bool {
    match extended_capabilities {
        Some(caps) => caps.len() > 10 && caps[10] & 0b0001_0000 != 0,
        None => false
    }
}

fn parse_cipher_suite(data: &[u8]) -> Result<CipherSuite, Error> {
    if data.len() != 4 {
        bail!("Invalid cipher suite length: <{}>.", data.len())
    }

    if data[0..3] != [0x00, 0x0f, 0xac] && data[0..3] != [0x00, 0x50, 0xf2] {
        bail!("Invalid cipher suite OUI: <{:02x?}>.", data)
    }

    match data[3] {
        0x00 => Ok(CipherSuite::None),
        0x01 => Ok(CipherSuite::WEP),
        0x02 => Ok(CipherSuite::TKIP),
        0x04 => Ok(CipherSuite::CCMP),
        0x05 => Ok(CipherSuite::WEP104),
        0x06 => Ok(CipherSuite::BIPCMAC128),
        0x08 => Ok(CipherSuite::GCMP128),
        0x09 => Ok(CipherSuite::GCMP256),
        0x0a => Ok(CipherSuite::CCMP256),
        0x0b => Ok(CipherSuite::BIPGMAC128),
        0x0c => Ok(CipherSuite::BIPGMAC256),
        0x0d => Ok(CipherSuite::BIPCMAC256),
        _ => Ok(CipherSuite::Unknown)
    }
}

pub fn calculate_fingerprint(bssid: &str,
                             caps: &Dot11Capabilities,
                             tagged_params: &TaggedParameters,
                             has_wps: &bool,
                             security: &Vec<u8>) -> String {
    let mut factors: Vec<u8> = Vec::new();

    // Infrastructure type.
    match caps.infrastructure_type {
        InfraStructureType::Invalid => factors.push(0),
        InfraStructureType::AccessPoint => factors.push(1),
        InfraStructureType::AdHoc => factors.push(2),
    }

    // Additional capabitilies information.
    factors.push(caps.privacy as u8);
    factors.push(caps.short_preamble as u8);
    factors.push(caps.pbcc as u8);
    factors.push(caps.channel_agility as u8);
    factors.push(caps.short_slot_time as u8);
    factors.push(caps.dsss_ofdm as u8);

    // Supported rates.
    if tagged_params.supported_rates.is_some() {
        for rate in tagged_params.supported_rates.as_ref().unwrap() {
            factors.extend(rate.to_le_bytes());
        }
    }

    // Extended spported rates.
    if tagged_params.extended_supported_rates.is_some() {
        for rate in tagged_params.extended_supported_rates.as_ref().unwrap() {
            factors.extend(rate.to_le_bytes());
        }
    }

    // HT capabilities.
    if tagged_params.ht_capabilities.is_some() {
        factors.extend(tagged_params.ht_capabilities.as_ref().unwrap());
    }

    // Extended capabilities.
    if tagged_params.extended_capabilities.is_some() {
        factors.extend(tagged_params.extended_capabilities.as_ref().unwrap());
    }

    // WPS
    factors.push(*has_wps as u8);

    // Security / Encryption.
    factors.extend(security);

    let hash = Sha256::digest(factors);

    trace!("Fingerprint debug: [{}] Caps: [{:?}] TagParams: [{:?}] WPS: [{}]", bssid, caps, tagged_params, has_wps);

    hash.iter().map(|b| format!("{:02x}", b)).collect()
}

pub fn decide_wpa_identifier(suites: &CipherSuites, pmf: &PmfMode) -> Result<EncryptionProtocol, Error> {
    if suites.key_management_modes.len() == 1
        && *suites.key_management_modes.get(0).unwrap() == KeyManagementMode::Unknown {
        return Ok(EncryptionProtocol::Invalid)
    }

    // WPA3-Enterprise 192?
    if suites.key_management_modes.len() == 1 && suites.pairwise_ciphers.len() == 1
        && (*suites.key_management_modes.get(0).unwrap() == KeyManagementMode::DOT1X_B_EAP_SHA384
        || *suites.key_management_modes.get(0).unwrap() == KeyManagementMode::DOT1X_FT_SHA384)
        && suites.group_cipher == CipherSuite::GCMP256
        && *suites.pairwise_ciphers.get(0).unwrap() == CipherSuite::GCMP256
        && *pmf == PmfMode::Required {
        return Ok(EncryptionProtocol::WPA3EnterpriseCNSA)
    }

    // WPA2/3 Transition Mode.
    if (suites.key_management_modes.contains(&KeyManagementMode::SAE)
        || suites.key_management_modes.contains(&KeyManagementMode::SAE_FT)
        || suites.key_management_modes.contains(&KeyManagementMode::AP_PEER))
        && (suites.key_management_modes.contains(&KeyManagementMode::PSK)
        || suites.key_management_modes.contains(&KeyManagementMode::PSK_FT)
        || suites.key_management_modes.contains(&KeyManagementMode::PSK_SHA256)
        || suites.key_management_modes.contains(&KeyManagementMode::TDLS))
        && *pmf == PmfMode::Optional {
        return Ok(EncryptionProtocol::WPA3Transition)
    }

    // WPA3-Enterprise
    if suites.key_management_modes.contains(&KeyManagementMode::DOT1X_SHA256)
        || suites.key_management_modes.contains(&KeyManagementMode::DOT1X_B_EAP_SHA256) {
        return if *pmf == PmfMode::Required {
            Ok(EncryptionProtocol::WPA3Enterprise)
        } else {
            Ok(EncryptionProtocol::Invalid)
        }
    }

    // WPA3-Personal
    if suites.key_management_modes.contains(&KeyManagementMode::SAE)
        || suites.key_management_modes.contains(&KeyManagementMode::SAE_FT)
        || suites.key_management_modes.contains(&KeyManagementMode::AP_PEER) {
        return if *pmf == PmfMode::Required {
            Ok(EncryptionProtocol::WPA3Personal)
        } else {
            Ok(EncryptionProtocol::Invalid)
        }
    }

    // WPA2-Enterprise
    if suites.key_management_modes.contains(&KeyManagementMode::DOT1X)
        || suites.key_management_modes.contains(&KeyManagementMode::DOT1X_FT) {
        return Ok(EncryptionProtocol::WPA2Enterprise)
    }

    // WPA2-Personal
    if suites.key_management_modes.contains(&KeyManagementMode::PSK)
        || suites.key_management_modes.contains(&KeyManagementMode::PSK_FT)
        || suites.key_management_modes.contains(&KeyManagementMode::PSK_SHA256)
        || suites.key_management_modes.contains(&KeyManagementMode::TDLS){
        return Ok(EncryptionProtocol::WPA2Personal)
    }

    bail!("Unknown WPA identifier for suite and PMF: {:?}, {:?}", suites, pmf);
}

pub fn decide_encryption_protocol(capabilities: &Dot11Capabilities,
                                  tagged_data: &mut TaggedParameterParserData) -> bool {
    if capabilities.privacy && tagged_data.security.is_empty() {
        tagged_data.security.push(
            SecurityInformation {
                protocols: vec![EncryptionProtocol::WEP],
                suites: Option::None,
                pmf: PmfMode::Unavailable,
                operating_channel_validation: false,
                beacon_protection: false
            }
        )
    };

    false
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_and_null_byte_ssids_are_hidden() {
        assert_eq!(read_ssid(&[]), None);
        assert_eq!(read_ssid(&[0, 0, 0, 0, 0]), None);
        assert_eq!(read_ssid(b"   "), None);
        assert_eq!(read_ssid(&[0, 0x20, 0]), None);
        assert_eq!(read_ssid(b"corp"), Some("corp".to_string()));
        assert_eq!(read_ssid(b" corp "), Some(" corp ".to_string()));
        assert_eq!(read_ssid(&[b'a', 0, b'b']), Some("a\0b".to_string()));
    }

    #[test]
    fn first_ssid_element_decides() {
        let rates = [1, 1, 0x82];
        let named = |name: &[u8]| [&[0, name.len() as u8], name].concat();

        assert_eq!(read_first_ssid(&named(b"corp")), Some("corp".to_string()));
        assert_eq!(read_first_ssid(&[named(b"corp"), rates.to_vec(), named(b"")].concat()), Some("corp".to_string()));
        assert_eq!(read_first_ssid(&[named(b"corp"), named(&[0, 0, 0, 0])].concat()), Some("corp".to_string()));
        assert_eq!(read_first_ssid(&[named(b"corp"), named(b"evil")].concat()), Some("corp".to_string()));
        assert_eq!(read_first_ssid(&[named(b""), named(b"corp")].concat()), None);
        assert_eq!(read_first_ssid(&[named(&[0, 0, 0, 0]), named(b"corp")].concat()), None);
        assert_eq!(read_first_ssid(&[rates.to_vec(), named(b"corp")].concat()), Some("corp".to_string()));
        assert_eq!(read_first_ssid(&rates), None);
        assert_eq!(read_first_ssid(&[]), None);
        assert_eq!(read_first_ssid(&[0]), None);
        assert_eq!(read_first_ssid(&[0, 4, b'c', b'o']), None);
        assert_eq!(read_first_ssid(&[1, 9, 0x82, 0, 4, b'c', b'o', b'r', b'p']), None);
    }

    #[test]
    fn reads_operating_channel_validation_from_rsn_capabilities() {
        assert!(parse_operating_channel_validation(&[0x8c, 0x40]));
        assert!(!parse_operating_channel_validation(&[0xcc, 0x00]));
        assert!(!parse_operating_channel_validation(&[0xff, 0xbf]));
        assert!(!parse_operating_channel_validation(&[0xff]));
    }

    #[test]
    fn rejects_truncated_rsn_elements() {
        assert!(parse_wpa_security(&[1, 0, 0x00, 0x0f, 0xac]).is_err());
        assert!(parse_wpa_security(&[1, 0, 0x00, 0x0f, 0xac, 4, 2, 0]).is_err());
        assert!(parse_wpa_security(&[1, 0, 0x00, 0x0f, 0xac, 4, 1, 0, 0x00, 0x0f, 0xac]).is_err());
        assert!(parse_wpa_security(&[1, 0, 0x00, 0x0f, 0xac, 4, 1, 0, 0x00, 0x0f, 0xac, 4, 1]).is_err());
        assert!(parse_wpa_security(&[1, 0, 0x00, 0x0f, 0xac, 4, 1, 0, 0x00, 0x0f, 0xac, 4, 1, 0]).is_err());
        assert!(parse_wpa_security(&[1, 0, 0x00, 0x0f, 0xac, 4, 1, 0, 0x00, 0x0f, 0xac, 4, 1, 0, 0x00, 0x0f]).is_err());

        let suites = parse_wpa_security(&[1, 0, 0x00, 0x0f, 0xac, 4, 1, 0, 0x00, 0x0f, 0xac, 4, 1, 0, 0x00, 0x0f, 0xac, 2])
            .unwrap();
        assert_eq!(suites.key_management_modes, vec![KeyManagementMode::PSK]);
    }

    #[test]
    fn rsn_without_key_management_is_no_known_protocol() {
        let suites = parse_wpa_security(&[1, 0, 0x00, 0x0f, 0xac, 4, 0, 0, 0, 0, 0, 0]).unwrap();
        assert!(decide_wpa_identifier(&suites, &PmfMode::Disabled).is_err());
    }

    #[test]
    fn reads_beacon_protection_from_extended_capabilities() {
        assert!(parse_beacon_protection(&Some(vec![0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10])));
        assert!(!parse_beacon_protection(&Some(vec![0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xef])));
        assert!(!parse_beacon_protection(&Some(vec![0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff])));
        assert!(!parse_beacon_protection(&None));
    }
}
