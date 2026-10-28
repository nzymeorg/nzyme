use std::collections::{BTreeMap, HashMap};
use std::net::IpAddr;
use std::sync::{Arc, Mutex};
use chrono::{DateTime, Duration, Utc};
use log::error;
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::vpn_tunnels_report;
use crate::metrics::Metrics;
use crate::protocols::parsers::l4_key::L4Key;
use crate::wired::packets::{GenericConnectionStatus, VpnHandshakeStatus, VpnTransport, VpnTunnel, VpnType};

// A UDP tunnel without datagrams for this long is reported as inactive.
const UDP_INACTIVE_AFTER_SECONDS: i64 = 90;
// Tunnels without any activity for this long are dropped from the table after the report.
const RETENTION_MINUTES: i64 = 10;

pub struct VpnTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    metrics: Arc<Mutex<Metrics>>,
    tunnels: Mutex<HashMap<L4Key, VpnTunnelRecord>>,
    /*
     * Handshake initiations by their protocol id, with the tunnel they were seen on. A response
     * to such an id seen on a different tunnel (the reply arrived on another port, for example)
     * completes the handshake of both.
     */
    initiations: Mutex<HashMap<u64, (L4Key, DateTime<Utc>)>>
}

#[derive(Debug, Clone)]
pub struct VpnTunnelRecord {
    pub vpn_type: VpnType,
    pub transport: VpnTransport,
    pub handshake_status: VpnHandshakeStatus,
    pub message_types: Vec<String>,
    pub provider: Option<String>,
    pub server_name: Option<String>,
    pub connection_status: GenericConnectionStatus,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub source_port: u16,
    pub destination_port: u16,
    pub established_at: DateTime<Utc>,
    pub terminated_at: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>,
    completed_bytes: u64,
    current_conversation_start: DateTime<Utc>,
    current_conversation_bytes: u64,
    completed_details: BTreeMap<String, String>,
    current_conversation_details: BTreeMap<String, String>
}

fn fold_details(into: &mut BTreeMap<String, String>, from: &BTreeMap<String, String>) {
    for (k, v) in from {
        match (into.get(k).and_then(|e| e.parse::<u64>().ok()), v.parse::<u64>().ok()) {
            (Some(existing), Some(new)) => { into.insert(k.clone(), (existing + new).to_string()); },
            _ => { into.insert(k.clone(), v.clone()); }
        }
    }
}

impl VpnTunnelRecord {

    pub fn tunneled_bytes(&self) -> u64 {
        self.completed_bytes + self.current_conversation_bytes
    }

    pub fn details(&self) -> BTreeMap<String, String> {
        let mut details = self.completed_details.clone();
        fold_details(&mut details, &self.current_conversation_details);
        details
    }

    fn new(t: &VpnTunnel) -> Self {
        Self {
            vpn_type: t.vpn_type,
            transport: t.transport,
            handshake_status: t.handshake_status,
            message_types: t.message_types.clone(),
            provider: t.provider.clone(),
            server_name: t.server_name.clone(),
            connection_status: t.connection_status.clone(),
            source_mac: t.source_mac.clone(),
            destination_mac: t.destination_mac.clone(),
            source_address: t.source_address,
            destination_address: t.destination_address,
            source_port: t.source_port,
            destination_port: t.destination_port,
            established_at: t.conversation_start_time,
            terminated_at: t.terminated_at,
            most_recent_segment_time: t.most_recent_segment_time,
            completed_bytes: 0,
            current_conversation_start: t.conversation_start_time,
            current_conversation_bytes: t.conversation_bytes,
            completed_details: BTreeMap::new(),
            current_conversation_details: t.details.clone()
        }
    }

    fn merge(&mut self, t: &VpnTunnel) {
        /*
         * A record created from mid-tunnel traffic may have the client and server swapped.
         * A later observation of a handshake has reliable orientation and wins.
         */
        if t.handshake_status > self.handshake_status {
            self.handshake_status = t.handshake_status;
            self.source_address = t.source_address;
            self.source_port = t.source_port;
            self.source_mac = t.source_mac.clone();
            self.destination_address = t.destination_address;
            self.destination_port = t.destination_port;
            self.destination_mac = t.destination_mac.clone();
        } else {
            if self.source_mac.is_none() {
                self.source_mac = t.source_mac.clone();
            }
            if self.destination_mac.is_none() {
                self.destination_mac = t.destination_mac.clone();
            }
        }

        for mt in &t.message_types {
            if !self.message_types.contains(mt) {
                self.message_types.push(mt.clone());
            }
        }

        // Once identified, a provider sticks across conversations of the same tunnel.
        if self.provider.is_none() {
            self.provider = t.provider.clone();
        }
        if self.server_name.is_none() {
            self.server_name = t.server_name.clone();
        }

        self.connection_status = t.connection_status.clone();
        self.terminated_at = t.terminated_at;

        if t.conversation_start_time < self.established_at {
            self.established_at = t.conversation_start_time;
        }
        if t.most_recent_segment_time > self.most_recent_segment_time {
            self.most_recent_segment_time = t.most_recent_segment_time;
        }

        if t.conversation_start_time == self.current_conversation_start {
            self.current_conversation_bytes = t.conversation_bytes;
            self.current_conversation_details = t.details.clone();
        } else {
            self.completed_bytes += self.current_conversation_bytes;
            fold_details(&mut self.completed_details, &self.current_conversation_details);
            self.current_conversation_start = t.conversation_start_time;
            self.current_conversation_bytes = t.conversation_bytes;
            self.current_conversation_details = t.details.clone();
        }
    }

}

