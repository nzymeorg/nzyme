use std::collections::{HashMap, HashSet, VecDeque};
use std::net::IpAddr;
use std::sync::{Arc, LockResult, Mutex, MutexGuard};
use chrono::{DateTime, Duration, Utc};
use log::{error, info};
use strum_macros::Display;
use crate::helpers::timer::{record_timer, Timer};
use crate::wired::packets::Datagram;
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::udp_conversations_report;
use crate::messagebus::bus::Bus;
use crate::metrics::Metrics;
use crate::protocols::detection::l7_tagger::{tag_udp_sessions, L7Tag};
use crate::protocols::detection::taggers::tagger_command::TaggerCommand;
use crate::protocols::detection::taggers::network_protocols::vpn_handshake_observer::VpnObservations;
use crate::protocols::parsers::l4_key::L4Key;
use crate::protocols::tools::ip_tools::is_site_local;
use crate::wired::traffic_direction::TrafficDirection;

const MAX_BYTES_PER_DIRECTION: usize = 256;
const RECENT_RING_MAX_DATAGRAMS: usize = 64;

// Byte-heaviness orientation thresholds for WebRTC/media flows.
const ORIENT_BY_BYTES_MIN_TOTAL: u64 = 50_000;
// The heavier direction must be at least this fraction of total to decide.
const ORIENT_BY_BYTES_MAJORITY: f64 = 0.70;

pub struct UdpTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    ethernet_bus: Arc<Bus>,
    metrics: Arc<Mutex<Metrics>>,
    conversations: Mutex<HashMap<L4Key, UdpConversation>>,
}

#[derive(Debug)]
pub struct UdpConversation {
    pub state: UdpConversationState,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub source_port: u16,
    pub destination_address: IpAddr,
    pub destination_port: u16,
    pub start_time: DateTime<Utc>,
    pub end_time: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>,
    pub datagrams_count: u64,
    pub datagrams_count_incremental: u64, // New datagrams since last report.
    pub bytes_count_rx: u64,
    pub bytes_count_tx: u64,
    pub bytes_count_rx_incremental: u64, // New bytes since last report.
    pub bytes_count_tx_incremental: u64, // New bytes since last report.
    pub datagrams_client_to_server: VecDeque<Vec<u8>>,
    pub datagrams_server_to_client: VecDeque<Vec<u8>>,
    pub capture_recent: bool,
    pub recent_client_to_server: VecDeque<Vec<u8>>,
    pub recent_server_to_client: VecDeque<Vec<u8>>,
    // VPN handshake and transport facts seen on any datagram, independent of the bounded buffers above.
    pub vpn: VpnObservations,
    pub tags: HashSet<L7Tag>
}

#[derive(PartialEq, Debug, Display, Clone)]
pub enum UdpConversationState {
    Active,
    Closed
}

