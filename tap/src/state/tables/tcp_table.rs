use std::{sync::{Arc}};
use std::collections::{BTreeMap, HashMap, HashSet};
use std::net::IpAddr;
use std::sync::{Mutex, MutexGuard};
use chrono::{DateTime, Duration, Utc};
use log::{debug, error, trace, warn};
use strum_macros::Display;
use crate::protocols::detection::l7_tagger::{L7Tag, tag_tcp_sessions, TcpTaggingFingerprint};
use crate::wired::packets::{TcpSegment};
use crate::state::tables::tcp_table::TcpSessionState::{ClosedFin, ClosedRst, ClosedTimeout, Established, FinWait1, FinWait2, Refused, SynReceived, SynSent};
use crate::protocols::parsers::l4_key::L4Key;
use crate::wired::traffic_direction::TrafficDirection;
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::tcp_sessions_report;
use crate::messagebus::bus::Bus;
use crate::metrics::Metrics;

pub struct TcpTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    ethernet_bus: Arc<Bus>,
    metrics: Arc<Mutex<Metrics>>,
    sessions: Mutex<HashMap<L4Key, TcpSession>>,
    reassembly_buffer_size: i32,
    session_timeout_seconds: i32,
    handshake_timeout_seconds: i32
}

// Default for `protocols.tcp.handshake_timeout_seconds`. Real stacks stop retransmitting a SYN after ~1 minute.
pub const DEFAULT_HANDSHAKE_TIMEOUT_SECONDS: i32 = 60;

#[derive(Debug)]
pub struct TcpSession {
    pub session_key: L4Key,
    pub state: TcpSessionState,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub source_port: u16,
    pub destination_address: IpAddr,
    pub destination_port: u16,
    pub start_time: DateTime<Utc>,
    pub end_time: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>,
    pub segments_count: u64,
    pub segments_count_incremental: u64, // New segments since last report.
    pub bytes_count_rx: u64,
    pub bytes_count_tx: u64,
    pub bytes_count_rx_incremental: u64, // New bytes since last report.
    pub bytes_count_tx_incremental: u64, // New bytes since last report.
    pub segments_client_to_server: BTreeMap<u32, Vec<u8>>,
    pub segments_server_to_client: BTreeMap<u32, Vec<u8>>,
    pub syn_ip_ttl: u8,
    pub syn_ip_tos: u8,
    pub syn_ip_df: bool,
    pub syn_cwr: bool,
    pub syn_ece: bool,
    pub syn_window_size: u16,
    pub syn_maximum_segment_size: Option<u16>,
    pub syn_window_scale_multiplier: Option<u8>,
    pub syn_options: Vec<u8>,
    pub tags: HashSet<L7Tag>,
    /*
     * Session attributes as seen by the L7 taggers at the last tagging run. Used to decide
     * if attributes changed and a new tagging run is required.
     */
    pub tagging_fingerprint: Option<TcpTaggingFingerprint>
}

impl TcpSession {

    pub fn bytes_count(&self) -> u64 {
        self.bytes_count_rx + self.bytes_count_tx
    }

}

#[derive(PartialEq, Debug, Display, Clone)]
pub enum TcpSessionState {
    SynSent,
    SynReceived,
    Established,
    FinWait1,
    FinWait2,
    ClosedFin,
    ClosedRst,
    ClosedTimeout,
    Refused
}

