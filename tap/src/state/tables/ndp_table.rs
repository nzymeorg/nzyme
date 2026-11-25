use std::collections::HashMap;
use std::hash::Hash;
use std::net::IpAddr;
use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicU64, Ordering};
use chrono::{DateTime, Utc};
use log::error;
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::ndp_report;
use crate::metrics::Metrics;
use crate::wired::packets::{NdpMessageType, NdpPacket, NdpRouterAdvertisement};

const MAX_MESSAGES: usize = 20_000;
const MAX_ROUTER_ADVERTISEMENTS: usize = 1_000;

pub struct NdpTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    metrics: Arc<Mutex<Metrics>>,
    messages: Mutex<HashMap<NdpMessageKey, NdpSummary>>,
    router_advertisements: Mutex<HashMap<NdpRouterAdvertisementKey, NdpSummary>>,
    dropped: AtomicU64
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct NdpMessageKey {
    pub message_type: NdpMessageType,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub hop_limit: u8,
    pub target_address: Option<IpAddr>,
    pub source_link_layer_address: Option<String>,
    pub target_link_layer_address: Option<String>,
    pub router: Option<bool>,
    pub solicited: Option<bool>,
    pub overrides: Option<bool>
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct NdpRouterAdvertisementKey {
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub hop_limit: u8,
    pub source_link_layer_address: Option<String>,
    pub advertisement: NdpRouterAdvertisement
}

#[derive(Debug, Clone)]
pub struct NdpSummary {
    pub packet_count: u64,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

impl NdpTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        NdpTable {
            leaderlink,
            metrics,
            messages: Mutex::new(HashMap::new()),
            router_advertisements: Mutex::new(HashMap::new()),
            dropped: AtomicU64::new(0)
        }
    }

    pub fn register_packet(&self, packet: &Arc<NdpPacket>) {
        let recorded = match &packet.router_advertisement {
            Some(advertisement) => {
                let key = NdpRouterAdvertisementKey {
                    source_mac: packet.source_mac.clone(),
                    destination_mac: packet.destination_mac.clone(),
                    source_address: packet.source_address,
                    destination_address: packet.destination_address,
                    hop_limit: packet.hop_limit,
                    source_link_layer_address: packet.source_link_layer_address.clone(),
                    advertisement: advertisement.clone()
                };

                Self::count(&self.router_advertisements, key, packet.timestamp, MAX_ROUTER_ADVERTISEMENTS)
            },
            None => {
                let key = NdpMessageKey {
                    message_type: packet.message_type,
                    source_mac: packet.source_mac.clone(),
                    destination_mac: packet.destination_mac.clone(),
                    source_address: packet.source_address,
                    destination_address: packet.destination_address,
                    hop_limit: packet.hop_limit,
                    target_address: packet.target_address,
                    source_link_layer_address: packet.source_link_layer_address.clone(),
                    target_link_layer_address: packet.target_link_layer_address.clone(),
                    router: packet.router,
                    solicited: packet.solicited,
                    overrides: packet.overrides
                };

                Self::count(&self.messages, key, packet.timestamp, MAX_MESSAGES)
            }
        };

        if !recorded {
            self.dropped.fetch_add(1, Ordering::Relaxed);
        }
    }

    fn count<K: Eq + Hash>(table: &Mutex<HashMap<K, NdpSummary>>,
                           key: K,
                           timestamp: DateTime<Utc>,
                           limit: usize) -> bool {
        match table.lock() {
            Ok(mut table) => {
                if let Some(summary) = table.get_mut(&key) {
                    summary.packet_count += 1;
                    if timestamp < summary.first_seen {
                        summary.first_seen = timestamp;
                    }
                    if timestamp > summary.last_seen {
                        summary.last_seen = timestamp;
                    }

                    return true;
                }

                if table.len() >= limit {
                    return false;
                }

                table.insert(key, NdpSummary { packet_count: 1, first_seen: timestamp, last_seen: timestamp });

                true
            },
            Err(e) => {
                error!("Could not acquire NDP table mutex: {}", e);
                false
            }
        }
    }

    pub fn process_report(&self) {
        let mut timer = Timer::new();

        let messages: Vec<(NdpMessageKey, NdpSummary)> = match self.messages.lock() {
            Ok(mut messages) => messages.drain().collect(),
            Err(e) => {
                error!("Could not acquire NDP messages mutex to build report: {}", e);
                return;
            }
        };

        let router_advertisements: Vec<(NdpRouterAdvertisementKey, NdpSummary)> =
            match self.router_advertisements.lock() {
                Ok(mut router_advertisements) => router_advertisements.drain().collect(),
                Err(e) => {
                    error!("Could not acquire NDP router advertisements mutex to build report: {}", e);
                    return;
                }
            };

        if messages.is_empty() && router_advertisements.is_empty() {
            return;
        }

        let report = match serde_json::to_string(&ndp_report::generate(&messages, &router_advertisements)) {
            Ok(report) => report,
            Err(e) => {
                error!("Could not serialize NDP report: {}", e);
                return;
            }
        };
        timer.stop();
        record_timer(
            timer.elapsed_microseconds(),
            "tables.ndp.timer.report_generation",
            &self.metrics
        );

        match self.leaderlink.lock() {
            Ok(link) => {
                if let Err(e) = link.send_report("ndp/messages", report) {
                    error!("Could not submit NDP report: {}", e);
                }
            },
            Err(e) => error!("Could not acquire leader link lock for NDP report submission: {}", e)
        }
    }

    pub fn calculate_metrics(&self) {
        let messages_size: i128 = match self.messages.lock() {
            Ok(messages) => messages.len() as i128,
            Err(e) => {
                error!("Could not acquire mutex to calculate NDP messages table size: {}", e);
                -1
            }
        };

        let router_advertisements_size: i128 = match self.router_advertisements.lock() {
            Ok(router_advertisements) => router_advertisements.len() as i128,
            Err(e) => {
                error!("Could not acquire mutex to calculate NDP router advertisements table size: {}", e);
                -1
            }
        };

        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("tables.ndp.messages.size", messages_size);
                metrics.set_gauge("tables.ndp.router_advertisements.size", router_advertisements_size);
                metrics.set_gauge("tables.ndp.dropped", self.dropped.load(Ordering::Relaxed) as i128);
            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

}
