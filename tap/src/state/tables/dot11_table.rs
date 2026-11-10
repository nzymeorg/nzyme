use std::{collections::HashMap, sync::Mutex};
use std::sync::Arc;

use log::error;

use crate::{
    helpers::network::is_mac_address_multicast, link::payloads::{
        AdvertisedNetworkReport, BssidReport, Dot11ChannelStatisticsReport, Dot11CipherSuites,
        Dot11ClientReport, Dot11ClientStatisticsReport, Dot11TableReport, SecurityInformationReport, SignalStrengthReport,
    },
};
use crate::alerting::alert_types::Dot11Alert;
use crate::state::tables::table_helpers::{clear_mutex_hashmap, clear_mutex_vector};
use crate::wireless::dot11::frames::{Dot11Capabilities, Dot11DeauthenticationFrame, Dot11DisassociationFrame, Dot11ProbeResponseFrame, PmfMode, RadiotapHeader, TaggedParameters};
use crate::link::leaderlink::Leaderlink;
use crate::metrics::Metrics;
use crate::link::payloads::{Dot11AlertReport, Dot11DiscoFrameReport, Dot11DiscoReport, PmfReport};
use crate::wireless::dot11::frames::{Dot11BeaconFrame, Dot11DataFrame, Dot11DataFrameDirection, Dot11ProbeRequestFrame, FrameSubType, InfraStructureType, SecurityInformation};

pub struct Dot11Table {
    pub leaderlink: Arc<Mutex<Leaderlink>>,
    pub metrics: Arc<Mutex<Metrics>>,
    pub bssids: Mutex<HashMap<String, Bssid>>,
    pub clients: Mutex<HashMap<String, Client>>,
    pub disco: DiscoTable,
    pub alerts: Mutex<Vec<Dot11Alert>>
}

#[derive(Debug)]
pub struct Bssid {
    pub advertised_networks: HashMap<String, AdvertisedNetwork>,
    pub clients: HashMap<String, Dot11ClientStatistics>,
    pub hidden_ssid_frames: u128,
    pub signal_strengths: Vec<i8>,
    pub fingerprints: Vec<String>,
}

#[derive(Debug)]
pub struct Client {
    pub probe_request_ssids: HashMap<String, u128>,
    pub wildcard_probe_requests: u128,
    pub signal_strengths: Vec<i8>
}

#[derive(Debug)]
pub struct Dot11ChannelStatistics {
    frames: u128,
    bytes: u128
}

#[derive(Debug)]
pub struct Dot11ClientStatistics {
    tx_frames: u128,
    tx_bytes: u128,
    rx_frames: u128,
    rx_bytes: u128,
    signal_strengths: Vec<i8>
}

#[derive(Debug)]
pub struct AdvertisedNetwork {
    pub security: Vec<SecurityInformation>,
    pub beacon_advertisements: u128,
    pub proberesp_advertisements: u128,
    pub fingerprints: Vec<String>,
    pub rates: Vec<f32>,
    pub wps: Vec<bool>,
    pub signal_strengths: HashMap<u16, Vec<i8>>,
    pub infrastructure_types: Vec<InfraStructureType>,
    pub channel_statistics: HashMap<u16, HashMap<FrameSubType, Dot11ChannelStatistics>>
}

pub const MAX_DISCO_FRAME_ENTRIES: usize = 2500;

#[derive(Debug, Default)]
pub struct DiscoTable {
    pub frames: Mutex<DiscoFrames>,
}

#[derive(Debug, Default)]
pub struct DiscoFrames {
    pub entries: HashMap<DiscoFrameKey, DiscoFrameAggregate>,
    pub dropped_frames: u128
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum DiscoType {
    Deauthentication,
    Disassociation
}

impl DiscoType {
    fn report_name(&self) -> String {
        match self {
            DiscoType::Deauthentication => "DEAUTHENTICATION".to_string(),
            DiscoType::Disassociation => "DISASSOCIATION".to_string()
        }
    }
}

// The frequency is part of the key because the tap hops channels and sees the same pair on several of them.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct DiscoFrameKey {
    pub disco_type: DiscoType,
    pub transmitter: String,
    pub receiver: String,
    pub bssid: String,
    pub reason_code: u16,
    pub frequency: Option<u16>
}