impl TcpTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>,
               ethernet_bus: Arc<Bus>,
               metrics: Arc<Mutex<Metrics>>,
               reassembly_buffer_size: i32,
               session_timeout_seconds: i32,
               handshake_timeout_seconds: i32) -> Self {
        Self {
            leaderlink,
            ethernet_bus,
            metrics,
            sessions: Mutex::new(HashMap::new()),
            reassembly_buffer_size,
            session_timeout_seconds,
            handshake_timeout_seconds
        }
    }

    pub fn register_segment(&mut self, segment: &Arc<TcpSegment>) {
        match self.sessions.lock() {
            Ok(mut sessions) => {
                match sessions.get_mut(&segment.session_key) {
                    Some(session) => {
                        let mut timer = Timer::new();
                        let session_state = Self::determine_session_state(segment, Some(session));

                        session.most_recent_segment_time = segment.timestamp;
                        session.state = session_state.clone();
                        session.segments_count += 1;
                        session.segments_count_incremental += 1;

                        let (bytes_tx, bytes_rx) = segment.get_directional_byte_counts(session);

                        session.bytes_count_rx += bytes_rx;
                        session.bytes_count_tx += bytes_tx;
                        session.bytes_count_rx_incremental += bytes_rx;
                        session.bytes_count_tx_incremental += bytes_tx;

                        if session.end_time.is_none()
                            && (session_state == ClosedFin || session_state == ClosedRst || session_state == Refused) {
                            session.end_time = Some(segment.timestamp);
                        }

                        if !segment.payload.is_empty() {
                            if session.bytes_count() <= self.reassembly_buffer_size as u64 {
                                match segment.determine_direction(session) {
                                    TrafficDirection::ClientToServer => {
                                        insert_session_segment(segment, &mut session.segments_client_to_server);
                                    }
                                    TrafficDirection::ServerToClient => {
                                        insert_session_segment(segment, &mut session.segments_server_to_client);
                                    }
                                }
                            } else {
                                trace!("TCP session [{:?}] has reached maximum segment buffer size.",
                                    session);
                            }
                        }

                        trace!("Segment of existing TCP Session: {:?}, State: {:?}, Flags: {:?}",
                            segment.session_key, session_state, segment.flags);

                        timer.stop();
                        record_timer(
                            timer.elapsed_microseconds(),
                            "tables.tcp.sessions.timer.register_existing",
                            &self.metrics
                        );
                    },
                    None => {
                        // First time seeing this session.
                        let mut timer = Timer::new();

                        let session_state = Self::determine_session_state(segment, None);

                        // We only record new sessions, not mid-session.
                        if session_state == SynSent {
                            /*
                             * This branch only runs for a bare SYN, which is by definition
                             * the client opening segment, so direction is always ClientToServer.
                             */
                            let (bytes_tx, bytes_rx) = (segment.size as u64, 0);

                            let new_session = TcpSession {
                                session_key: segment.session_key.clone(),
                                state: session_state.clone(),
                                start_time: segment.timestamp,
                                end_time: None,
                                most_recent_segment_time: segment.timestamp,
                                source_mac: segment.source_mac.clone(),
                                destination_mac: segment.destination_mac.clone(),
                                source_address: segment.source_address,
                                source_port: segment.source_port,
                                destination_address: segment.destination_address,
                                destination_port: segment.destination_port,
                                segments_count: 1,
                                segments_count_incremental: 1,
                                bytes_count_rx: bytes_rx,
                                bytes_count_tx: bytes_tx,
                                bytes_count_rx_incremental: bytes_rx,
                                bytes_count_tx_incremental: bytes_tx,
                                segments_client_to_server: BTreeMap::new(),
                                segments_server_to_client: BTreeMap::new(),
                                syn_ip_ttl: segment.ip_ttl,
                                syn_ip_tos: segment.ip_tos,
                                syn_ip_df: segment.ip_df,
                                syn_cwr: segment.flags.cwr,
                                syn_ece: segment.flags.ece,
                                syn_window_size: segment.window_size,
                                syn_maximum_segment_size: segment.maximum_segment_size,
                                syn_window_scale_multiplier: segment.window_scale_multiplier,
                                syn_options: segment.options.clone(),
                                tags: HashSet::new(),
                                tagging_fingerprint: None
                            };

                            trace!("New TCP Session: {:?}, State: {:?}, Flags: {:?}",
                            segment.session_key, session_state, segment.flags);

                            sessions.insert(segment.session_key.clone(), new_session);

                            timer.stop();
                            record_timer(
                                timer.elapsed_microseconds(),
                                "tables.tcp.sessions.timer.register_new",
                                &self.metrics
                            );
                        }
                    }
                }
            },
            Err(e) => {
                error!("Could not acquire TCP sessions table mutex: {}", e);
            }
        }
    }

    pub fn process_report(&self) {
        match self.sessions.lock() {
            Ok(mut sessions) => {
                // Mark all timed out sessions in table as ClosedTimeout.
                let mut timer = Timer::new();
                timeout_sweep(
                    &mut sessions,
                    self.session_timeout_seconds as i64,
                    self.handshake_timeout_seconds as i64
                );
                timer.stop();
                record_timer(
                    timer.elapsed_microseconds(),
                    "tables.tcp.timer.sessions.timeout_sweep",
                    &self.metrics
                );

                // Scan session payloads and tag.
                let mut timer = Timer::new();
                tag_tcp_sessions(&mut sessions, self.ethernet_bus.clone(), self.metrics.clone());
                timer.stop();
                record_timer(
                    timer.elapsed_microseconds(),
                    "tables.tcp.timer.sessions.tagging",
                    &self.metrics
                );

                // Generate JSON.
                let mut timer = Timer::new();
                let report = match serde_json::to_string(&tcp_sessions_report::generate(&sessions)) {
                    Ok(report) => report,
                    Err(e) => {
                        error!("Could not serialize TCP sessions report: {}", e);
                        return;
                    }
                };
                timer.stop();
                record_timer(
                    timer.elapsed_microseconds(),
                    "tables.tcp.timer.report_generation",
                    &self.metrics
                );

                // Send report.
                match self.leaderlink.lock() {
                    Ok(link) => {
                        if let Err(e) = link.send_report("tcp/sessions", report) {
                            error!("Could not submit TCP sessions report: {}", e);
                        }
                    },
                    Err(e) => error!("Could not acquire leader link lock for TCP report submission: {}", e)
                }

                // Delete all timed out, refused and closedfin/closedrst sessions in table.
                retention_sweep(&mut sessions);

                // Reset all incremental counters.
                incremental_counter_sweep(&mut sessions);
            },
            Err(e) => {
                error!("Could not acquire TCP sessions table mutex for report generation: {}", e);
            }
        }
    }

    pub fn calculate_metrics(&self) {
        let (sessions_size, sessions_bytes): (i128, i128) = match self.sessions.lock() {
            Ok(sessions) => {
                let mut bytes: i128 = 0;
                sessions.values().for_each(|s| bytes += s.bytes_count() as i128);
                (sessions.len() as i128, bytes)
            },
            Err(e) => {
                error!("Could not acquire mutex to calculate TCP session table sizes: {}", e);

                (-1, -1)
            }
        };

        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("tables.tcp.sessions.size", sessions_size);
                metrics.set_gauge("tables.tcp.sessions.bytes", sessions_bytes);
            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

    fn determine_session_state(segment: &TcpSegment, session: Option<&TcpSession>)
        -> TcpSessionState {
        match session {
            Some(session) => {
                // Do not re-open closed connections in case of out-of-order segments.
                if session.state == ClosedFin { return ClosedFin }
                if session.state == ClosedRst { return ClosedRst }
                if session.state == ClosedTimeout { return ClosedTimeout }

                if segment.flags.syn && segment.flags.ack {
                    // SYN ACK.
                    SynReceived
                } else if segment.flags.ack && session.state == SynReceived {
                    // Final ACK. Handshake complete.
                    Established
                } else if segment.flags.reset && session.state == SynSent {
                    // SYN got an immediate RST response. Connection rejected by host.
                    Refused
                } else if segment.flags.reset {
                    ClosedRst
                } else if segment.flags.fin && session.state == Established {
                    // Initial FIN
                    FinWait1
                } else if segment.flags.ack && session.state == FinWait1 {
                    // FIN ACK
                    FinWait2
                } else if segment.flags.ack && session.state == FinWait2 {
                    // Final FIN.
                    ClosedFin
                } else {
                    Established
                }
            },
            None => {
                // First time we see this session.
                if segment.flags.syn && !segment.flags.ack {
                    SynSent
                } else if segment.flags.syn && segment.flags.ack {
                    SynReceived
                } else if !segment.flags.syn && segment.flags.ack {
                    /*
                     * Either normal segment flow in established connection or final
                     * ACK in handshake or final ACK in FIN process. Considering that there was
                     * no previous segment recorded, we assume the likeliest case: A segment
                     * exchanged during an established connection.
                     *
                     * We'd falsely mark this connection as ESTABLISHED and have it time out if
                     * this was a FIN ACK and accept that risk, as it can only happen right after
                     * capture start.
                     *
                     * Additionally, the processing logic is only recording newly established
                     * connections and discarding anything else.
                     */
                    Established
                } else if segment.flags.fin {
                    FinWait1
                } else if segment.flags.reset {
                    ClosedRst
                } else {
                    warn!("Unexpected flags for new TCP session: <{:?}> {:?}",
                        segment.session_key, segment.flags);

                    Established
                }
            }
        }
    }
}