impl VpnTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        Self {
            leaderlink,
            metrics,
            tunnels: Mutex::new(HashMap::new()),
            initiations: Mutex::new(HashMap::new())
        }
    }

    pub fn register_tunnel(&self, tunnel_ref: Arc<VpnTunnel>) {
        let tunnel = (*tunnel_ref).clone();

        match self.tunnels.lock() {
            Ok(mut tunnels) => {
                match tunnels.get_mut(&tunnel.session_key) {
                    Some(record) => record.merge(&tunnel),
                    None => {
                        tunnels.insert(tunnel.session_key.clone(), VpnTunnelRecord::new(&tunnel));
                    }
                }

                self.correlate_handshakes(&tunnel, &mut tunnels);
            },
            Err(e) => error!("Could not acquire VPN tunnels table mutex: {}", e)
        }
    }

    fn correlate_handshakes(&self, tunnel: &VpnTunnel, tunnels: &mut HashMap<L4Key, VpnTunnelRecord>) {
        let mut initiations = match self.initiations.lock() {
            Ok(i) => i,
            Err(e) => {
                error!("Could not acquire VPN handshake correlation mutex: {}", e);
                return;
            }
        };

        for id in &tunnel.initiation_ids {
            initiations.entry(*id).or_insert((tunnel.session_key.clone(), Utc::now()));
        }

        for id in &tunnel.response_to_ids {
            let other_key = match initiations.get(id) {
                Some((key, _)) if *key != tunnel.session_key => key.clone(),
                _ => continue
            };

            // Both tunnels saw one half of the same handshake.
            for key in [&other_key, &tunnel.session_key] {
                if let Some(record) = tunnels.get_mut(key) {
                    record.handshake_status = VpnHandshakeStatus::Complete;
                    record.current_conversation_details.insert(
                        "Handshake completed on another port".to_string(), "Yes".to_string()
                    );
                }
            }
        }
    }

    pub fn process_report(&self) {
        match self.tunnels.lock() {
            Ok(mut tunnels) => {
                let now = Utc::now();

                // Safety net for UDP tunnels whose last conversation was replaced before closing.
                let inactive_cutoff = now - Duration::seconds(UDP_INACTIVE_AFTER_SECONDS);
                for t in tunnels.values_mut() {
                    if t.transport == VpnTransport::UDP
                        && matches!(t.connection_status, GenericConnectionStatus::Active)
                        && t.most_recent_segment_time < inactive_cutoff {
                        t.connection_status = GenericConnectionStatus::Inactive;
                        t.terminated_at = Some(t.most_recent_segment_time);
                    }
                }

                // Generate JSON.
                let mut timer = Timer::new();
                let report = match serde_json::to_string(&vpn_tunnels_report::generate(&tunnels)) {
                    Ok(report) => report,
                    Err(e) => {
                        error!("Could not serialize VPN tunnels report: {}", e);
                        return;
                    }
                };
                timer.stop();
                record_timer(
                    timer.elapsed_microseconds(),
                    "tables.vpn.timer.report_generation",
                    &self.metrics
                );

                // Send report.
                match self.leaderlink.lock() {
                    Ok(link) => {
                        if let Err(e) = link.send_report("vpn/tunnels", report) {
                            error!("Could not submit VPN tunnels report: {}", e);
                        }
                    },
                    Err(e) => error!("Could not acquire leader link lock for VPN tunnels \
                                        report submission: {}", e)
                }

                // Clean up.
                let retention_cutoff = now - Duration::minutes(RETENTION_MINUTES);
                tunnels.retain(|_, t| t.most_recent_segment_time >= retention_cutoff);
                if let Ok(mut initiations) = self.initiations.lock() {
                    initiations.retain(|_, (_, seen)| *seen >= retention_cutoff);
                }
            },
            Err(e) => {
                error!("Could not acquire VPN tunnels table mutex for report generation: {}", e);
            }
        }
    }

    pub fn calculate_metrics(&self) {
        let tunnels_size: i128 = match self.tunnels.lock() {
            Ok(t) => t.len() as i128,
            Err(e) => {
                error!("Could not acquire mutex to calculate VPN tunnels table size: {}", e);

                -1
            }
        };

        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("tables.vpn.tunnels.size", tunnels_size);
            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

}
