use std::collections::HashMap;
use std::net::{IpAddr, SocketAddr};
use std::sync::{Arc, Mutex};
use chrono::{DateTime, Duration, Utc};
use log::error;
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::nat_traversal_report;
use crate::metrics::Metrics;
use crate::protocols::parsers::l4_key::L4Key;
use crate::protocols::parsers::stun_tagger::{StunFlow, StunTransport};

const STALE_AFTER_MINUTES: i64 = 1;

pub struct StunTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    metrics: Arc<Mutex<Metrics>>,
    negotiations: Mutex<HashMap<L4Key, NegotiationFlow>>,
    turn_activity: Mutex<HashMap<L4Key, TurnFlow>>,
    discoveries: Mutex<HashMap<L4Key, DiscoveryFlow>>,
}

#[derive(Debug, Clone)]
pub struct NegotiationFlow {
    pub session_key: L4Key,
    pub negotiation_key: Option<String>,
    pub transport: StunTransport,
    pub source_address: IpAddr,
    pub source_mac: Option<String>,
    pub source_port: u16,
    pub destination_address: IpAddr,
    pub destination_port: u16,
    pub ufrags: Vec<String>,
    pub successful: bool,
    pub is_turn: bool,
    pub turn_usernames: Vec<String>,
    pub mapped_addresses: Vec<SocketAddr>,
    pub relayed_addresses: Vec<SocketAddr>,
    pub peer_addresses: Vec<SocketAddr>,
    pub first_seen: DateTime<Utc>,
    pub last_activity: DateTime<Utc>,
}

#[derive(Debug, Clone)]
pub struct TurnFlow {
    pub session_key: L4Key,
    pub transport: StunTransport,
    pub source_address: IpAddr,
    pub source_mac: Option<String>,
    pub source_port: u16,
    pub destination_address: IpAddr,
    pub destination_port: u16,
    pub relayed_addresses: Vec<SocketAddr>,
    pub peer_addresses: Vec<SocketAddr>,
    pub mapped_addresses: Vec<SocketAddr>,
    pub turn_usernames: Vec<String>,
    pub first_seen: DateTime<Utc>,
    pub last_activity: DateTime<Utc>,
}

#[derive(Debug, Clone)]
pub struct DiscoveryFlow {
    pub session_key: L4Key,
    pub transport: StunTransport,
    pub source_mac: Option<String>,
    pub source_address: IpAddr,
    pub source_port: u16,
    pub destination_address: IpAddr,
    pub destination_port: u16,
    pub mapped_addresses: Vec<SocketAddr>,
    pub saw_success_response: bool,
    pub saw_error_response: bool,
    pub first_seen: DateTime<Utc>,
    pub last_activity: DateTime<Utc>,
}

