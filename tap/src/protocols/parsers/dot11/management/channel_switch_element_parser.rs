use crate::helpers::network::{dot11_channel_to_frequency, Nl80211Band};
use crate::wireless::dot11::frames::{ChannelSwitchAnnouncement, ChannelSwitchAnnouncementType};

const SUPPORTED_RATES: u8 = 1;
const DS_PARAMETER_SET: u8 = 3;
const COUNTRY: u8 = 7;
const CHANNEL_SWITCH_ANNOUNCEMENT: u8 = 37;
const ERP: u8 = 42;
const HT_CAPABILITIES: u8 = 45;
const EXTENDED_SUPPORTED_RATES: u8 = 50;
const EXTENDED_CHANNEL_SWITCH_ANNOUNCEMENT: u8 = 60;
const HT_OPERATION: u8 = 61;
const EXTENSION: u8 = 255;
const EXTENSION_HE_OPERATION: u8 = 36;

const HT_OPERATION_LENGTH: usize = 22;
const HIGHEST_6GHZ_CHANNEL: u8 = 233;

// In units of 500 kbit/s, without the bit that marks a rate as basic.
const DSSS_RATES: [u8; 4] = [2, 4, 11, 22];

// The values above the rates are BSS membership selectors. This one says nothing about the PHY.
const SAE_HASH_TO_ELEMENT_ONLY: u8 = 123;

/*
 * Access points announce a switch in both elements, in which case the extended one only adds the operating
 * class. An announcement is trailing if it is the last element of the frame and follows an element with a
 * higher number, which is where a tool appends it and not where an access point places it.
 *
 * The country and if the frame advertises 802.11b rates only are read from the same frame, because channel 14
 * is open to such access points in Japan and to nothing else. They are unknown in action frames.
 *
 * The current frequency is the channel the frame says it was sent on, because a tap also records frames under
 * other frequencies than the one they were sent on. A frame that names no channel, or several that disagree, has
 * none: clients differ in which one they would follow, so a forged frame could show the tap another channel than
 * its victims. The first of repeated elements counts, like for most clients.
 */
#[derive(Debug, Default, PartialEq, Eq)]
pub struct ChannelElements {
    pub announcement: Option<ChannelSwitchAnnouncement>,
    pub current_frequency: Option<u16>
}

pub fn parse(elements: &[u8]) -> Option<ChannelSwitchAnnouncement> {
    parse_elements(elements).announcement
}

pub fn parse_elements(elements: &[u8]) -> ChannelElements {
    let mut standard: Option<ChannelSwitchAnnouncement> = None;
    let mut extended: Option<ChannelSwitchAnnouncement> = None;
    let mut highest_number: u8 = 0;
    let mut has_rates = false;
    let mut has_ofdm = false;
    let mut country_code: Option<String> = None;
    let mut current_frequencies: Vec<u16> = Vec::new();
    let mut cursor: usize = 0;

    while cursor + 2 <= elements.len() {
        let number = elements[cursor];
        let length = elements[cursor+1] as usize;
        cursor += 2;

        if cursor + length > elements.len() {
            break;
        }

        let data = &elements[cursor..cursor+length];
        cursor += length;

        if let Some(announcement) = standard.as_mut() {
            announcement.is_trailing_element = false;
        }
        if let Some(announcement) = extended.as_mut() {
            announcement.is_trailing_element = false;
        }

        match number {
            CHANNEL_SWITCH_ANNOUNCEMENT if length == 3 && standard.is_none() => {
                standard = Some(ChannelSwitchAnnouncement {
                    announcement_type: ChannelSwitchAnnouncementType::Standard,
                    switch_mode: data[0],
                    new_operating_class: None,
                    new_channel: data[1],
                    switch_count: data[2],
                    is_trailing_element: highest_number > number,
                    dsss_only: None,
                    country_code: None,
                    current_frequency: None
                });
            },
            EXTENDED_CHANNEL_SWITCH_ANNOUNCEMENT if length == 4 && extended.is_none() => {
                extended = Some(ChannelSwitchAnnouncement {
                    announcement_type: ChannelSwitchAnnouncementType::Extended,
                    switch_mode: data[0],
                    new_operating_class: Some(data[1]),
                    new_channel: data[2],
                    switch_count: data[3],
                    is_trailing_element: highest_number > number,
                    dsss_only: None,
                    country_code: None,
                    current_frequency: None
                });
            },
            SUPPORTED_RATES | EXTENDED_SUPPORTED_RATES => {
                has_rates = true;
                has_ofdm = has_ofdm || data.iter()
                    .map(|rate| rate & 0b0111_1111)
                    .any(|rate| !DSSS_RATES.contains(&rate) && rate != SAE_HASH_TO_ELEMENT_ONLY);
            },
            ERP | HT_CAPABILITIES => has_ofdm = true,
            DS_PARAMETER_SET if length == 1 => {
                current_frequencies.extend(channel_to_frequency(data[0]));
            },
            HT_OPERATION if length >= HT_OPERATION_LENGTH => {
                current_frequencies.extend(channel_to_frequency(data[0]));
            },
            EXTENSION if length >= 1 && data[0] == EXTENSION_HE_OPERATION => {
                current_frequencies.extend(parse_he_6ghz_primary_channel(&data[1..])
                    .filter(|channel| *channel <= HIGHEST_6GHZ_CHANNEL)
                    .and_then(|channel| dot11_channel_to_frequency(u16::from(channel), Nl80211Band::Band6GHz).ok()));
            },
            COUNTRY if length >= 2 && data[0].is_ascii_uppercase() && data[1].is_ascii_uppercase() => {
                country_code = Some(String::from_utf8_lossy(&data[0..2]).to_string());
            },
            _ => {}
        }

        highest_number = highest_number.max(number);
    }

    current_frequencies.dedup();
    let current_frequency = match current_frequencies.as_slice() {
        [frequency] => Some(*frequency),
        _ => None
    };

    let announcement = match (standard, extended) {
        (Some(standard), Some(extended)) => Some(ChannelSwitchAnnouncement {
            new_operating_class: extended.new_operating_class,
            is_trailing_element: standard.is_trailing_element || extended.is_trailing_element,
            ..standard
        }),
        (Some(standard), None) => Some(standard),
        (None, Some(extended)) => Some(extended),
        (None, None) => None
    };

    ChannelElements {
        announcement: announcement.map(|announcement| ChannelSwitchAnnouncement {
            dsss_only: if has_rates { Some(!has_ofdm) } else { None },
            country_code,
            current_frequency,
            ..announcement
        }),
        current_frequency
    }
}

