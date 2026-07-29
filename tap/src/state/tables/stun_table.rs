use std::collections::HashMap;
use std::net::{IpAddr, SocketAddr};
use std::sync::{Arc, Mutex};
use chrono::{DateTime, Duration, Utc};
use log::{error, info};
use crate::link::leaderlink::Leaderlink;
use crate::metrics::Metrics;
use crate::protocols::parsers::l4_key::L4Key;
use crate::protocols::parsers::stun_tagger::StunFlow;

const STALE_AFTER_MINUTES: i64 = 1;

pub struct StunTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    metrics: Arc<Mutex<Metrics>>,
    negotiations: Mutex<HashMap<NegotiationKey, IceNegotiation>>,
    turn_activity: Mutex<HashMap<IpAddr, TurnActivity>>,
    discoveries: Mutex<HashMap<IpAddr, StunDiscovery>>,
}

/// How the observed public port relates to the internal source port.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NatMapping {
    /// No mapped address observed yet. NAT behavior unknown.
    Unknown,
    /// Every observed mapping preserved the source port. Direct traversal generally works.
    Preserved,
    /// At least one mapping used a different public port than the source (symmetric NAT). Likely
    /// to be forced onto a relay.
    Varies,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct NegotiationKey {
    pub host_address: IpAddr,
    pub ufrag_pair: String,
}

#[derive(Debug, Clone)]
pub struct IceNegotiation {
    pub host_address: IpAddr,
    pub host_mac: Option<String>,
    pub ufrag_pair: String,
    pub ufrag_a: String,
    pub ufrag_b: String,
    pub member_flows: Vec<L4Key>,
    pub is_turn: bool,
    pub turn_usernames: Vec<String>,
    pub mapped_addresses: Vec<SocketAddr>,
    pub relayed_addresses: Vec<SocketAddr>,
    pub peer_addresses: Vec<SocketAddr>,
    pub probed_remotes: Vec<SocketAddr>,
    pub first_seen: DateTime<Utc>,
    pub last_activity: DateTime<Utc>,
}

#[derive(Debug, Clone)]
pub struct TurnActivity {
    pub host_address: IpAddr,
    pub host_mac: Option<String>,
    pub server_addresses: Vec<SocketAddr>,
    pub relayed_addresses: Vec<SocketAddr>,
    pub peer_addresses: Vec<SocketAddr>,
    pub mapped_addresses: Vec<SocketAddr>,
    pub turn_usernames: Vec<String>,
    pub member_flows: Vec<L4Key>,
    pub first_seen: DateTime<Utc>,
    pub last_activity: DateTime<Utc>,
}

#[derive(Debug, Clone)]
pub struct StunDiscovery {
    pub host_address: IpAddr,
    pub host_mac: Option<String>,
    pub server_addresses: Vec<SocketAddr>,
    pub public_addresses: Vec<IpAddr>,
    pub nat_mapping: NatMapping,
    pub flow_count: u64,
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