impl StunTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        Self {
            leaderlink,
            metrics,
            negotiations: Mutex::new(HashMap::new()),
            turn_activity: Mutex::new(HashMap::new()),
            discoveries: Mutex::new(HashMap::new()),
        }
    }

    pub fn register_flow(&self, flow_ref: Arc<StunFlow>) {
        let flow = (*flow_ref).clone(); // Escape Arc.
        let key = flow.session_key.clone();

        if canonical_ice_ufrag(&flow.ufrags).is_some() {
            remove_flow(&self.turn_activity, &key, "TURN-activity");
            remove_flow(&self.discoveries, &key, "discoveries");
            match self.negotiations.lock() {
                Ok(mut negotiations) => upsert_negotiation(&mut negotiations, &flow),
                Err(e) => error!("Could not acquire ICE negotiations table mutex: {}", e),
            }
        } else if flow.is_turn {
            remove_flow(&self.discoveries, &key, "discoveries");
            match self.turn_activity.lock() {
                Ok(mut turn_activity) => upsert_turn(&mut turn_activity, &flow),
                Err(e) => error!("Could not acquire STUN TURN-activity table mutex: {}", e),
            }
        } else {
            match self.discoveries.lock() {
                Ok(mut discoveries) => upsert_discovery(&mut discoveries, &flow),
                Err(e) => error!("Could not acquire STUN discoveries table mutex: {}", e),
            }
        }
    }

    pub fn process_report(&self) {
        let cutoff = Utc::now() - Duration::minutes(STALE_AFTER_MINUTES);
        let mut timer = Timer::new();

        let negotiations = drain_active(&self.negotiations, cutoff, "ICE negotiations", |n| n.last_activity);
        let turn_activity = drain_active(&self.turn_activity, cutoff, "STUN TURN-activity", |t| t.last_activity);

        let discoveries = take_all(&self.discoveries, "STUN discoveries");

        let report = nat_traversal_report::generate(&negotiations, &turn_activity, &discoveries);
        let report_json = match serde_json::to_string(&report) {
            Ok(json) => json,
            Err(e) => {
                error!("Could not serialize NAT traversal report: {}", e);
                return;
            }
        };

        timer.stop();
        record_timer(
            timer.elapsed_microseconds(),
            "tables.stun.timer.report_generation",
            &self.metrics
        );

        match self.leaderlink.lock() {
            Ok(link) => {
                if let Err(e) = link.send_report("nat/traversal", report_json) {
                    error!("Could not submit NAT traversal report: {}", e);
                }
            }
            Err(e) => error!("Could not acquire leader link lock for NAT traversal report \
                              submission: {}", e),
        }
    }

    pub fn calculate_metrics(&self) {
        // TODO
    }

}

fn drain_active<V: Clone>(
    table: &Mutex<HashMap<L4Key, V>>,
    cutoff: DateTime<Utc>,
    name: &str,
    last_activity: impl Fn(&V) -> DateTime<Utc>,
) -> HashMap<L4Key, V> {
    match table.lock() {
        Ok(mut t) => {
            t.retain(|_, v| last_activity(v) >= cutoff);
            t.clone()
        }
        Err(e) => {
            error!("Could not acquire {} table mutex: {}", name, e);
            HashMap::new()
        }
    }
}

fn take_all<V>(table: &Mutex<HashMap<L4Key, V>>, name: &str) -> HashMap<L4Key, V> {
    match table.lock() {
        Ok(mut t) => std::mem::take(&mut *t),
        Err(e) => {
            error!("Could not acquire {} table mutex: {}", name, e);
            HashMap::new()
        }
    }
}

fn remove_flow<V>(table: &Mutex<HashMap<L4Key, V>>, key: &L4Key, name: &str) {
    match table.lock() {
        Ok(mut t) => { t.remove(key); }
        Err(e) => error!("Could not acquire STUN {} table mutex for reclassification: {}", name, e),
    }
}

fn upsert_negotiation(table: &mut HashMap<L4Key, NegotiationFlow>, flow: &StunFlow) {
    let record = table.entry(flow.session_key.clone()).or_insert_with(|| NegotiationFlow {
        session_key: flow.session_key.clone(),
        negotiation_key: canonical_ice_ufrag(&flow.ufrags).map(|(canonical, _, _)| canonical),
        transport: flow.transport,
        source_address: flow.source_address,
        source_mac: flow.source_mac.clone(),
        source_port: flow.source_port,
        destination_address: flow.destination_address,
        destination_port: flow.destination_port,
        ufrags: Vec::new(),
        is_turn: false,
        turn_usernames: Vec::new(),
        mapped_addresses: Vec::new(),
        relayed_addresses: Vec::new(),
        peer_addresses: Vec::new(),
        successful: false,
        first_seen: flow.established_at,
        last_activity: flow.most_recent_segment_time,
    });
    if record.negotiation_key.is_none() {
        record.negotiation_key = canonical_ice_ufrag(&flow.ufrags).map(|(canonical, _, _)| canonical);
    }
    record.is_turn |= flow.is_turn;
    backfill_mac(&mut record.source_mac, flow);
    extend_unique(&mut record.ufrags, &flow.ufrags);
    extend_unique(&mut record.turn_usernames, &flow.turn_usernames);
    extend_unique(&mut record.mapped_addresses, &flow.mapped_addresses);
    extend_unique(&mut record.relayed_addresses, &flow.relayed_addresses);
    extend_unique(&mut record.peer_addresses, &flow.peer_addresses);

    record.successful |= has_bidirectional_ufrag(&record.ufrags);

    widen_window(&mut record.first_seen, &mut record.last_activity, flow);
}

