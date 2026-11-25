use std::collections::BTreeMap;
use chrono::{DateTime, Utc};
use serde::Serialize;
use crate::state::tables::ipv6_transition_table::Ipv6TransitionTunnelRecord;

#[derive(Serialize)]
pub struct Ipv6TransitionTunnelsReport {
    pub tunnels: Vec<Ipv6TransitionTunnelReport>
}

#[derive(Serialize)]
pub struct Ipv6TransitionTunnelReport {
    pub tunnel_type: String,
    pub transport: String,
    pub details: BTreeMap<String, String>,
    pub connection_status: String,
    pub tunneled_bytes: u64,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: String,
    pub source_port: Option<u16>,
    pub destination_address: String,
    pub destination_port: Option<u16>,
    pub established_at: DateTime<Utc>,
    pub terminated_at: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>
}

pub fn generate(tunnels: &[&Ipv6TransitionTunnelRecord]) -> Ipv6TransitionTunnelsReport {
    Ipv6TransitionTunnelsReport {
        tunnels: tunnels.iter().map(|tunnel| Ipv6TransitionTunnelReport {
            tunnel_type: tunnel.tunnel_type.to_string(),
            transport: tunnel.transport.to_string(),
            details: tunnel.details(),
            connection_status: tunnel.connection_status.to_string(),
            tunneled_bytes: tunnel.tunneled_bytes(),
            source_mac: tunnel.source_mac.clone(),
            destination_mac: tunnel.destination_mac.clone(),
            source_address: tunnel.source_address.to_string(),
            source_port: tunnel.source_port,
            destination_address: tunnel.destination_address.to_string(),
            destination_port: tunnel.destination_port,
            established_at: tunnel.established_at,
            terminated_at: tunnel.terminated_at,
            most_recent_segment_time: tunnel.most_recent_segment_time
        }).collect()
    }
}