// Channels up to 14 only exist at 2.4 GHz. Elements that name a channel this way are not used at 6 GHz.
fn channel_to_frequency(channel: u8) -> Option<u16> {
    let band = if channel <= 14 { Nl80211Band::Band2GHz } else { Nl80211Band::Band5GHz };
    dot11_channel_to_frequency(u16::from(channel), band).ok()
}

// The 6 GHz operation information follows the fields that the operation parameters say are present.
fn parse_he_6ghz_primary_channel(operation: &[u8]) -> Option<u8> {
    if operation.len() < 6 {
        return None;
    }

    let parameters = u32::from(operation[0]) | u32::from(operation[1]) << 8 | u32::from(operation[2]) << 16;
    if parameters & (1 << 17) == 0 {
        return None;
    }

    let mut offset = 6;
    if parameters & (1 << 14) != 0 {
        offset += 3;
    }
    if parameters & (1 << 15) != 0 {
        offset += 1;
    }

    operation.get(offset).copied()
}

#[cfg(test)]
mod tests {
    use super::*;

    const SSID: [u8; 6] = [0, 4, b't', b'e', b's', b't'];
    const RATES_B: [u8; 6] = [1, 4, 0x82, 0x84, 0x8b, 0x96];
    const RATES_BG: [u8; 10] = [1, 8, 0x82, 0x84, 0x8b, 0x96, 0x0c, 0x12, 0x18, 0x24];
    const RATES_EXTENDED: [u8; 6] = [50, 4, 0x30, 0x48, 0x60, 0x6c];
    const COUNTRY_JP: [u8; 8] = [7, 6, b'J', b'P', b' ', 1, 14, 20];
    const COUNTRY_US: [u8; 8] = [7, 6, b'U', b'S', b' ', 1, 11, 30];
    const HT: [u8; 4] = [45, 2, 0, 0];
    const RSN: [u8; 4] = [48, 2, 1, 0];
    const VENDOR: [u8; 6] = [221, 4, 0x00, 0x50, 0xf2, 0x02];

    fn elements(parts: &[&[u8]]) -> Vec<u8> {
        parts.concat()
    }

    #[test]
    fn finds_no_announcement_in_plain_elements() {
        assert_eq!(parse(&elements(&[&SSID, &RSN, &VENDOR])), None);
        assert_eq!(parse(&[]), None);
    }

    #[test]
    fn parses_announcement_in_standard_position() {
        let parsed = parse(&elements(&[&SSID, &[37, 3, 1, 11, 4], &RSN, &VENDOR])).unwrap();

        assert_eq!(parsed.announcement_type, ChannelSwitchAnnouncementType::Standard);
        assert_eq!(parsed.switch_mode, 1);
        assert_eq!(parsed.new_channel, 11);
        assert_eq!(parsed.switch_count, 4);
        assert_eq!(parsed.new_operating_class, None);
        assert!(!parsed.is_trailing_element);
    }