#[derive(Debug, Default)]
pub struct DiscoFrameAggregate {
    pub frame_count: u128,
    pub signal_strengths: Vec<i8>
}

impl Dot11Table {
    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        Self {
            bssids: Mutex::new(HashMap::new()),
            clients: Mutex::new(HashMap::new()),
            disco: DiscoTable::default(),
            alerts: Mutex::new(Vec::new()),
            leaderlink,
            metrics
        }
    }

    pub fn register_alert(&mut self, alert: Dot11Alert) {
        match self.alerts.lock() {
            Ok(mut alerts) => {
                alerts.push(alert);
            },
            Err(e) => error!("Could not acquire alerts table mutex: {}", e)
        }
    }

    pub fn register_beacon_frame(&mut self, beacon: Dot11BeaconFrame) {
        self.register_advertisement(
            FrameSubType::Beacon,
            beacon.length,
            beacon.header,
            beacon.transmitter,
            beacon.tagged_parameters,
            beacon.security,
            beacon.has_wps,
            beacon.fingerprint,
            beacon.capabilities
        );
    }

    pub fn register_probe_response_frame(&mut self, proberesp: Dot11ProbeResponseFrame) {
        self.register_advertisement(
            FrameSubType::ProbeResponse,
            proberesp.length,
            proberesp.header,
            proberesp.transmitter,
            proberesp.tagged_parameters,
            proberesp.security,
            proberesp.has_wps,
            proberesp.fingerprint,
            proberesp.capabilities
        );
    }

    #[allow(clippy::too_many_arguments)]
    fn register_advertisement(&mut self,
                              frame_type: FrameSubType,
                              length: usize,
                              header: RadiotapHeader,
                              transmitter: String,
                              tagged_parameters: TaggedParameters,
                              security: Vec<SecurityInformation>,
                              has_wps: bool,
                              fingerprint: String,
                              capabilities: Dot11Capabilities
    ) {
        let signal_strength: i8 = header.antenna_signal.unwrap_or(0);

        match self.bssids.lock() {
            Ok(mut bssids) => {
                match bssids.get_mut(&transmitter) {
                    Some(bssid) => {
                        // Update existing SSIDs of BSSID.
                        match tagged_parameters.ssid {
                            Some(ssid) => {
                                // Has this BSSID advertised this SSID before?
                                match bssid.advertised_networks.get_mut(&ssid) {
                                    Some(ssid) => {
                                        for sec in security {
                                            if !ssid.security.contains(&sec) {
                                                ssid.security.push(sec);
                                            }
                                        }

                                        if !ssid.wps.contains(&has_wps) {
                                            ssid.wps.push(has_wps);
                                        }

                                        if !ssid.fingerprints.contains(&fingerprint) {
                                            ssid.fingerprints.push(fingerprint.clone());
                                        }

                                        if let Some(rates) = &tagged_parameters.supported_rates {
                                            Self::update_existing_ssid_rates(ssid, rates);
                                        }

                                        if let Some(rates) = &tagged_parameters.extended_supported_rates {
                                            Self::update_existing_ssid_rates(ssid, rates);
                                        }

                                        if !ssid.infrastructure_types.contains(&capabilities.infrastructure_type) {
                                            ssid.infrastructure_types.push(capabilities.infrastructure_type);
                                        }

                                        Self::update_existing_channel_statistics(
                                            FrameSubType::Beacon, header.frequency, length, ssid
                                        );

                                        if let Some(freq) = &header.frequency {
                                            match ssid.signal_strengths.get_mut(freq) {
                                                Some(ss) => ss.push(signal_strength),
                                                None => {
                                                    ssid.signal_strengths.insert(*freq, vec![signal_strength]);
                                                },
                                            }
                                        }

                                        match frame_type {
                                            FrameSubType::Beacon => ssid.beacon_advertisements += 1,
                                            FrameSubType::ProbeResponse => ssid.proberesp_advertisements += 1,
                                            _ => {}
                                        };
                                    }
                                    None => {
                                        // Insert new SSID.
                                        let (beacon_advertisements, proberesp_advertisements) =
                                            match frame_type {
                                                FrameSubType::Beacon => (1,0),
                                                FrameSubType::ProbeResponse => (0,1),
                                                _ => (0,0)
                                            };

                                        bssid.advertised_networks.insert(
                                            ssid,
                                            AdvertisedNetwork {
                                                security,
                                                fingerprints: vec![fingerprint.clone()],
                                                beacon_advertisements,
                                                proberesp_advertisements,
                                                rates: Self::build_initial_ssid_rates(
                                                    &tagged_parameters.supported_rates,
                                                    &tagged_parameters.extended_supported_rates
                                                ),
                                                wps: vec![has_wps],
                                                signal_strengths: Self::build_initial_signal_strengths(&header.frequency, signal_strength),
                                                infrastructure_types: vec![capabilities.infrastructure_type],
                                                channel_statistics: Self::build_initial_channel_statistics(
                                                    FrameSubType::Beacon, &header.frequency, length
                                                )
                                            },
                                        );
                                    }
                                }
                            }
                            None => {
                                // No SSID.
                                bssid.hidden_ssid_frames += 1;
                            }
                        }

                        // Update BSSID.
                        if !bssid.fingerprints.contains(&fingerprint) {
                            bssid.fingerprints.push(fingerprint);
                        }
                        bssid.signal_strengths.push(signal_strength);
                    }
                    None => {
                        // BSSID not yet in table.
                        let (beacon_advertisements, proberesp_advertisements) =
                            match frame_type {
                                FrameSubType::Beacon => (1,0),
                                FrameSubType::ProbeResponse => (0,1),
                                _ => (0,0)
                            };

                        let (advertised_networks, hidden_ssid_frames) = match tagged_parameters.ssid {
                            Some(ssid) => (
                                HashMap::from([(
                                    ssid,
                                    AdvertisedNetwork {
                                        security,
                                        fingerprints: vec![fingerprint.clone()],
                                        beacon_advertisements,
                                        proberesp_advertisements,
                                        rates: Self::build_initial_ssid_rates(
                                            &tagged_parameters.supported_rates,
                                            &tagged_parameters.extended_supported_rates
                                        ),
                                        wps: vec![has_wps],
                                        signal_strengths: Self::build_initial_signal_strengths(
                                            &header.frequency, signal_strength
                                        ),
                                        infrastructure_types: vec![capabilities.infrastructure_type],
                                        channel_statistics: Self::build_initial_channel_statistics(
                                            FrameSubType::Beacon, &header.frequency, length
                                        )
                                    },
                                )]),
                                0,
                            ),
                            None => (HashMap::new(), 1),
                        };

                        bssids.insert(
                            transmitter,
                            Bssid {
                                advertised_networks,
                                hidden_ssid_frames,
                                clients: HashMap::new(),
                                signal_strengths: vec![signal_strength],
                                fingerprints: vec![fingerprint]
                            },
                        );
                    }
                };
            }
            Err(e) => error!("Could not acquire BSSIDs table mutex: {}", e),
        }
    }

    pub fn register_probe_request_frame(&self, frame: Dot11ProbeRequestFrame) {
        let signal_strength: i8 = frame.header.antenna_signal.unwrap_or(0);

        match self.clients.lock() {
            Ok(mut clients) => {
                match clients.get_mut(&frame.transmitter) {
                    Some(client) => {
                        // Update existing client.
                        match frame.ssid {
                            Some(ssid) => {
                                // Specific/SSID request.
                                match client.probe_request_ssids.get_mut(&ssid) {
                                    None => {
                                        // First time this client is seeing this SSID.
                                        client.probe_request_ssids.insert(ssid, 1);
                                    }
                                    Some(count) => {
                                        // Client has previously seen this SSID.
                                        *count += 1;
                                    }
                                }
                            },
                            None => {
                                // Wildcard request.
                                client.wildcard_probe_requests += 1;
                            }
                        }

                        // Update signal strength
                        client.signal_strengths.push(signal_strength);
                    },
                    None => {
                        // First time we are seeing this client.
                        let (probe_request_ssids, wildcard_probe_requests) = match frame.ssid {
                            Some(ssid) => {
                                let mut ssids = HashMap::new();
                                ssids.insert(ssid, 1);
                                (ssids, 0)
                            },
                            None => (HashMap::new(), 1),
                        };

                        clients.insert(
                            frame.transmitter,
                            Client {
                                probe_request_ssids,
                                wildcard_probe_requests,
                                signal_strengths: vec![signal_strength]
                            }
                        );
                    }
                }
            },
            Err(e) => error!("Could not acquire clients table mutex: {}", e)
        }
    }

    pub fn register_data_frame(&self, frame: Dot11DataFrame) {
        let signal_strength: i8 = frame.header.antenna_signal.unwrap_or(0);

        let (sta, tx_frames, tx_bytes, rx_frames, rx_bytes) = match frame.ds.direction {
            Dot11DataFrameDirection::Entering => {
                (frame.ds.destination, 0, 0, 1, frame.length)
            },
            Dot11DataFrameDirection::Leaving => {
                (frame.ds.destination, 1, frame.length, 0, 0)
            },
            Dot11DataFrameDirection::NotLeavingOrAdHoc |
            Dot11DataFrameDirection::WDS => return
        };

        if frame.ds.direction == Dot11DataFrameDirection::Entering && sta == "FF:FF:FF:FF:FF:FF" {
            // Ignore entering frames with wildcard destination.
            return
        }

        if is_mac_address_multicast(&sta) {
            // Not interested in multicast frames.
            return
        }

        match self.bssids.lock() {
            Ok(mut bssids) => {
                if let Some(bssid) = bssids.get_mut(&frame.ds.bssid) {
                    match bssid.clients.get_mut(&sta) {
                        Some(client) => {
                            // Update existing client.
                            client.tx_frames += tx_frames;
                            client.tx_bytes += tx_bytes as u128;
                            client.rx_frames += rx_frames;
                            client.rx_bytes += rx_bytes as u128;
                            client.signal_strengths.push(signal_strength);
                        },
                        None => {
                            // First time seeing this client.
                            bssid.clients.insert(sta, Dot11ClientStatistics {
                                tx_frames,
                                tx_bytes: tx_bytes as u128,
                                rx_frames,
                                rx_bytes: rx_bytes as u128,
                                signal_strengths: vec![signal_strength]
                            });
                        },
                    }
                }
            },
            Err(e) => error!("Could not acquire BSSIDs table mutex: {}", e),
        }
    }

    pub fn register_deauthentication_frame(&self, frame: Dot11DeauthenticationFrame) {
        self.register_disco_frame(
            DiscoType::Deauthentication,
            frame.transmitter,
            frame.destination,
            frame.bssid,
            frame.reason_code,
            &frame.header
        );
    }

    pub fn register_disassociation_frame(&self, frame: Dot11DisassociationFrame) {
        self.register_disco_frame(
            DiscoType::Disassociation,
            frame.transmitter,
            frame.destination,
            frame.bssid,
            frame.reason_code,
            &frame.header
        );
    }

    fn register_disco_frame(&self,
                            disco_type: DiscoType,
                            transmitter: String,
                            receiver: String,
                            bssid: String,
                            reason_code: u16,
                            header: &RadiotapHeader) {
        let key = DiscoFrameKey {
            disco_type,
            transmitter,
            receiver,
            bssid,
            reason_code,
            frequency: header.frequency
        };

        match self.disco.frames.lock() {
            Ok(mut frames) => {
                if !frames.entries.contains_key(&key) && frames.entries.len() >= MAX_DISCO_FRAME_ENTRIES {
                    frames.dropped_frames += 1;
                    return;
                }

                let aggregate = frames.entries.entry(key).or_default();
                aggregate.frame_count += 1;

                if let Some(signal_strength) = header.antenna_signal {
                    aggregate.signal_strengths.push(signal_strength);
                }
            },
            Err(e) => error!("Could not acquire disco table: {}", e)
        }
    }

    pub fn build_initial_channel_statistics(frame_subtype: FrameSubType, frequency: &Option<u16>, frame_length: usize) 
            -> HashMap<u16, HashMap<FrameSubType, Dot11ChannelStatistics>> {
        let mut channel_statistics = HashMap::new();
        if let Some(freq) = frequency {
            let mut stats = HashMap::new();
            stats.insert(
                frame_subtype,
                Dot11ChannelStatistics {
                    frames: 1,
                    bytes: frame_length as u128
                }
            );

            channel_statistics.insert(*freq, stats);
        }

        channel_statistics
    }

    pub fn update_existing_channel_statistics(frame_subtype: FrameSubType, frequency: Option<u16>, frame_length: usize,
            ssid: &mut AdvertisedNetwork) {
        if let Some(freq) = frequency {
            match ssid.channel_statistics.get_mut(&freq) {
                Some(types) => {
                    match types.get_mut(&frame_subtype) {
                        Some(stats) => {
                            // Add frame to stats.
                            stats.frames += 1;
                            stats.bytes += frame_length as u128;
                        },
                        None => {
                            // First frame of this subtype on this frequency.
                            types.insert(frame_subtype, Dot11ChannelStatistics {
                                frames: 1,
                                bytes: frame_length as u128
                            });
                        }
                    }
                },
                None => {
                    // First frame for this frequency. 
                    let mut stats = HashMap::new();
                    stats.insert(frame_subtype, Dot11ChannelStatistics {
                        frames: 1,
                        bytes: frame_length as u128
                    });
                    ssid.channel_statistics.insert(freq, stats);
                }
            }
        }
    }

    pub fn build_initial_ssid_rates(rates: &Option<Vec<f32>>, extended_rates: &Option<Vec<f32>>) -> Vec<f32> {
        let mut result = Vec::new();
        if let Some(rates) = rates {
            for rate in rates {
                result.push(*rate)
            }
        }

        if let Some(rates) = extended_rates {
            for rate in rates {
                result.push(*rate)
            }
        }

        result
    }

    pub fn update_existing_ssid_rates(ssid: &mut AdvertisedNetwork, rates: &Vec<f32>) {
        for rate in rates {
            if !ssid.rates.contains(rate) {
                ssid.rates.push(*rate);
            }
        }
    }

    pub fn build_initial_signal_strengths(channel: &Option<u16>, signal_strength: i8) -> HashMap<u16, Vec<i8>> {
        let mut map = HashMap::new();

        match channel {
            Some(channel) => map.insert(*channel, vec![signal_strength]),
            None => return map,
        };

        map
    }

    pub fn process_report(&self) {
        // Generate JSON.
        let report = match serde_json::to_string(&self.generate_report()) {
            Ok(report) => report,
            Err(e) => {
                error!("Could not serialize 802.11 report: {}", e);
                return;
            }
        };

        // Send report.
        match self.leaderlink.lock() {
            Ok(link) => {
                if let Err(e) = link.send_report("dot11/summary", report) {
                    error!("Could not submit 802.11 report: {}", e);
                }
            },
            Err(e) => error!("Could not acquire 802.11 table lock for report submission: {}", e)
        }

        // Clean up.
        clear_mutex_hashmap(&self.bssids);
        clear_mutex_hashmap(&self.clients);
        match self.disco.frames.lock() {
            Ok(mut frames) => {
                match self.metrics.lock() {
                    Ok(mut metrics) => {
                        metrics.set_gauge("tables.dot11.disco.frames.size", frames.entries.len() as i128);
                        metrics.set_gauge("tables.dot11.disco.frames.dropped", frames.dropped_frames as i128);
                    },
                    Err(e) => error!("Could not acquire metrics mutex: {}", e)
                }

                frames.entries.clear();
                frames.dropped_frames = 0;
            },
            Err(e) => error!("Could not acquire disco table mutex: {}", e)
        }

        clear_mutex_vector(&self.alerts);
    }

    fn generate_report(&self) -> Dot11TableReport {
        let mut bssid_report: HashMap<String, BssidReport> = HashMap::new();

        match self.bssids.lock() {
            Ok(bssids) => for (bssid, info) in &*bssids {
                let mut advertised_networks: HashMap<String, AdvertisedNetworkReport> =
                    HashMap::new();
                for (ssid, netinfo) in &info.advertised_networks {
                    let mut netsec_report: Vec<SecurityInformationReport> = Vec::new();

                    for netsecinfo in &netinfo.security {
                        let mut protocols: Vec<String> = Vec::new();
                        for protocol in &netsecinfo.protocols {
                            protocols.push(protocol.to_string());
                        }

                        let suites: Dot11CipherSuites = match &netsecinfo.suites {
                            Some(suite) => {
                                let mut pairwise_ciphers: Vec<String> = Vec::new();
                                for cipher in &suite.pairwise_ciphers {
                                    pairwise_ciphers.push(cipher.to_string());
                                }

                                let mut key_management_modes: Vec<String> = Vec::new();
                                for mode in &suite.key_management_modes {
                                    key_management_modes.push(mode.to_string());
                                }

                                Dot11CipherSuites {
                                    group_cipher: suite.group_cipher.to_string(),
                                    pairwise_ciphers,
                                    key_management_modes
                                }
                            },
                            None => Dot11CipherSuites {
                                group_cipher: "".to_string(),
                                pairwise_ciphers: Vec::new(),
                                key_management_modes: Vec::new(),
                            },
                        };

                        let pmf = match netsecinfo.pmf {
                            PmfMode::Required => PmfReport::Required,
                            PmfMode::Optional => PmfReport::Optional,
                            PmfMode::Disabled => PmfReport::Disabled,
                            PmfMode::Unavailable => PmfReport::Unavailable
                        };

                        netsec_report.push(SecurityInformationReport { protocols, suites, pmf });
                    }

                    let mut channel_statistics = HashMap::new();
                    for (frequency, frame_types) in &netinfo.channel_statistics {
                        let mut frame_type_summary = HashMap::new();
                        for (frame_type, stats) in frame_types {
                            frame_type_summary.insert(
                                frame_type.to_string().to_lowercase(),
                                Dot11ChannelStatisticsReport {
                                    frames: stats.frames,
                                    bytes: stats.bytes
                                });
                        }

                        channel_statistics.insert(*frequency, frame_type_summary);
                    }

                    let mut infrastructure_types: Vec<String> = Vec::new();
                    for t in &netinfo.infrastructure_types {
                        infrastructure_types.push(t.to_string());
                    }

                    advertised_networks.insert(
                        ssid.clone(),
                        AdvertisedNetworkReport {
                            security: netsec_report,
                            fingerprints: netinfo.fingerprints.clone(),
                            beacon_advertisements: netinfo.beacon_advertisements,
                            proberesp_advertisements: netinfo.proberesp_advertisements,
                            rates: netinfo.rates.clone(),
                            wps: netinfo.wps.clone(),
                            signal_strength: calculate_signal_strength_report(
                                &netinfo.signal_strengths,
                            ),
                            signal_histogram: calculate_signal_histogram(&netinfo.signal_strengths),
                            infrastructure_types,
                            channel_statistics
                        },
                    );
                }

                let mut clients = HashMap::new();
                for (sta, stats) in &info.clients {
                    clients.insert(sta.clone(), Dot11ClientStatisticsReport {
                        tx_frames: stats.tx_frames,
                        tx_bytes: stats.tx_bytes,
                        rx_frames: stats.rx_frames,
                        rx_bytes: stats.rx_bytes,
                        signal_strength: calculate_1d_signal_strength_report(&stats.signal_strengths)
                    });
                }

                bssid_report.insert(
                    bssid.clone(),
                    BssidReport {
                        advertised_networks,
                        clients,
                        hidden_ssid_frames: info.hidden_ssid_frames,
                        signal_strength: calculate_1d_signal_strength_report(
                            &info.signal_strengths,
                        ),
                        fingerprints: info.fingerprints.clone(),
                    },
                );
            },
            Err(e) => error!("Could not acquire BSSIDs table mutex: {}", e),
        }

        let mut clients_report: HashMap<String, Dot11ClientReport> = HashMap::new();
        match self.clients.lock() {
            Ok(clients) => {
                for (client, info) in &*clients {
                    clients_report.insert(
                        client.clone(),
                        Dot11ClientReport {
                            probe_request_ssids: info.probe_request_ssids.clone(),
                            wildcard_probe_requests: info.wildcard_probe_requests,
                            signal_strength: calculate_1d_signal_strength_report(
                                &info.signal_strengths
                            )
                        }
                    );
                }
            },
            Err(e) => error!("Could not acquire BSSIDs table mutex: {}", e),
        }

        let disco_report = self.disco_table_to_report();

        let mut alerts_report: Vec<Dot11AlertReport> = Vec::new();
        match self.alerts.lock() {
            Ok(alerts) => {
                for alert in &*alerts {
                    alerts_report.push(
                        Dot11AlertReport {
                            alert_type: alert.alert_type.clone(),
                            signal_strength: alert.signal_strength,
                            attributes: alert.attributes.clone()
                        }
                    );
                }
            },
            Err(e) => error!("Could not acquire alerts table mutex: {}", e),
        }

        Dot11TableReport {
            bssids: bssid_report,
            clients: clients_report,
            disco: disco_report,
            alerts: alerts_report
        }
    }

    fn disco_table_to_report(&self) -> Dot11DiscoReport {
        let mut frames: Vec<Dot11DiscoFrameReport> = Vec::new();

        match self.disco.frames.lock() {
            Ok(table) => {
                for (key, aggregate) in &table.entries {
                    frames.push(
                        Dot11DiscoFrameReport {
                            disco_type: key.disco_type.report_name(),
                            transmitter: key.transmitter.clone(),
                            receiver: key.receiver.clone(),
                            bssid: key.bssid.clone(),
                            reason_code: key.reason_code,
                            frequency: key.frequency,
                            frame_count: aggregate.frame_count,
                            signal_strength: calculate_signal_strength_report_from_list(&aggregate.signal_strengths)
                        }
                    );
                }
            },
            Err(e) => error!("Could not acquire disco table mutex: {}", e)
        }

        Dot11DiscoReport { frames }
    }
}