fn upsert_turn(table: &mut HashMap<L4Key, TurnFlow>, flow: &StunFlow) {
    let record = table.entry(flow.session_key.clone()).or_insert_with(|| TurnFlow {
        session_key: flow.session_key.clone(),
        transport: flow.transport,
        source_address: flow.source_address,
        source_mac: flow.source_mac.clone(),
        source_port: flow.source_port,
        destination_address: flow.destination_address,
        destination_port: flow.destination_port,
        relayed_addresses: Vec::new(),
        peer_addresses: Vec::new(),
        mapped_addresses: Vec::new(),
        turn_usernames: Vec::new(),
        first_seen: flow.established_at,
        last_activity: flow.most_recent_segment_time,
    });

    backfill_mac(&mut record.source_mac, flow);
    extend_unique(&mut record.relayed_addresses, &flow.relayed_addresses);
    extend_unique(&mut record.peer_addresses, &flow.peer_addresses);
    extend_unique(&mut record.mapped_addresses, &flow.mapped_addresses);
    extend_unique(&mut record.turn_usernames, &flow.turn_usernames);
    widen_window(&mut record.first_seen, &mut record.last_activity, flow);
}

fn upsert_discovery(table: &mut HashMap<L4Key, DiscoveryFlow>, flow: &StunFlow) {
    let record = table.entry(flow.session_key.clone()).or_insert_with(|| DiscoveryFlow {
        session_key: flow.session_key.clone(),
        transport: flow.transport,
        source_address: flow.source_address,
        source_mac: flow.source_mac.clone(),
        source_port: flow.source_port,
        destination_address: flow.destination_address,
        destination_port: flow.destination_port,
        mapped_addresses: Vec::new(),
        saw_success_response: false,
        saw_error_response: false,
        first_seen: flow.established_at,
        last_activity: flow.most_recent_segment_time,
    });

    record.saw_success_response |= flow.saw_success_response;
    record.saw_error_response |= flow.saw_error_response;

    backfill_mac(&mut record.source_mac, flow);
    extend_unique(&mut record.mapped_addresses, &flow.mapped_addresses);
    widen_window(&mut record.first_seen, &mut record.last_activity, flow);
}

fn backfill_mac(host_mac: &mut Option<String>, flow: &StunFlow) {
    if host_mac.is_none() && let Some(mac) = &flow.source_mac {
        *host_mac = Some(mac.clone());
    }
}

fn widen_window(first_seen: &mut DateTime<Utc>, last_activity: &mut DateTime<Utc>, flow: &StunFlow) {
    if flow.established_at < *first_seen {
        *first_seen = flow.established_at;
    }
    if flow.most_recent_segment_time > *last_activity {
        *last_activity = flow.most_recent_segment_time;
    }
}

fn extend_unique<T: PartialEq + Clone>(target: &mut Vec<T>, values: &[T]) {
    for value in values {
        if !target.contains(value) {
            target.push(value.clone());
        }
    }
}

fn canonical_ice_ufrag(ufrags: &[String]) -> Option<(String, String, String)> {
    for username in ufrags {
        if let Some((first, second)) = username.split_once(':') {
            if first.is_empty() || second.is_empty() {
                continue;
            }
            let (a, b) = if first <= second {
                (first.to_string(), second.to_string())
            } else {
                (second.to_string(), first.to_string())
            };
            return Some((format!("{a}|{b}"), a, b));
        }
    }
    None
}

fn has_bidirectional_ufrag(ufrags: &[String]) -> bool {
    for u in ufrags {
        if let Some((a, b)) = u.split_once(':') {
            if a.is_empty() || b.is_empty() { continue; }
            let reversed = format!("{b}:{a}");
            if ufrags.iter().any(|other| other == &reversed) {
                return true;
            }
        }
    }
    false
}