use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::{Arc, Mutex};
use chrono::{DateTime, Utc};
use log::error;
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::icmp_report;
use crate::metrics::Metrics;
use crate::state::tables::table_helpers::clear_mutex_vector;
use crate::wired::packets::IcmpPacket;

// Queries and replies are aggregated per key within a report period; error messages are kept per packet.
pub struct IcmpTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    metrics: Arc<Mutex<Metrics>>,
    summaries: Mutex<HashMap<IcmpSummaryKey, IcmpSummary>>,
    errors: Mutex<Vec<Arc<IcmpPacket>>>
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct IcmpSummaryKey {
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub icmp_type: u8,
    pub icmp_code: u8
}

#[derive(Debug, Clone)]
pub struct IcmpSummary {
    pub packet_count: u64,
    pub bytes_min: u32,
    pub bytes_max: u32,
    pub ip_ttl: u8,
    pub ip_tos: u8,
    pub ip_df: bool,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

impl IcmpTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        IcmpTable {
            leaderlink,
            metrics,
            summaries: Mutex::new(HashMap::new()),
            errors: Mutex::new(Vec::new())
        }
    }

    pub fn register_packet(&self, packet: Arc<IcmpPacket>) {
        if packet.is_error() {
            match self.errors.lock() {
                Ok(mut errors) => errors.push(packet),
                Err(e) => error!("Could not acquire ICMP errors mutex: {}", e)
            }

            return;
        }

        let key = IcmpSummaryKey {
            source_mac: packet.source_mac.clone(),
            destination_mac: packet.destination_mac.clone(),
            source_address: packet.source_address,
            destination_address: packet.destination_address,
            icmp_type: packet.icmp_type,
            icmp_code: packet.icmp_code
        };

        match self.summaries.lock() {
            Ok(mut summaries) => {
                match summaries.get_mut(&key) {
                    Some(summary) => {
                        summary.packet_count += 1;
                        summary.bytes_min = summary.bytes_min.min(packet.payload_size);
                        summary.bytes_max = summary.bytes_max.max(packet.payload_size);
                        summary.ip_ttl = packet.ip_ttl;
                        summary.ip_tos = packet.ip_tos;
                        summary.ip_df = summary.ip_df || packet.ip_df;
                        if packet.timestamp < summary.first_seen {
                            summary.first_seen = packet.timestamp;
                        }
                        if packet.timestamp > summary.last_seen {
                            summary.last_seen = packet.timestamp;
                        }
                    },
                    None => {
                        summaries.insert(key, IcmpSummary {
                            packet_count: 1,
                            bytes_min: packet.payload_size,
                            bytes_max: packet.payload_size,
                            ip_ttl: packet.ip_ttl,
                            ip_tos: packet.ip_tos,
                            ip_df: packet.ip_df,
                            first_seen: packet.timestamp,
                            last_seen: packet.timestamp
                        });
                    }
                }
            },
            Err(e) => error!("Could not acquire ICMP summaries mutex: {}", e)
        }
    }

    pub fn process_report(&self) {
        let mut timer = Timer::new();

        let summaries: Vec<(IcmpSummaryKey, IcmpSummary)> = match self.summaries.lock() {
            Ok(mut summaries) => summaries.drain().collect(),
            Err(e) => {
                error!("Could not acquire ICMP summaries mutex to build report: {}", e);
                return;
            }
        };

        let errors: Vec<Arc<IcmpPacket>> = match self.errors.lock() {
            Ok(errors) => errors.clone(),
            Err(e) => {
                error!("Could not acquire ICMP errors mutex to build report: {}", e);
                return;
            }
        };

        if summaries.is_empty() && errors.is_empty() {
            return;
        }

        let report = match serde_json::to_string(&icmp_report::generate(&summaries, &errors)) {
            Ok(report) => report,
            Err(e) => {
                error!("Could not serialize ICMP report: {}", e);
                return;
            }
        };
        timer.stop();
        record_timer(
            timer.elapsed_microseconds(),
            "tables.icmp.timer.report_generation",
            &self.metrics
        );

        match self.leaderlink.lock() {
            Ok(link) => {
                if let Err(e) = link.send_report("icmp/packets", report) {
                    error!("Could not submit ICMP report: {}", e);
                }
            },
            Err(e) => error!("Could not acquire leader link lock for ICMP report submission: {}", e)
        }

        clear_mutex_vector(&self.errors);
    }

    pub fn calculate_metrics(&self) {
        let summaries = match self.summaries.lock() {
            Ok(s) => s.len() as i128,
            Err(_) => -1
        };
        let errors = match self.errors.lock() {
            Ok(e) => e.len() as i128,
            Err(_) => -1
        };

        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("tables.icmp.summaries.size", summaries);
                metrics.set_gauge("tables.icmp.errors.size", errors);
            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

}