impl UdpTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>,
               ethernet_bus: Arc<Bus>,
               metrics: Arc<Mutex<Metrics>>) -> Self {
        Self {
            leaderlink,
            ethernet_bus,
            metrics,
            conversations: Mutex::new(HashMap::new())
        }
    }

    pub fn register_datagram(&mut self, datagram: Arc<Datagram>) {
        let tags = match datagram.tags.lock() {
            Ok(tags) => tags.clone(),
            Err(e) => {
                error!("Could not acquire datagram tags mutex: {}", e);
                HashSet::new()
            }
        };

        match self.conversations.lock() {
            Ok(mut conversations) => {
                match conversations.get_mut(&datagram.session_key) {
                    Some(c) => {
                        if c.most_recent_segment_time < Utc::now() - Duration::seconds(3) {
                            /*
                             * Stale: treat as a brand-new conversation. VPN tunnel tags carry
                             * over because the tunnel on this 5-tuple continues and its later
                             * datagrams may not allow a fresh identification.
                             */
                            let mut tags = tags;
                            tags.extend(c.tags.iter()
                                .filter(|t| matches!(t, L7Tag::WireGuard | L7Tag::OpenVPN))
                                .cloned());
                            self.insert_new_conversation(datagram, tags, &mut conversations)
                        } else {
                            let mut timer = Timer::new();
                            let traffic_direction = datagram.determine_direction(c);
                            let (bytes_tx, bytes_rx) = datagram.get_directional_byte_counts(c);

                            c.bytes_count_rx += bytes_rx;
                            c.bytes_count_tx += bytes_tx;
                            c.bytes_count_rx_incremental += bytes_rx;
                            c.bytes_count_tx_incremental += bytes_tx;

                            c.most_recent_segment_time = datagram.timestamp;
                            c.datagrams_count += 1;
                            c.datagrams_count_incremental += 1;

                            match traffic_direction {
                                TrafficDirection::ClientToServer =>
                                    push_bounded(&mut c.datagrams_client_to_server, datagram.payload.clone()),
                                TrafficDirection::ServerToClient =>
                                    push_bounded(&mut c.datagrams_server_to_client, datagram.payload.clone()),
                            }

                            if c.capture_recent {
                                match traffic_direction {
                                    TrafficDirection::ClientToServer =>
                                        push_recent(&mut c.recent_client_to_server, datagram.payload.clone()),
                                    TrafficDirection::ServerToClient =>
                                        push_recent(&mut c.recent_server_to_client, datagram.payload.clone()),
                                }
                            }

                            c.vpn.observe(&datagram.payload, datagram.source_address, datagram.source_port);

                            c.tags.extend(tags);

                            timer.stop();
                            record_timer(
                                timer.elapsed_microseconds(),
                                "tables.udp.conversations.timer.register_existing",
                                &self.metrics
                            );
                        }
                    },
                    None => {
                        self.insert_new_conversation(datagram, tags, &mut conversations)
                    }
                }
            }
            Err(e) => {
                error!("Could not acquire UDP conversations table mutex: {}", e);
            }
        }
    }

    fn insert_new_conversation(&self, datagram: Arc<Datagram>, tags: HashSet<L7Tag>,
                               conversations: &mut MutexGuard<HashMap<L4Key, UdpConversation>>) {
        let mut timer = Timer::new();

        /*
         * The datagram that creates a conversation defines its client (source_*),
         * so its direction is by definition ClientToServer.
         */
        let (bytes_tx, bytes_rx) = (datagram.size as u64, 0);

        let mut datagrams_client_to_server = VecDeque::new();
        let datagrams_server_to_client = VecDeque::new();

        push_bounded(&mut datagrams_client_to_server, datagram.payload.clone());

        let mut vpn = VpnObservations::default();
        vpn.observe(&datagram.payload, datagram.source_address, datagram.source_port);

        conversations.insert(
            datagram.session_key.clone(),
            UdpConversation {
                state: UdpConversationState::Active,
                source_mac: datagram.source_mac.clone(),
                destination_mac: datagram.destination_mac.clone(),
                source_address: datagram.source_address,
                source_port: datagram.source_port,
                destination_address: datagram.destination_address,
                destination_port: datagram.destination_port,
                start_time: datagram.timestamp,
                end_time: None,
                most_recent_segment_time: datagram.timestamp,
                datagrams_count: 1,
                datagrams_count_incremental: 1,
                bytes_count_rx: bytes_rx,   // 0
                bytes_count_tx: bytes_tx,   // size
                bytes_count_rx_incremental: bytes_rx,
                bytes_count_tx_incremental: bytes_tx,
                datagrams_client_to_server,
                datagrams_server_to_client,
                capture_recent: false,
                recent_client_to_server: VecDeque::new(),
                recent_server_to_client: VecDeque::new(),
                vpn,
                tags,
            }
        );

        timer.stop();
        record_timer(
            timer.elapsed_microseconds(),
            "tables.udp.conversations.timer.register_new",
            &self.metrics
        );
    }

    pub fn process_report(&self) {
        match self.conversations.lock() {
            Ok(mut conversations) => {
                // Set end time and state in all expired conversations.
                for c in conversations.values_mut() {
                    if Utc::now() - c.most_recent_segment_time >
                        Duration::try_seconds(60).unwrap() {

                        c.end_time = Some(c.most_recent_segment_time);
                        c.state = UdpConversationState::Closed;
                    }
                }

                // Scan session payloads and tag. (Sets STUN/RTP/etc tags and applies
                // the STUN tagger's own OrientClientTo.)
                let mut timer = Timer::new();
                tag_udp_sessions(&mut conversations, self.ethernet_bus.clone(), self.metrics.clone());
                timer.stop();
                record_timer(
                    timer.elapsed_microseconds(),
                    "tables.tcp.timer.sessions.tagging",
                    &self.metrics
                );

                // Further orientation where possible.
                for c in conversations.values_mut() {
                    orient_webrtc(c);
                }

                // Generate JSON.
                let mut timer = Timer::new();
                let report = match serde_json::to_string(&udp_conversations_report::generate(&conversations)) {
                    Ok(report) => report,
                    Err(e) => {
                        error!("Could not serialize UDP conversations report: {}", e);
                        return;
                    }
                };
                timer.stop();
                record_timer(
                    timer.elapsed_microseconds(),
                    "tables.udp.timer.report_generation",
                    &self.metrics
                );

                // Send report.
                match self.leaderlink.lock() {
                    Ok(link) => {
                        if let Err(e) = link.send_report("udp/conversations", report) {
                            error!("Could not submit UDP conversations report: {}", e);
                        }
                    },
                    Err(e) => error!("Could not acquire leader link lock for UDP conversations \
                        report submission: {}", e)
                }

                // Delete all closed conversations.
                conversations.retain(|_key, c| c.state != UdpConversationState::Closed);
                // Reset all incremental counters.
                incremental_counter_sweep(&mut conversations);
            },
            Err(e) => {
                error!("Could not acquire UDP conversations table mutex for report \
                    generation and maintenance: {}", e);
            }
        }
    }

    pub fn calculate_metrics(&self) {
        let conversations_size: i128 = match self.conversations.lock() {
            Ok(c) => c.len() as i128,
            Err(e) => {
                error!("Could not acquire mutex to calculate UDP conversation table size: {}", e);

                -1
            }
        };

        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("tables.udp.conversations.size", conversations_size);
            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

}

