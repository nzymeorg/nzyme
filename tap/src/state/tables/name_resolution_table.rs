use std::collections::{HashMap, HashSet};
use std::net::IpAddr;
use std::sync::{Arc, Mutex};
use chrono::{DateTime, Duration, Utc};
use log::error;
use crate::helpers::network::is_mac_address_multicast;
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::name_resolution_responses_report;
use crate::metrics::Metrics;
use crate::state::tables::table_helpers::clear_mutex_hashmap;
use crate::wired::packets::NameResolutionPacket;
use crate::wired::types::NameResolutionProtocol;

// A multicast answer is attributed to the clients that asked for the name this many seconds before it.
const QUERY_ATTRIBUTION_SECONDS: i64 = 10;

#[derive(Debug, Clone, Eq, PartialEq, Hash)]
pub struct ResponseKey {
    pub protocol: NameResolutionProtocol,
    pub responder_mac: String,
    pub responder_address: IpAddr,
    pub name: String
}

#[derive(Debug)]
pub struct ResponseAggregate {
    pub answer_addresses: HashSet<IpAddr>,
    pub response_count: u64,
    pub unicast_count: u64,
    pub victims: HashMap<String, VictimAggregate>,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

#[derive(Debug)]
pub struct VictimAggregate {
    pub address: Option<IpAddr>,
    pub count: u64,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

#[derive(Debug, Clone)]
struct RecentQuery {
    client_mac: String,
    client_address: IpAddr,
    timestamp: DateTime<Utc>
}

pub struct NameResolutionTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    metrics: Arc<Mutex<Metrics>>,
    responses: Mutex<HashMap<ResponseKey, ResponseAggregate>>,
    recent_queries: Mutex<HashMap<(NameResolutionProtocol, String), Vec<RecentQuery>>>
}

impl NameResolutionTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        NameResolutionTable {
            leaderlink,
            metrics,
            responses: Mutex::new(HashMap::new()),
            recent_queries: Mutex::new(HashMap::new())
        }
    }

    pub fn register(&self, packet: &NameResolutionPacket) {
        if packet.is_response {
            self.register_response(packet);
        } else {
            self.register_query(packet);
        }
    }

    fn register_query(&self, packet: &NameResolutionPacket) {
        let Some(client_mac) = &packet.source_mac else {
            return;
        };

        match self.recent_queries.lock() {
            Ok(mut queries) => {
                let cutoff = packet.timestamp - Duration::seconds(QUERY_ATTRIBUTION_SECONDS);
                for name in &packet.queries {
                    let entry = queries.entry((packet.protocol.clone(), name.clone())).or_default();
                    entry.retain(|q| q.timestamp >= cutoff);
                    entry.push(RecentQuery {
                        client_mac: client_mac.clone(),
                        client_address: packet.source_address,
                        timestamp: packet.timestamp
                    });
                }
            },
            Err(e) => error!("Could not acquire name resolution queries mutex: {}", e)
        }
    }

    fn register_response(&self, packet: &NameResolutionPacket) {
        let Some(responder_mac) = &packet.source_mac else {
            return;
        };

        if packet.answers.is_empty() {
            return;
        }

        let unicast = packet.destination_mac.as_ref().is_some_and(|mac| !is_mac_address_multicast(mac));

        // A packet with an A and an AAAA record for the same name is one answer for that name.
        let mut by_name: HashMap<String, Vec<IpAddr>> = HashMap::new();
        for a in &packet.answers {
            by_name.entry(a.name.clone()).or_default().push(a.address);
        }

        let queriers: HashMap<String, Vec<RecentQuery>> = if unicast {
            HashMap::new()
        } else {
            match self.recent_queries.lock() {
                Ok(queries) => {
                    let cutoff = packet.timestamp - Duration::seconds(QUERY_ATTRIBUTION_SECONDS);
                    by_name.keys().map(|name| {
                        let recent = queries.get(&(packet.protocol.clone(), name.clone()))
                            .map(|qs| qs.iter().filter(|q| q.timestamp >= cutoff).cloned().collect())
                            .unwrap_or_default();
                        (name.clone(), recent)
                    }).collect()
                },
                Err(e) => {
                    error!("Could not acquire name resolution queries mutex: {}", e);
                    HashMap::new()
                }
            }
        };

        match self.responses.lock() {
            Ok(mut responses) => {
                for (name, addresses) in by_name {
                    let key = ResponseKey {
                        protocol: packet.protocol.clone(),
                        responder_mac: responder_mac.clone(),
                        responder_address: packet.source_address,
                        name: name.clone()
                    };

                    let agg = responses.entry(key).or_insert_with(|| ResponseAggregate {
                        answer_addresses: HashSet::new(),
                        response_count: 0,
                        unicast_count: 0,
                        victims: HashMap::new(),
                        first_seen: packet.timestamp,
                        last_seen: packet.timestamp
                    });

                    agg.answer_addresses.extend(addresses);
                    agg.response_count += 1;
                    agg.first_seen = agg.first_seen.min(packet.timestamp);
                    agg.last_seen = agg.last_seen.max(packet.timestamp);

                    if unicast {
                        agg.unicast_count += 1;
                        if let Some(mac) = &packet.destination_mac {
                            Self::record_victim(agg, mac, Some(packet.destination_address), packet.timestamp);
                        }
                    } else if let Some(qs) = queriers.get(&name) {
                        for q in qs {
                            Self::record_victim(agg, &q.client_mac, Some(q.client_address), packet.timestamp);
                        }
                    }
                }
            },
            Err(e) => error!("Could not acquire name resolution responses mutex: {}", e)
        }
    }

    fn record_victim(agg: &mut ResponseAggregate, mac: &str, address: Option<IpAddr>, timestamp: DateTime<Utc>) {
        let victim = agg.victims.entry(mac.to_string()).or_insert_with(|| VictimAggregate {
            address,
            count: 0,
            first_seen: timestamp,
            last_seen: timestamp
        });

        if victim.address.is_none() {
            victim.address = address;
        }
        victim.count += 1;
        victim.first_seen = victim.first_seen.min(timestamp);
        victim.last_seen = victim.last_seen.max(timestamp);
    }

    pub fn process_report(&self) {
        let mut timer = Timer::new();

        let report = match self.responses.lock() {
            Ok(responses) => {
                if responses.is_empty() {
                    None
                } else {
                    match serde_json::to_string(&name_resolution_responses_report::generate(&responses)) {
                        Ok(report) => Some(report),
                        Err(e) => {
                            error!("Could not serialize name resolution responses report: {}", e);
                            return;
                        }
                    }
                }
            },
            Err(e) => {
                error!("Could not acquire name resolution responses mutex: {}", e);
                return;
            }
        };

        timer.stop();
        record_timer(
            timer.elapsed_microseconds(),
            "tables.nameres.timer.report_generation",
            &self.metrics
        );

        if let Some(report) = report {
            match self.leaderlink.lock() {
                Ok(link) => {
                    if let Err(e) = link.send_report("nameres/responses", report) {
                        error!("Could not submit name resolution responses report: {}", e);
                    }
                },
                Err(e) => error!("Could not acquire leader link lock for name resolution responses \
                    report submission: {}", e)
            }
        }

        clear_mutex_hashmap(&self.responses);

        match self.recent_queries.lock() {
            Ok(mut queries) => {
                let cutoff = Utc::now() - Duration::seconds(QUERY_ATTRIBUTION_SECONDS);
                queries.retain(|_, qs| {
                    qs.retain(|q| q.timestamp >= cutoff);
                    !qs.is_empty()
                });
            },
            Err(e) => error!("Could not acquire name resolution queries mutex: {}", e)
        }
    }

    pub fn calculate_metrics(&self) {
        let responses_size: i128 = match self.responses.lock() {
            Ok(r) => r.len() as i128,
            Err(e) => {
                error!("Could not acquire mutex to calculate name resolution responses table size: {}", e);
                -1
            }
        };

        let queries_size: i128 = match self.recent_queries.lock() {
            Ok(q) => q.values().map(|v| v.len() as i128).sum(),
            Err(e) => {
                error!("Could not acquire mutex to calculate name resolution queries table size: {}", e);
                -1
            }
        };

        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("tables.nameres.responses.size", responses_size);
                metrics.set_gauge("tables.nameres.recent_queries.size", queries_size);
            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

}