fn insert_session_segment(segment: &TcpSegment, segments: &mut BTreeMap<u32, Vec<u8>>) {
    match segments.get(&segment.sequence_number) {
        Some(_) => {
            trace!("TCP session {:?} already contains segment {}. Ignoring as \
                                retransmission.", segment.session_key, segment.sequence_number)
        },
        None => {
            // New segment. (not a retransmission of segment we already recorded)
            segments.insert(segment.sequence_number, segment.payload.clone());
        }
    }
}

fn is_terminal_state(state: &TcpSessionState) -> bool {
    matches!(state, ClosedFin | ClosedRst | ClosedTimeout | Refused)
}

fn timeout_sweep(sessions: &mut MutexGuard<HashMap<L4Key, TcpSession>>,
                 session_timeout: i64,
                 handshake_timeout: i64) {
    let now = Utc::now();
    let session_timeout = Duration::try_seconds(session_timeout).unwrap();
    let handshake_timeout = Duration::try_seconds(handshake_timeout).unwrap();

    for session in sessions.values_mut() {
        // Never overwrite a state that is already final. (Would turn Refused into ClosedTimeout.)
        if is_terminal_state(&session.state) {
            continue;
        }

        let idle = now - session.most_recent_segment_time;

        // Sessions that never completed the handshake time out early.
        let timeout = match session.state {
            SynSent | SynReceived => handshake_timeout,
            _ => session_timeout
        };

        if idle > timeout {
            debug!("Timeout sweep for TCP session: {:?}", session);
            session.state = ClosedTimeout;
            session.end_time = Some(now);
        }
    }
}