fn incremental_counter_sweep(sessions: &mut MutexGuard<HashMap<L4Key, UdpConversation>>) {
    for session in sessions.values_mut() {
        session.datagrams_count_incremental = 0;
        session.bytes_count_rx_incremental = 0;
        session.bytes_count_tx_incremental = 0;
    }
}

fn push_bounded(q: &mut VecDeque<Vec<u8>>, payload: Vec<u8>) {
    // Current size.
    let size: usize = q.iter().map(|v| v.len()).sum();

    // If already full, exit.
    if size >= MAX_BYTES_PER_DIRECTION {
        return;
    }

    // If this datagram would exceed the maximum size, exit.
    if size + payload.len() > MAX_BYTES_PER_DIRECTION {
        return;
    }

    q.push_back(payload);
}

fn push_recent(q: &mut VecDeque<Vec<u8>>, payload: Vec<u8>) {
    q.push_back(payload);
    while q.len() > RECENT_RING_MAX_DATAGRAMS {
        q.pop_front();
    }
}

// Orient a WebRTC/media conversation so the meaningful endpoint is the source.
fn orient_webrtc(c: &mut UdpConversation) {
    // Only apply to WebRTC/media flows.
    if !c.tags.contains(&L7Tag::STUN) {
        return;
    }

    let total = c.bytes_count_tx + c.bytes_count_rx;

    // Level 1: Decisive byte imbalance. Heavy sender is source.
    if total >= ORIENT_BY_BYTES_MIN_TOTAL {
        // Bytes the current destination sent.
        let dest_sent = c.bytes_count_rx;
        // Bytes the current source sent.
        let src_sent = c.bytes_count_tx;
        let threshold = (total as f64) * ORIENT_BY_BYTES_MAJORITY;

        if (dest_sent as f64) >= threshold {
            // Destination is the heavy sender. Make it the source.
            TaggerCommand::OrientClientTo {
                address: c.destination_address,
                port: c.destination_port,
            }.apply(c);
            return;
        }
        if (src_sent as f64) >= threshold {
            // Source is already the heavy sender.
            return;
        }

        // Balanced despite volume. Fall through to heuristics.
    }

    // Level 2: site-local as source (internal asset) when there is a clear split.
    let src_local = is_site_local(c.source_address);
    let dst_local = is_site_local(c.destination_address);
    match (src_local, dst_local) {
        (true, false) => return, // Source is already the internal side.
        (false, true) => {
            TaggerCommand::OrientClientTo {
                address: c.destination_address,
                port: c.destination_port,
            }.apply(c);
            return;
        }
        _ => {} // Both or neither site-local. Fall through to Level 3
    }

    // Level 3: Canonical (lower endpoint as source)
    let src = (c.source_address, c.source_port);
    let dst = (c.destination_address, c.destination_port);
    if dst < src {
        TaggerCommand::OrientClientTo {
            address: c.destination_address,
            port: c.destination_port,
        }.apply(c);
    }
}