        if canonical_ice_ufrag(&flow.ufrags).is_some() {
            match self.negotiations.lock() {
                Ok(mut negotiations) => upsert_negotiation(&mut negotiations, &flow),
                Err(e) => error!("Could not acquire STUN negotiations table mutex: {}", e),
            }
        } else if flow.is_turn {
            match self.turn_activity.lock() {
                Ok(mut turn_activity) => upsert_turn_activity(&mut turn_activity, &flow),
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

        match self.negotiations.lock() {
            Ok(mut negotiations) => {
                info!("negotiations: {:?}", negotiations);
                negotiations.retain(|_, n| n.last_activity >= cutoff);
            }
            Err(e) => error!("Could not acquire STUN negotiations table mutex: {}", e),
        }

        match self.turn_activity.lock() {
            Ok(mut turn_activity) => {
                info!("turn_activity: {:?}", turn_activity);
                turn_activity.retain(|_, t| t.last_activity >= cutoff);
            }
            Err(e) => error!("Could not acquire STUN TURN-activity table mutex: {}", e),
        }

        match self.discoveries.lock() {
            Ok(mut discoveries) => {
                info!("discoveries: {:?}", discoveries);
                discoveries.retain(|_, d| d.last_activity >= cutoff);
            }
            Err(e) => error!("Could not acquire STUN discoveries table mutex: {}", e),
        }
    }

    pub fn calculate_metrics(&self) {
        // TODO
    }

}

fn upsert_negotiation(table: &mut HashMap<NegotiationKey, IceNegotiation>, flow: &StunFlow) {
    let host_address = flow.source_address;

    let Some((ufrag_pair, ufrag_a, ufrag_b)) = canonical_ice_ufrag(&flow.ufrags) else {
        return;
    };

    let key = NegotiationKey {
        host_address,
        ufrag_pair: ufrag_pair.clone(),
    };

    let negotiation = table.entry(key).or_insert_with(|| IceNegotiation {
        host_address,
        host_mac: flow.source_mac.clone(),
        ufrag_pair,
        ufrag_a,
        ufrag_b,
        member_flows: Vec::new(),
        is_turn: false,
        turn_usernames: Vec::new(),
        mapped_addresses: Vec::new(),
        relayed_addresses: Vec::new(),
        peer_addresses: Vec::new(),
        probed_remotes: Vec::new(),
        first_seen: flow.established_at,
        last_activity: flow.most_recent_segment_time,
    });

    backfill_mac(&mut negotiation.host_mac, flow);

    push_unique(&mut negotiation.member_flows, flow.session_key.clone());
    negotiation.is_turn |= flow.is_turn;

    extend_unique(&mut negotiation.turn_usernames, &flow.turn_usernames);
    extend_unique(&mut negotiation.mapped_addresses, &flow.mapped_addresses);
    extend_unique(&mut negotiation.relayed_addresses, &flow.relayed_addresses);
    extend_unique(&mut negotiation.peer_addresses, &flow.peer_addresses);

    push_unique(
        &mut negotiation.probed_remotes,
        SocketAddr::new(flow.destination_address, flow.destination_port),
    );

    widen_window(&mut negotiation.first_seen, &mut negotiation.last_activity, flow);
}

fn upsert_turn_activity(table: &mut HashMap<IpAddr, TurnActivity>, flow: &StunFlow) {
    let host_address = flow.source_address;

    let activity = table.entry(host_address).or_insert_with(|| TurnActivity {
        host_address,
        host_mac: flow.source_mac.clone(),
        server_addresses: Vec::new(),
        relayed_addresses: Vec::new(),
        peer_addresses: Vec::new(),
        mapped_addresses: Vec::new(),
        turn_usernames: Vec::new(),
        member_flows: Vec::new(),
        first_seen: flow.established_at,
        last_activity: flow.most_recent_segment_time,
    });

    backfill_mac(&mut activity.host_mac, flow);

    push_unique(&mut activity.member_flows, flow.session_key.clone());
    push_unique(
        &mut activity.server_addresses,
        SocketAddr::new(flow.destination_address, flow.destination_port),
    );

    extend_unique(&mut activity.relayed_addresses, &flow.relayed_addresses);
    extend_unique(&mut activity.peer_addresses, &flow.peer_addresses);
    extend_unique(&mut activity.mapped_addresses, &flow.mapped_addresses);
    extend_unique(&mut activity.turn_usernames, &flow.turn_usernames);

    widen_window(&mut activity.first_seen, &mut activity.last_activity, flow);
}

fn upsert_discovery(table: &mut HashMap<IpAddr, StunDiscovery>, flow: &StunFlow) {
    let host_address = flow.source_address;

    let discovery = table.entry(host_address).or_insert_with(|| StunDiscovery {
        host_address,
        host_mac: flow.source_mac.clone(),
        server_addresses: Vec::new(),
        public_addresses: Vec::new(),
        nat_mapping: NatMapping::Unknown,
        flow_count: 0,
        first_seen: flow.established_at,
        last_activity: flow.most_recent_segment_time,
    });

    backfill_mac(&mut discovery.host_mac, flow);

    push_unique(
        &mut discovery.server_addresses,
        SocketAddr::new(flow.destination_address, flow.destination_port),
    );

    for mapped in &flow.mapped_addresses {
        push_unique(&mut discovery.public_addresses, mapped.ip());
    }
    discovery.nat_mapping = update_nat_mapping(discovery.nat_mapping, flow);

    discovery.flow_count += 1;

    widen_window(&mut discovery.first_seen, &mut discovery.last_activity, flow);
}

fn update_nat_mapping(current: NatMapping, flow: &StunFlow) -> NatMapping {
    if current == NatMapping::Varies {
        return NatMapping::Varies;
    }

    let mut result = current;
    for mapped in &flow.mapped_addresses {
        if mapped.port() == flow.source_port {
            if result == NatMapping::Unknown {
                result = NatMapping::Preserved;
            }
        } else {
            return NatMapping::Varies;
        }
    }

    result
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

fn push_unique<T: PartialEq>(target: &mut Vec<T>, value: T) {
    if !target.contains(&value) {
        target.push(value);
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