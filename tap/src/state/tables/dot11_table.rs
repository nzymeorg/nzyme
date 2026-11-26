use std::{collections::HashMap, sync::Mutex};
use std::sync::Arc;
use std::time::{Duration, Instant};

use log::error;

use crate::{
    helpers::network::is_mac_address_multicast, link::payloads::{
        AdvertisedNetworkReport, BssidReport, Dot11ChannelStatisticsReport, Dot11CipherSuites,
        Dot11ClientReport, Dot11ClientStatisticsReport, Dot11TableReport, SecurityInformationReport, SignalStrengthReport,
    },
};
use crate::alerting::alert_types::Dot11Alert;
use crate::state::tables::dot11_channel_switch_table::{ChannelSwitchFrameType, ChannelSwitchTable};
use crate::state::tables::dot11_frame_tables::{ConnectionFrames, DiscoFrames, DiscoType};
use crate::state::tables::table_helpers::{clear_mutex_hashmap, clear_mutex_vector};
use crate::wireless::dot11::frames::{DisconnectionFrameDetails, Dot11Capabilities, Dot11ChannelSwitchActionFrame, Dot11ConnectionFrame, Dot11DeauthenticationFrame, Dot11DisassociationFrame, Dot11ProbeResponseFrame, PmfMode, RadiotapHeader, TaggedParameters};
use crate::link::leaderlink::Leaderlink;
use crate::metrics::Metrics;
use crate::link::payloads::{Dot11AlertReport, Dot11DiscoReport, FrequencySignalStrengthReport, PmfReport};
use crate::wireless::dot11::frames::{Dot11BeaconFrame, Dot11DataFrame, Dot11DataFrameDirection, Dot11ProbeRequestFrame, FrameSubType, InfraStructureType, SecurityInformation};

const KNOWN_ACCESS_POINT_MINIMUM_AGE: Duration = Duration::from_secs(300);

pub struct Dot11Table {
    pub leaderlink: Arc<Mutex<Leaderlink>>,
    pub metrics: Arc<Mutex<Metrics>>,
    pub bssids: Mutex<HashMap<String, Bssid>>,
    pub clients: Mutex<HashMap<String, Client>>,
    pub disco: Mutex<DiscoFrames>,
    pub connection_frames: Mutex<ConnectionFrames>,
    pub channel_switches: Mutex<ChannelSwitchTable>,
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
    pub signal_strengths: Vec<i8>,
    pub signal_by_frequency: ClientSignalStrengths
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

    // Only of frames the client sent. A frame sent to it arrives with the strength of the access point.
    signal_strengths: ClientSignalStrengths
}

#[derive(Debug, Default)]
pub struct ClientSignalStrengths {
    by_frequency: HashMap<u16, FrequencySignalStrength>
}

#[derive(Debug)]
struct FrequencySignalStrength {
    min: i8,
    max: i8,
    sum: i64,
    frames: u64
}

impl ClientSignalStrengths {

    pub fn register(&mut self, frequency: u16, signal_strength: i8) {
        let entry = self.by_frequency.entry(frequency).or_insert(FrequencySignalStrength {
            min: signal_strength,
            max: signal_strength,
            sum: 0,
            frames: 0
        });

        entry.min = entry.min.min(signal_strength);
        entry.max = entry.max.max(signal_strength);
        entry.sum += i64::from(signal_strength);
        entry.frames += 1;
    }

    pub fn total(&self) -> Option<SignalStrengthReport> {
        let frames: u64 = self.by_frequency.values().map(|f| f.frames).sum();
        if frames == 0 {
            return None;
        }

        Some(SignalStrengthReport {
            min: self.by_frequency.values().map(|f| f.min).min()?,
            max: self.by_frequency.values().map(|f| f.max).max()?,
            average: self.by_frequency.values().map(|f| f.sum).sum::<i64>() as f32 / frames as f32
        })
    }