    #[test]
    fn flags_announcement_appended_after_higher_elements() {
        let parsed = parse(&elements(&[&SSID, &RSN, &VENDOR, &[37, 3, 1, 14, 0]])).unwrap();

        assert_eq!(parsed.new_channel, 14);
        assert_eq!(parsed.switch_count, 0);
        assert!(parsed.is_trailing_element);
    }

    #[test]
    fn flags_both_announcements_appended_after_higher_elements() {
        let parsed = parse(&elements(&[&SSID, &RSN, &VENDOR, &[37, 3, 1, 14, 0], &[60, 4, 1, 81, 14, 0]])).unwrap();

        assert_eq!(parsed.new_operating_class, Some(81));
        assert!(parsed.is_trailing_element);
    }

    #[test]
    fn reads_the_channel_a_frame_says_it_was_sent_on() {
        let ds = [3, 1, 6];
        let ht_operation = [61, 22, 36, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];

        assert_eq!(parse_elements(&elements(&[&SSID, &ds, &HT])).current_frequency, Some(2437));
        assert_eq!(parse_elements(&elements(&[&SSID, &ht_operation])).current_frequency, Some(5180));
        assert_eq!(parse_elements(&elements(&[&SSID, &ds, &ht_operation])).current_frequency, None);
        assert_eq!(parse_elements(&elements(&[&SSID, &ds, &[61, 22, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]]))
            .current_frequency, Some(2437));
        assert_eq!(parse_elements(&elements(&[&SSID, &[3, 1, 11], &ds])).current_frequency, None);
        assert_eq!(parse_elements(&elements(&[&SSID, &ds, &ds])).current_frequency, Some(2437));
        assert_eq!(parse_elements(&elements(&[&SSID, &[3, 1, 200], &ht_operation])).current_frequency, Some(5180));
        assert_eq!(parse_elements(&elements(&[&SSID, &[61, 1, 36]])).current_frequency, None);
        assert_eq!(parse_elements(&elements(&[&SSID, &RSN])).current_frequency, None);
        assert_eq!(parse_elements(&elements(&[&SSID, &[3, 2, 6, 6]])).current_frequency, None);
        assert_eq!(parse_elements(&elements(&[&SSID, &[3, 1, 0]])).current_frequency, None);
        assert_eq!(parse_elements(&elements(&[&SSID, &[3, 1, 200]])).current_frequency, None);

        let parsed = parse_elements(&elements(&[&SSID, &ds, &[37, 3, 1, 11, 4]]));
        assert_eq!(parsed.current_frequency, Some(2437));
        assert_eq!(parsed.announcement.unwrap().current_frequency, Some(2437));
    }

    #[test]
    fn reads_the_6ghz_channel_from_the_he_operation() {
        // Operation parameters with the 6 GHz information present, BSS color, MCS set, then the information.
        let plain = [255, 12, 36, 0x00, 0x00, 0x02, 0x01, 0xfc, 0xff, 37, 0x03, 39, 0, 0];
        assert_eq!(parse_elements(&elements(&[&SSID, &plain])).current_frequency, Some(6135));

        // VHT operation information and the co-hosted BSSID indicator come before it when they are present.
        let preceded = [255, 16, 36, 0x00, 0xc0, 0x02, 0x01, 0xfc, 0xff, 1, 2, 3, 4, 101, 0x03, 103, 0, 0];
        assert_eq!(parse_elements(&elements(&[&SSID, &preceded])).current_frequency, Some(6455));

        let without = [255, 7, 36, 0x00, 0x00, 0x00, 0x01, 0xfc, 0xff];
        assert_eq!(parse_elements(&elements(&[&SSID, &without])).current_frequency, None);

        let truncated = [255, 7, 36, 0x00, 0x00, 0x02, 0x01, 0xfc, 0xff];
        assert_eq!(parse_elements(&elements(&[&SSID, &truncated])).current_frequency, None);

        let other_extension = [255, 3, 35, 1, 2];
        assert_eq!(parse_elements(&elements(&[&SSID, &other_extension])).current_frequency, None);
    }

    #[test]
    fn he_information_does_not_override_other_elements() {
        let ds = [3, 1, 6];
        let out_of_range = [255, 12, 36, 0x00, 0x00, 0x02, 0x01, 0xfc, 0xff, 240, 0x03, 0, 0, 0];
        let valid = [255, 12, 36, 0x00, 0x00, 0x02, 0x01, 0xfc, 0xff, 37, 0x03, 39, 0, 0];

        assert_eq!(parse_elements(&elements(&[&SSID, &ds, &out_of_range])).current_frequency, Some(2437));
        assert_eq!(parse_elements(&elements(&[&SSID, &out_of_range])).current_frequency, None);
        assert_eq!(parse_elements(&elements(&[&SSID, &ds, &valid])).current_frequency, None);
    }

