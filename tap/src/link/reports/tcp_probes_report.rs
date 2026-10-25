use chrono::{DateTime, Utc};
use serde::Serialize;
use crate::state::tables::tcp_table::{TcpProbe, TcpProbeKey, TcpProbeReply, TcpProbeTriple};

/*
 * Stateless TCP probes: Segments that belong to no session and have a shape no real stack sends. See
 * `is_probe_segment` in the TCP table.
 */
#[derive(Serialize)]
pub struct TcpProbesReport {
    pub probes: Vec<TcpProbeReport>,
    pub replies: Vec<TcpProbeReplyReport>
}

#[derive(Serialize)]
pub struct TcpProbeReplyReport {
    pub source_address: String,
    pub destination_address: String,
    pub destination_port: u16,
    pub packet_count: u64,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

#[derive(Serialize)]
pub struct TcpProbeReport {
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: String,
    pub destination_address: String,
    pub destination_port: u16,
    pub flag_syn: bool,
    pub flag_ack: bool,
    pub flag_fin: bool,
    pub flag_rst: bool,
    pub flag_psh: bool,
    pub flag_urg: bool,
    pub flag_ece: bool,
    pub flag_cwr: bool,
    pub window_size: u16,
    pub maximum_segment_size: Option<u16>,
    pub window_scale_multiplier: Option<u8>,
    pub options: Vec<u8>,
    pub ip_ttl: u8,
    pub ip_tos: u8,
    pub ip_df: bool,
    pub packet_count: u64,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

pub fn generate(p: &[(TcpProbeKey, TcpProbe)], r: &[(TcpProbeTriple, TcpProbeReply)]) -> TcpProbesReport {
    let mut probes: Vec<TcpProbeReport> = Vec::with_capacity(p.len());
    let mut replies: Vec<TcpProbeReplyReport> = Vec::with_capacity(r.len());

    for (triple, reply) in r {
        replies.push(TcpProbeReplyReport {
            source_address: triple.source_address.to_string(),
            destination_address: triple.destination_address.to_string(),
            destination_port: triple.destination_port,
            packet_count: reply.packet_count,
            first_seen: reply.first_seen,
            last_seen: reply.last_seen
        });
    }

    for (key, probe) in p {
        probes.push(TcpProbeReport {
            source_mac: key.source_mac.clone(),
            destination_mac: key.destination_mac.clone(),
            source_address: key.source_address.to_string(),
            destination_address: key.destination_address.to_string(),
            destination_port: key.destination_port,
            flag_syn: key.flag_syn,
            flag_ack: key.flag_ack,
            flag_fin: key.flag_fin,
            flag_rst: key.flag_rst,
            flag_psh: key.flag_psh,
            flag_urg: key.flag_urg,
            flag_ece: key.flag_ece,
            flag_cwr: key.flag_cwr,
            window_size: key.window_size,
            maximum_segment_size: key.maximum_segment_size,
            window_scale_multiplier: key.window_scale_multiplier,
            options: key.options.clone(),
            ip_ttl: key.ip_ttl,
            ip_tos: key.ip_tos,
            ip_df: key.ip_df,
            packet_count: probe.packet_count,
            first_seen: probe.first_seen,
            last_seen: probe.last_seen
        });
    }

    TcpProbesReport { probes, replies }
}
