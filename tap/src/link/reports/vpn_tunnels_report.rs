use std::collections::{BTreeMap, HashMap};
use std::sync::MutexGuard;
use chrono::{DateTime, Utc};
use serde::Serialize;
use crate::protocols::parsers::l4_key::L4Key;
use crate::state::tables::vpn_table::VpnTunnelRecord;

#[derive(Serialize)]
pub struct VpnTunnelsReport {
    pub tunnels: Vec<VpnTunnelReport>
}

#[derive(Serialize)]
pub struct VpnTunnelReport {
    pub vpn_type: String,
    pub transport: String,
    pub handshake_status: String,
    pub message_types: Vec<String>,
    pub provider: Option<String>,
    pub server_name: Option<String>,
    pub details: BTreeMap<String, String>,
    pub connection_status: String,
    pub tunneled_bytes: u64,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: String,
    pub source_port: u16,
    pub destination_address: String,
    pub destination_port: u16,
    pub established_at: DateTime<Utc>,
    pub terminated_at: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>
}

pub fn generate(t: &MutexGuard<HashMap<L4Key, VpnTunnelRecord>>) -> VpnTunnelsReport {
    let mut tunnels: Vec<VpnTunnelReport> = Vec::new();

    for tunnel in t.values() {
        tunnels.push(VpnTunnelReport {
            vpn_type: tunnel.vpn_type.to_string(),
            transport: tunnel.transport.to_string(),
            handshake_status: tunnel.handshake_status.to_string(),
            message_types: tunnel.message_types.clone(),
            provider: tunnel.provider.clone(),
            server_name: tunnel.server_name.clone(),
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
        })
    }

    VpnTunnelsReport { tunnels }
}
