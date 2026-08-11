use std::collections::HashMap;
use chrono::{DateTime, Utc};
use serde::ser::{Serializer, SerializeSeq, SerializeStruct};
use serde::Serialize;
use std::net::SocketAddr;
use crate::protocols::parsers::l4_key::L4Key;
use crate::protocols::parsers::stun_tagger::StunTransport;
use crate::state::tables::stun_table::{DiscoveryFlow, NegotiationFlow, TurnFlow};

fn serialize_socketaddrs<S>(addrs: &[SocketAddr], serializer: S) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    struct Split<'a>(&'a SocketAddr);

    impl Serialize for Split<'_> {
        fn serialize<S: Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
            let mut st = s.serialize_struct("SocketAddr", 2)?;
            st.serialize_field("address", &self.0.ip())?;
            st.serialize_field("port", &self.0.port())?;
            st.end()
        }
    }

    let mut seq = serializer.serialize_seq(Some(addrs.len()))?;
    for addr in addrs {
        seq.serialize_element(&Split(addr))?;
    }
    seq.end()
}

fn transport_str(transport: StunTransport) -> String {
    match transport {
        StunTransport::Tcp => "tcp".to_string(),
        StunTransport::Udp => "udp".to_string(),
    }
}

#[derive(Serialize)]
pub struct NatTraversalReport {
    pub negotiations: Vec<NegotiationFlowReport>,
    pub relays: Vec<RelayFlowReport>,
    pub discoveries: Vec<DiscoveryFlowReport>,
}

#[derive(Serialize)]
pub struct NegotiationFlowReport {
    pub host_address: String,
    pub host_mac: Option<String>,
    pub transport: String,
    pub source_port: u16,
    pub remote_address: String,
    pub remote_port: u16,
    pub ufrags: Vec<String>,
    pub is_turn: bool,
    pub turn_usernames: Vec<String>,
    #[serde(serialize_with = "serialize_socketaddrs")]
    pub mapped_addresses: Vec<SocketAddr>,
    #[serde(serialize_with = "serialize_socketaddrs")]
    pub relayed_addresses: Vec<SocketAddr>,
    #[serde(serialize_with = "serialize_socketaddrs")]
    pub peer_addresses: Vec<SocketAddr>,
    pub first_seen: DateTime<Utc>,
    pub last_activity: DateTime<Utc>,
}

#[derive(Serialize)]
pub struct RelayFlowReport {
    pub host_address: String,
    pub host_mac: Option<String>,
    pub transport: String,
    pub source_port: u16,
    pub server_address: String,
    pub server_port: u16,
    #[serde(serialize_with = "serialize_socketaddrs")]
    pub relayed_addresses: Vec<SocketAddr>,
    #[serde(serialize_with = "serialize_socketaddrs")]
    pub peer_addresses: Vec<SocketAddr>,
    #[serde(serialize_with = "serialize_socketaddrs")]
    pub mapped_addresses: Vec<SocketAddr>,
    pub turn_usernames: Vec<String>,
    pub first_seen: DateTime<Utc>,
    pub last_activity: DateTime<Utc>,
}

#[derive(Serialize)]
pub struct DiscoveryFlowReport {
    pub transport: String,
    pub source_mac: Option<String>,
    pub source_address: String,
    pub source_port: u16,
    pub destination_address: String,
    pub destination_port: u16,
    #[serde(serialize_with = "serialize_socketaddrs")]
    pub mapped_addresses: Vec<SocketAddr>,
    pub saw_success_response: bool,
    pub saw_error_response: bool,
    pub first_seen: DateTime<Utc>,
    pub last_activity: DateTime<Utc>,
}

pub fn generate(
    negotiations: &HashMap<L4Key, NegotiationFlow>,
    turn_activity: &HashMap<L4Key, TurnFlow>,
    discoveries: &HashMap<L4Key, DiscoveryFlow>,
) -> NatTraversalReport {
    NatTraversalReport {
        negotiations: negotiations.values().map(negotiation_to_report).collect(),
        relays: turn_activity.values().map(relay_to_report).collect(),
        discoveries: discoveries.values().map(discovery_to_report).collect(),
    }
}

fn negotiation_to_report(n: &NegotiationFlow) -> NegotiationFlowReport {
    NegotiationFlowReport {
        host_address: n.host_address.to_string(),
        host_mac: n.host_mac.clone(),
        transport: transport_str(n.transport),
        source_port: n.source_port,
        remote_address: n.destination_address.to_string(),
        remote_port: n.destination_port,
        ufrags: n.ufrags.clone(),
        is_turn: n.is_turn,
        turn_usernames: n.turn_usernames.clone(),
        mapped_addresses: n.mapped_addresses.clone(),
        relayed_addresses: n.relayed_addresses.clone(),
        peer_addresses: n.peer_addresses.clone(),
        first_seen: n.first_seen,
        last_activity: n.last_activity,
    }
}

fn relay_to_report(t: &TurnFlow) -> RelayFlowReport {
    RelayFlowReport {
        host_address: t.host_address.to_string(),
        host_mac: t.host_mac.clone(),
        transport: transport_str(t.transport),
        source_port: t.source_port,
        server_address: t.destination_address.to_string(),
        server_port: t.destination_port,
        relayed_addresses: t.relayed_addresses.clone(),
        peer_addresses: t.peer_addresses.clone(),
        mapped_addresses: t.mapped_addresses.clone(),
        turn_usernames: t.turn_usernames.clone(),
        first_seen: t.first_seen,
        last_activity: t.last_activity,
    }
}

fn discovery_to_report(d: &DiscoveryFlow) -> DiscoveryFlowReport {
    DiscoveryFlowReport {
        transport: transport_str(d.transport),
        source_mac: d.source_mac.clone(),
        source_address: d.source_address.to_string(),
        source_port: d.source_port,
        destination_address: d.destination_address.to_string(),
        destination_port: d.destination_port,
        mapped_addresses: d.mapped_addresses.clone(),
        saw_error_response: d.saw_error_response,
        saw_success_response: d.saw_success_response,
        first_seen: d.first_seen,
        last_activity: d.last_activity,
    }
}