    pub fn by_frequency(&self) -> HashMap<u16, FrequencySignalStrengthReport> {
        self.by_frequency.iter()
            .map(|(frequency, f)| (*frequency, FrequencySignalStrengthReport {
                min: f.min,
                max: f.max,
                average: f.sum as f32 / f.frames as f32,
                frames: f.frames
            }))
            .collect()
    }

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

impl Dot11Table {
    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        Self {
            bssids: Mutex::new(HashMap::new()),
            clients: Mutex::new(HashMap::new()),
            disco: Mutex::new(DiscoFrames::default()),
            connection_frames: Mutex::new(ConnectionFrames::default()),
            channel_switches: Mutex::new(ChannelSwitchTable::default()),
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
        match self.channel_switches.lock() {
            Ok(mut channel_switches) => channel_switches.register_beacon(
                &beacon.transmitter,
                beacon.header.frequency,
                beacon.header.antenna_signal,
                beacon.timestamp,
                beacon.tagged_parameters.channel_switch.as_ref(),
                beacon.tagged_parameters.current_frequency,
                beacon.header.captured_at
            ),
            Err(e) => error!("Could not acquire channel switch table mutex: {}", e)
        }

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
        if let Some(announcement) = &proberesp.tagged_parameters.channel_switch {
            match self.channel_switches.lock() {
                Ok(mut channel_switches) => channel_switches.register_frame(
                    &proberesp.transmitter,
                    ChannelSwitchFrameType::ProbeResponse,
                    proberesp.header.frequency,
                    proberesp.header.antenna_signal,
                    announcement,
                    proberesp.header.captured_at
                ),
                Err(e) => error!("Could not acquire channel switch table mutex: {}", e)
            }
        }

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
        let frequency_signal = match (frame.header.frequency, frame.header.antenna_signal) {
            (Some(frequency), Some(signal_strength)) => Some((frequency, signal_strength)),
            _ => None
        };

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
                        if let Some((frequency, signal_strength)) = frequency_signal {
                            client.signal_by_frequency.register(frequency, signal_strength);
                        }
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

                        let mut signal_by_frequency = ClientSignalStrengths::default();
                        if let Some((frequency, signal_strength)) = frequency_signal {
                            signal_by_frequency.register(frequency, signal_strength);
                        }

                        clients.insert(
                            frame.transmitter,
                            Client {
                                probe_request_ssids,
                                wildcard_probe_requests,
                                signal_strengths: vec![signal_strength],
                                signal_by_frequency
                            }
                        );
                    }
                }
            },
            Err(e) => error!("Could not acquire clients table mutex: {}", e)
        }
    }

    pub fn register_data_frame(&self, frame: Dot11DataFrame) {
        let sent_by_client = frame.ds.direction == Dot11DataFrameDirection::Leaving;
        let signal_strength = match (sent_by_client, frame.header.frequency, frame.header.antenna_signal) {
            (true, Some(frequency), Some(signal_strength)) => Some((frequency, signal_strength)),
            _ => None
        };

        let (sta, tx_frames, tx_bytes, rx_frames, rx_bytes) = match frame.ds.direction {
            Dot11DataFrameDirection::Entering => {
                (frame.ds.destination, 0, 0, 1, frame.length)
            },
            Dot11DataFrameDirection::Leaving => {
                (frame.ds.source, 1, frame.length, 0, 0)
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

                            if let Some((frequency, signal_strength)) = signal_strength {
                                client.signal_strengths.register(frequency, signal_strength);
                            }
                        },
                        None => {
                            // First time seeing this client.
                            let mut signal_strengths = ClientSignalStrengths::default();
                            if let Some((frequency, signal_strength)) = signal_strength {
                                signal_strengths.register(frequency, signal_strength);
                            }

                            bssid.clients.insert(sta, Dot11ClientStatistics {
                                tx_frames,
                                tx_bytes: tx_bytes as u128,
                                rx_frames,
                                rx_bytes: rx_bytes as u128,
                                signal_strengths
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
            &frame.details,
            &frame.header
        );
    }

    pub fn register_disassociation_frame(&self, frame: Dot11DisassociationFrame) {
        self.register_disco_frame(
            DiscoType::Disassociation,
            frame.transmitter,
            frame.destination,
            frame.bssid,
            &frame.details,
            &frame.header
        );
    }

    // Counts every action frame, so the metrics show if a capture delivers them at all.
    pub fn register_action_frame(&self, channel_switch: Option<Dot11ChannelSwitchActionFrame>) {
        match self.channel_switches.lock() {
            Ok(mut channel_switches) => {
                channel_switches.action_frames += 1;

                if let Some(frame) = channel_switch {
                    channel_switches.register_frame(
                        &frame.bssid,
                        ChannelSwitchFrameType::Action,
                        frame.header.frequency,
                        frame.header.antenna_signal,
                        &frame.announcement,
                        frame.header.captured_at
                    );
                }
            },
            Err(e) => error!("Could not acquire channel switch table mutex: {}", e)
        }
    }

    fn register_disco_frame(&self,
                            disco_type: DiscoType,
                            transmitter: String,
                            receiver: String,
                            bssid: String,
                            details: &DisconnectionFrameDetails,
                            header: &RadiotapHeader) {
        let is_known_access_point = self.knows_access_point(&bssid);

        match self.disco.lock() {
            Ok(mut disco) => {
                disco.register(disco_type, transmitter, receiver, bssid, details, header, is_known_access_point)
            },
            Err(e) => error!("Could not acquire disco table: {}", e)
        }
    }

    pub fn register_connection_frame(&self, frame: Dot11ConnectionFrame) {
        let is_known_access_point = self.knows_access_point(&frame.bssid);

        match self.connection_frames.lock() {
            Ok(mut connection_frames) => connection_frames.register(frame, is_known_access_point),
            Err(e) => error!("Could not acquire connection frames table mutex: {}", e)
        }
    }

    /*
     * An access point counts as known once it has been sending beacons for a while. One beacon is not enough: it
     * could be sent right before the frames it is meant to make room for.
     */
    fn knows_access_point(&self, bssid: &str) -> bool {
        match self.channel_switches.lock() {
            Ok(channel_switches) => channel_switches.has_beaconed_for(bssid, KNOWN_ACCESS_POINT_MINIMUM_AGE),
            Err(e) => {
                error!("Could not acquire channel switch table mutex: {}", e);
                false
            }
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
        match self.disco.lock() {
            Ok(mut disco) => {
                match self.metrics.lock() {
                    Ok(mut metrics) => {
                        metrics.set_gauge("tables.dot11.disco.frames.size", disco.table.entries.len() as i128);
                        metrics.set_gauge("tables.dot11.disco.frames.dropped", disco.table.dropped_frames as i128);
                        metrics.set_gauge("tables.dot11.disco.frames.overflow", disco.table.overflow.len() as i128);
                    },
                    Err(e) => error!("Could not acquire metrics mutex: {}", e)
                }

                disco.clear();
            },
            Err(e) => error!("Could not acquire disco table mutex: {}", e)
        }

        match self.connection_frames.lock() {
            Ok(mut connection_frames) => {
                match self.metrics.lock() {
                    Ok(mut metrics) => {
                        metrics.set_gauge(
                            "tables.dot11.connection_frames.size", connection_frames.table.entries.len() as i128
                        );
                        metrics.set_gauge(
                            "tables.dot11.connection_frames.dropped", connection_frames.table.dropped_frames as i128
                        );
                        metrics.set_gauge(
                            "tables.dot11.connection_frames.overflow", connection_frames.table.overflow.len() as i128
                        );
                    },
                    Err(e) => error!("Could not acquire metrics mutex: {}", e)
                }

                connection_frames.clear();
            },
            Err(e) => error!("Could not acquire connection frames table mutex: {}", e)
        }

        match self.channel_switches.lock() {
            Ok(mut channel_switches) => {
                match self.metrics.lock() {
                    Ok(mut metrics) => {
                        metrics.set_gauge(
                            "tables.dot11.channel_switches.size", channel_switches.entries.len() as i128
                        );
                        metrics.set_gauge(
                            "tables.dot11.channel_switches.dropped", channel_switches.dropped_frames as i128
                        );
                        metrics.set_gauge(
                            "tables.dot11.channel_switches.action_frames", channel_switches.action_frames as i128
                        );
                        metrics.set_gauge(
                            "tables.dot11.channel_switches.beacon_states.size",
                            channel_switches.beacon_state_count() as i128
                        );
                        metrics.set_gauge(
                            "tables.dot11.channel_switches.declared_frequencies.dropped",
                            channel_switches.dropped_declared_frequencies as i128
                        );
                        metrics.set_gauge(
                            "tables.dot11.channel_switches.beacon_states.rejected",
                            channel_switches.rejected_beacon_states as i128
                        );
                    },
                    Err(e) => error!("Could not acquire metrics mutex: {}", e)
                }

                channel_switches.end_report(Instant::now());
            },
            Err(e) => error!("Could not acquire channel switch table mutex: {}", e)
        }

        clear_mutex_vector(&self.alerts);
    }

    fn generate_report(&self) -> Dot11TableReport {
        let mut bssid_report: HashMap<String, BssidReport> = HashMap::new();

        let mut declared_frequencies = match self.channel_switches.lock() {
            Ok(channel_switches) => channel_switches.declared_frequencies(),
            Err(e) => {
                error!("Could not acquire channel switch table mutex: {}", e);
                HashMap::new()
            }
        };

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

                        netsec_report.push(SecurityInformationReport {
                            protocols,
                            suites,
                            pmf,
                            operating_channel_validation: netsecinfo.operating_channel_validation,
                            beacon_protection: netsecinfo.beacon_protection
                        });
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
                        signal_strength: stats.signal_strengths.total(),
                        signal_by_frequency: stats.signal_strengths.by_frequency()
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
                        declared_frequencies: declared_frequencies.remove(bssid).unwrap_or_default()
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
                            ),
                            signal_by_frequency: info.signal_by_frequency.by_frequency()
                        }
                    );
                }
            },
            Err(e) => error!("Could not acquire BSSIDs table mutex: {}", e),
        }

        // One instant for both, so that the frame ages of a report compare.
        let now = Instant::now();

        let disco_report = match self.disco.lock() {
            Ok(disco) => disco.to_report(now),
            Err(e) => {
                error!("Could not acquire disco table mutex: {}", e);
                Dot11DiscoReport { frames: Vec::new(), overflow: Vec::new() }
            }
        };

        let (connection_frames_report, connection_frame_overflow_report) = match self.connection_frames.lock() {
            Ok(connection_frames) => (connection_frames.to_report(now), connection_frames.overflow_report(now)),
            Err(e) => {
                error!("Could not acquire connection frames table mutex: {}", e);
                (Vec::new(), Vec::new())
            }
        };

        let channel_switches_report = match self.channel_switches.lock() {
            Ok(channel_switches) => channel_switches.to_report(),
            Err(e) => {
                error!("Could not acquire channel switch table mutex: {}", e);
                Vec::new()
            }
        };

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
            connection_frames: connection_frames_report,
            connection_frame_overflow: connection_frame_overflow_report,
            channel_switches: channel_switches_report,
            alerts: alerts_report
        }
    }
}

// yo this all below here needs some serious refactoring and dedup lmao

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

#[cfg(test)]
mod client_signal_strength_tests {
    use super::ClientSignalStrengths;

    #[test]
    fn a_client_that_sent_nothing_has_no_signal_strength() {
        let strengths = ClientSignalStrengths::default();

        assert!(strengths.total().is_none());
        assert!(strengths.by_frequency().is_empty());
    }

    #[test]
    fn signal_strength_is_kept_per_frequency() {
        let mut strengths = ClientSignalStrengths::default();
        strengths.register(2437, -60);
        strengths.register(2437, -64);
        strengths.register(2442, -80);

        let total = strengths.total().unwrap();
        assert_eq!((total.min, total.max), (-80, -60));
        assert!((total.average - -68.0).abs() < 0.01);

        let by_frequency = strengths.by_frequency();
        assert_eq!(by_frequency.len(), 2);
        assert_eq!((by_frequency[&2437].min, by_frequency[&2437].max, by_frequency[&2437].frames), (-64, -60, 2));
        assert!((by_frequency[&2437].average - -62.0).abs() < 0.01);
        assert_eq!((by_frequency[&2442].min, by_frequency[&2442].max, by_frequency[&2442].frames), (-80, -80, 1));
    }
}
