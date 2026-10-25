use std::sync::Arc;
use chrono::{DateTime, Utc};
use serde::Serialize;
use crate::state::tables::icmp_table::{IcmpSummary, IcmpSummaryKey};
use crate::wired::packets::IcmpPacket;

#[derive(Serialize)]
pub struct IcmpReport {
    pub summaries: Vec<IcmpSummaryReport>,
    pub errors: Vec<IcmpErrorReport>
}

#[derive(Serialize)]
pub struct IcmpSummaryReport {
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: String,
    pub destination_address: String,
    pub icmp_type: u8,
    pub icmp_code: u8,
    pub packet_count: u64,
    pub bytes_min: u32,
    pub bytes_max: u32,
    pub ip_ttl: u8,
    pub ip_tos: u8,
    pub ip_df: bool,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

#[derive(Serialize)]
pub struct IcmpErrorReport {
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: String,
    pub destination_address: String,
    pub icmp_type: u8,
    pub icmp_code: u8,
    pub ip_ttl: u8,
    pub redirect_gateway: Option<String>,
    pub quoted_protocol: Option<u8>,
    pub quoted_source_address: Option<String>,
    pub quoted_destination_address: Option<String>,
    pub quoted_source_port: Option<u16>,
    pub quoted_destination_port: Option<u16>,
    pub quoted_ttl: Option<u8>,
    pub timestamp: DateTime<Utc>
}

pub fn generate(summaries: &[(IcmpSummaryKey, IcmpSummary)], errors: &[Arc<IcmpPacket>]) -> IcmpReport {
    let summaries = summaries.iter().map(|(key, summary)| IcmpSummaryReport {
        source_mac: key.source_mac.clone(),
        destination_mac: key.destination_mac.clone(),
        source_address: key.source_address.to_string(),
        destination_address: key.destination_address.to_string(),
        icmp_type: key.icmp_type,
        icmp_code: key.icmp_code,
        packet_count: summary.packet_count,
        bytes_min: summary.bytes_min,
        bytes_max: summary.bytes_max,
        ip_ttl: summary.ip_ttl,
        ip_tos: summary.ip_tos,
        ip_df: summary.ip_df,
        first_seen: summary.first_seen,
        last_seen: summary.last_seen
    }).collect();

    let errors = errors.iter().map(|p| IcmpErrorReport {
        source_mac: p.source_mac.clone(),
        destination_mac: p.destination_mac.clone(),
        source_address: p.source_address.to_string(),
        destination_address: p.destination_address.to_string(),
        icmp_type: p.icmp_type,
        icmp_code: p.icmp_code,
        ip_ttl: p.ip_ttl,
        redirect_gateway: p.redirect_gateway.map(|g| g.to_string()),
        quoted_protocol: p.quoted.as_ref().map(|q| q.protocol),
        quoted_source_address: p.quoted.as_ref().map(|q| q.source_address.to_string()),
        quoted_destination_address: p.quoted.as_ref().map(|q| q.destination_address.to_string()),
        quoted_source_port: p.quoted.as_ref().and_then(|q| q.source_port),
        quoted_destination_port: p.quoted.as_ref().and_then(|q| q.destination_port),
        quoted_ttl: p.quoted.as_ref().map(|q| q.ttl),
        timestamp: p.timestamp
    }).collect();

    IcmpReport { summaries, errors }
}