    #[test]
    fn first_announcement_counts() {
        let parsed = parse(&elements(&[&SSID, &[37, 3, 1, 11, 3], &[37, 3, 1, 1, 3]])).unwrap();
        assert_eq!(parsed.new_channel, 11);
    }

    #[test]
    fn does_not_flag_last_element_that_is_in_order() {
        assert!(!parse(&elements(&[&SSID, &[37, 3, 1, 11, 4]])).unwrap().is_trailing_element);
    }

    #[test]
    fn merges_operating_class_of_extended_announcement() {
        let parsed = parse(&elements(&[&SSID, &[37, 3, 0, 100, 9], &RSN, &[60, 4, 0, 128, 100, 9], &VENDOR])).unwrap();

        assert_eq!(parsed.announcement_type, ChannelSwitchAnnouncementType::Standard);
        assert_eq!(parsed.new_channel, 100);
        assert_eq!(parsed.new_operating_class, Some(128));
        assert!(!parsed.is_trailing_element);
    }

    #[test]
    fn parses_extended_announcement_alone() {
        let parsed = parse(&elements(&[&SSID, &RSN, &[60, 4, 1, 115, 36, 2], &VENDOR])).unwrap();

        assert_eq!(parsed.announcement_type, ChannelSwitchAnnouncementType::Extended);
        assert_eq!(parsed.switch_mode, 1);
        assert_eq!(parsed.new_operating_class, Some(115));
        assert_eq!(parsed.new_channel, 36);
        assert_eq!(parsed.switch_count, 2);
    }

    #[test]
    fn ignores_malformed_announcements_and_truncated_elements() {
        assert_eq!(parse(&elements(&[&SSID, &[37, 2, 1, 11], &RSN])), None);
        assert_eq!(parse(&elements(&[&SSID, &[60, 3, 1, 115, 36], &RSN])), None);
        assert_eq!(parse(&elements(&[&SSID, &[37, 3, 1]])), None);
        assert_eq!(parse(&[37]), None);
    }

    #[test]
    fn reads_country_and_if_only_dsss_rates_are_advertised() {
        let csa: [u8; 5] = [37, 3, 1, 14, 0];

        let parsed = parse(&elements(&[&SSID, &RATES_B, &COUNTRY_JP, &csa])).unwrap();
        assert_eq!(parsed.dsss_only, Some(true));
        assert_eq!(parsed.country_code, Some("JP".to_string()));

        let parsed = parse(&elements(&[&SSID, &RATES_BG, &COUNTRY_US, &csa])).unwrap();
        assert_eq!(parsed.dsss_only, Some(false));
        assert_eq!(parsed.country_code, Some("US".to_string()));

        assert_eq!(parse(&elements(&[&SSID, &RATES_B, &csa, &RATES_EXTENDED])).unwrap().dsss_only, Some(false));
        assert_eq!(parse(&elements(&[&SSID, &RATES_B, &csa, &HT])).unwrap().dsss_only, Some(false));
        assert_eq!(parse(&elements(&[&SSID, &RATES_B, &[42, 1, 0], &csa])).unwrap().dsss_only, Some(false));
        assert_eq!(parse(&elements(&[&SSID, &[1, 5, 0x82, 0x84, 0x8b, 0x96, 0xfb], &csa])).unwrap().dsss_only, Some(true));
        assert_eq!(parse(&elements(&[&SSID, &[1, 5, 0x82, 0x84, 0x8b, 0x96, 0xff], &csa])).unwrap().dsss_only, Some(false));
    }

    #[test]
    fn leaves_country_and_rates_unknown_without_their_elements() {
        let parsed = parse(&[37, 3, 1, 14, 0]).unwrap();
        assert_eq!(parsed.dsss_only, None);
        assert_eq!(parsed.country_code, None);

        assert_eq!(parse(&elements(&[&[7, 6, b'j', b'p', b' ', 1, 14, 20], &[37, 3, 1, 14, 0]])).unwrap().country_code, None);
        assert_eq!(parse(&elements(&[&[7, 1, b'J'], &[37, 3, 1, 14, 0]])).unwrap().country_code, None);
        assert_eq!(parse(&elements(&[&[7, 2, 0xff, b'P'], &[37, 3, 1, 14, 0]])).unwrap().country_code, None);
    }

    // A frame check sequence left at the end of a frame reads as an element with an impossible length.
    #[test]
    fn stops_at_trailing_bytes_that_are_no_element() {
        let parsed = parse(&elements(&[&SSID, &RSN, &VENDOR, &[37, 3, 1, 14, 0], &[0xde, 0xad, 0xbe, 0xef]])).unwrap();

        assert!(parsed.is_trailing_element);
    }
}