fn retention_sweep(sessions: &mut MutexGuard<HashMap<L4Key, TcpSession>>) {
    sessions.retain(|_, s| !is_terminal_state(&s.state))
}

fn incremental_counter_sweep(sessions: &mut MutexGuard<HashMap<L4Key, TcpSession>>) {
    for session in sessions.values_mut() {
        session.segments_count_incremental = 0;
        session.bytes_count_rx_incremental = 0;
        session.bytes_count_tx_incremental = 0;
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    use std::net::Ipv4Addr;

    fn session(port: u16, state: TcpSessionState, idle_seconds: i64) -> (L4Key, TcpSession) {
        let src = IpAddr::V4(Ipv4Addr::new(10, 0, 0, 1));
        let dst = IpAddr::V4(Ipv4Addr::new(10, 0, 0, 2));
        let key = L4Key::new(src, 40000, dst, port);
        let last = Utc::now() - Duration::try_seconds(idle_seconds).unwrap();

        (key.clone(), TcpSession {
            session_key: key,
            state,
            source_mac: None,
            destination_mac: None,
            source_address: src,
            source_port: 40000,
            destination_address: dst,
            destination_port: port,
            start_time: last,
            end_time: None,
            most_recent_segment_time: last,
            segments_count: 1,
            segments_count_incremental: 1,
            bytes_count_rx: 0,
            bytes_count_tx: 60,
            bytes_count_rx_incremental: 0,
            bytes_count_tx_incremental: 60,
            segments_client_to_server: BTreeMap::new(),
            segments_server_to_client: BTreeMap::new(),
            syn_ip_ttl: 64,
            syn_ip_tos: 0,
            syn_ip_df: true,
            syn_cwr: false,
            syn_ece: false,
            syn_window_size: 1024,
            syn_maximum_segment_size: None,
            syn_window_scale_multiplier: None,
            syn_options: Vec::new(),
            tags: HashSet::new(),
            tagging_fingerprint: None
        })
    }

    fn table(entries: Vec<(L4Key, TcpSession)>) -> Mutex<HashMap<L4Key, TcpSession>> {
        Mutex::new(entries.into_iter().collect())
    }

    fn state_of(sessions: &MutexGuard<HashMap<L4Key, TcpSession>>, port: u16) -> TcpSessionState {
        let key = L4Key::new(
            IpAddr::V4(Ipv4Addr::new(10, 0, 0, 1)), 40000,
            IpAddr::V4(Ipv4Addr::new(10, 0, 0, 2)), port
        );
        sessions.get(&key).unwrap().state.clone()
    }

    #[test]
    fn handshake_sessions_time_out_early() {
        let t = table(vec![
            session(1, SynSent, 90),
            session(2, SynReceived, 90),
            session(3, SynSent, 30),
            session(4, Established, 90)
        ]);
        let mut sessions = t.lock().unwrap();

        timeout_sweep(&mut sessions, 1800, 60);

        assert_eq!(state_of(&sessions, 1), ClosedTimeout);
        assert_eq!(state_of(&sessions, 2), ClosedTimeout);
        assert_eq!(state_of(&sessions, 3), SynSent);
        assert_eq!(state_of(&sessions, 4), Established);
        assert!(sessions.get(&session(1, SynSent, 0).0).unwrap().end_time.is_some());
    }

    #[test]
    fn established_sessions_use_session_timeout() {
        let t = table(vec![
            session(1, Established, 1700),
            session(2, Established, 1900),
            session(3, FinWait1, 1900)
        ]);
        let mut sessions = t.lock().unwrap();

        timeout_sweep(&mut sessions, 1800, 60);

        assert_eq!(state_of(&sessions, 1), Established);
        assert_eq!(state_of(&sessions, 2), ClosedTimeout);
        assert_eq!(state_of(&sessions, 3), ClosedTimeout);
    }

    #[test]
    fn terminal_states_are_never_overwritten() {
        let t = table(vec![
            session(1, Refused, 5000),
            session(2, ClosedFin, 5000),
            session(3, ClosedRst, 5000)
        ]);
        let mut sessions = t.lock().unwrap();

        timeout_sweep(&mut sessions, 1800, 60);

        assert_eq!(state_of(&sessions, 1), Refused);
        assert_eq!(state_of(&sessions, 2), ClosedFin);
        assert_eq!(state_of(&sessions, 3), ClosedRst);
    }

    #[test]
    fn retention_sweep_drops_refused_and_closed_sessions() {
        let t = table(vec![
            session(1, Refused, 0),
            session(2, ClosedFin, 0),
            session(3, ClosedRst, 0),
            session(4, ClosedTimeout, 0),
            session(5, SynSent, 0),
            session(6, Established, 0),
            session(7, FinWait2, 0)
        ]);
        let mut sessions = t.lock().unwrap();

        retention_sweep(&mut sessions);

        assert_eq!(sessions.len(), 3);
        assert_eq!(state_of(&sessions, 5), SynSent);
        assert_eq!(state_of(&sessions, 6), Established);
        assert_eq!(state_of(&sessions, 7), FinWait2);
    }
}