// yo this all below here needs some serious refactoring and dedup lmao

fn calculate_signal_strength_report_from_list(signal_strengths: &[i8]) -> Option<SignalStrengthReport> {
    if signal_strengths.is_empty() {
        return None;
    }

    let min = *signal_strengths.iter().min().unwrap();
    let max = *signal_strengths.iter().max().unwrap();
    let sum: i128 = signal_strengths.iter().map(|s| *s as i128).sum();

    Some(SignalStrengthReport {
        min,
        max,
        average: sum as f32 / signal_strengths.len() as f32
    })
}

fn calculate_signal_strength_report(signal_strengths: &HashMap<u16, Vec<i8>>) -> SignalStrengthReport {
    let mut sum: i128 = 0;
    let mut min = 0;
    let mut max = -100;
    let mut count = 0;

    for channel in signal_strengths.values() {
        for ss in channel {
            count += 1;
            sum += *ss as i128;

            if ss > &max {
                max = *ss;
            }

            if ss < &min {
                min = *ss;
            }
        }
    }

    
    let average = if count > 0 { (sum / count) as f32 } else { 0.0 };

    SignalStrengthReport {
        min,
        max,
        average,
    }
}

fn calculate_1d_signal_strength_report(signal_strengths: &Vec<i8>) -> SignalStrengthReport {
    let mut sum: i128 = 0;
    let mut min = 0;
    let mut max = -100;
    for ss in signal_strengths {
        sum += *ss as i128;

        if ss > &max {
            max = *ss;
        }

        if ss < &min {
            min = *ss;
        }
    }

    let count = signal_strengths.len() as i128;
    let average = (sum / count) as f32;

    SignalStrengthReport {
        min,
        max,
        average,
    }
}

fn calculate_signal_histogram(signal_strengths: &HashMap<u16, Vec<i8>>) -> HashMap<u16, HashMap<i8, u128>> {
    let mut histograms: HashMap<u16, HashMap<i8, u128>> = HashMap::new();

    for (channel, signals) in signal_strengths {
        match histograms.get_mut(channel) {
            Some(channel) => {
                // Update existing channel.
                for signal in signals {
                    match channel.get_mut(signal) {
                        Some(existing) => { *existing += 1; },
                        None => { channel.insert(*signal, 1); }
                    }
                }
            },
            None => {
                // First time channel.
                let mut histogram: HashMap<i8, u128> = HashMap::new();
                for signal in signals {
                    match histogram.get_mut(signal) {
                        Some(existing) => { *existing += 1; },
                        None => { histogram.insert(*signal, 1); }
                    }
                }
                histograms.insert(*channel, histogram);
            },
        }
    }

    histograms
}
