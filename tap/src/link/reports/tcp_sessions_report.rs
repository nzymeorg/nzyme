use std::collections::{HashMap, HashSet};
use std::sync::MutexGuard;
use chrono::{DateTime, Utc};
use serde::Serialize;
use crate::state::tables::tcp_table::{TcpSession, TcpSynAck};
use crate::protocols::parsers::l4_key::L4Key;

#[derive(Serialize)]
pub struct TcpSessionsReport {
    pub sessions: Vec<TcpSessionReport>
}

#[derive(Serialize)]
pub struct TcpSessionReport {
    pub state: String,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: String,
    pub source_port: u16,
    pub destination_address: String,
    pub destination_port: u16,
    pub start_time: DateTime<Utc>,
    pub end_time: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>,
    pub segments_count: u64,
    pub segments_count_incremental: u64,
    pub bytes_count_rx: u64,
    pub bytes_count_tx: u64,
    pub bytes_count_rx_incremental: u64,
    pub bytes_count_tx_incremental: u64,
    pub syn_ip_ttl: u8,
    pub syn_ip_tos: u8,
    pub syn_ip_df: bool,
    pub syn_cwr: bool,
    pub syn_ece: bool,
    pub syn_window_size: u16,
    pub syn_maximum_segment_size: Option<u16>,
    pub syn_window_scale_multiplier: Option<u8>,
    pub syn_options: Vec<u8>,
    pub syn_options_truncated: bool,
    pub syn_ip_id: u16,
    pub syn_timestamp_value: Option<u32>,
    pub syn_timestamp_echo: Option<u32>,
    pub latest_client_timestamp_value: Option<u32>,
    pub latest_client_timestamp_time: Option<DateTime<Utc>>,
    pub latest_client_ip_id: Option<u16>,
    pub segments_count_client_to_server: u64,
    pub syn_ack: Option<TcpSynAckReport>,
    pub tags: HashSet<String>
}

#[derive(Serialize)]
pub struct TcpSynAckReport {
    pub ip_ttl: u8,
    pub ip_tos: u8,
    pub ip_df: bool,
    pub ip_id: u16,
    pub cwr: bool,
    pub ece: bool,
    pub window_size: u16,
    pub maximum_segment_size: Option<u16>,
    pub window_scale_multiplier: Option<u8>,
    pub options: Vec<u8>,
    pub options_truncated: bool,
    pub timestamp_value: Option<u32>,
    pub timestamp_echo: Option<u32>
}

impl TcpSynAckReport {
    fn from(s: &TcpSynAck) -> Self {
        TcpSynAckReport {
            ip_ttl: s.ip_ttl,
            ip_tos: s.ip_tos,
            ip_df: s.ip_df,
            ip_id: s.ip_id,
            cwr: s.cwr,
            ece: s.ece,
            window_size: s.window_size,
            maximum_segment_size: s.maximum_segment_size,
            window_scale_multiplier: s.window_scale_multiplier,
            options: s.options.clone(),
            options_truncated: s.options_truncated,
            timestamp_value: s.timestamp_value,
            timestamp_echo: s.timestamp_echo
        }
    }
}

pub fn generate(s: &MutexGuard<HashMap<L4Key, TcpSession>>) -> TcpSessionsReport {
    let mut sessions: Vec<TcpSessionReport> = Vec::new();

    for session in s.values() {
        sessions.push(TcpSessionReport {
            state: session.state.to_string(),
            source_mac: session.source_mac.clone(),
            destination_mac: session.destination_mac.clone(),
            source_address: session.source_address.to_string(),
            source_port: session.source_port,
            destination_address: session.destination_address.to_string(),
            destination_port: session.destination_port,
            start_time: session.start_time,
            end_time: session.end_time,
            most_recent_segment_time: session.most_recent_segment_time,
            segments_count: session.segments_count,
            segments_count_incremental: session.segments_count_incremental,
            bytes_count_rx: session.bytes_count_rx,
            bytes_count_tx: session.bytes_count_tx,
            bytes_count_rx_incremental: session.bytes_count_rx_incremental,
            bytes_count_tx_incremental: session.bytes_count_tx_incremental,
            syn_ip_ttl: session.syn_ip_ttl,
            syn_ip_tos: session.syn_ip_tos,
            syn_ip_df: session.syn_ip_df,
            syn_cwr: session.syn_cwr,
            syn_ece: session.syn_ece,
            syn_window_size: session.syn_window_size,
            syn_maximum_segment_size: session.syn_maximum_segment_size,
            syn_window_scale_multiplier: session.syn_window_scale_multiplier,
            syn_options: session.syn_options.clone(),
            syn_options_truncated: session.syn_options_truncated,
            syn_ip_id: session.syn_ip_id,
            syn_timestamp_value: session.syn_timestamp_value,
            syn_timestamp_echo: session.syn_timestamp_echo,
            latest_client_timestamp_value: session.latest_client_timestamp_value,
            latest_client_timestamp_time: session.latest_client_timestamp_time,
            latest_client_ip_id: session.latest_client_ip_id,
            segments_count_client_to_server: session.segments_count_client_to_server,
            syn_ack: session.syn_ack.as_ref().map(TcpSynAckReport::from),
            tags: session.tags.clone().into_iter()
                        .map(|t| t.to_string())
                        .collect()
        })
    }

    TcpSessionsReport {
        sessions
    }
}