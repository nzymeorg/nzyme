use std::{collections::HashMap, sync::{Arc, Mutex}, thread};
use std::net::{IpAddr, Ipv4Addr};
use std::ptr::copy;
use crate::{helpers::math, metrics::Metrics, system_state::SystemState};

use chrono::{DateTime, Duration, Utc};
use clokwerk::{Scheduler, TimeUnits};
use log::{debug, error, info};
use crate::configuration::Configuration;
use crate::context::context_engine::ContextEngine;
use crate::context::context_source::ContextSource;
use crate::state::tables::dns_table::DnsTable;
use crate::wired::types::DNSDataType::PTR;
use crate::wired::types::DNSType::QueryResponse;
use crate::state::state::State;
use crate::wired::packets::DNSPacket;
use crate::wired::types::DNSType;

pub struct DnsProcessor {
    system_state: Arc<SystemState>,
    state: Arc<State>,
    context_engine: Arc<ContextEngine>,
    dns_table: Arc<Mutex<DnsTable>>,
    query_entropy: Arc<Mutex<HashMap<DateTime<Utc>, f32>>>,
    response_entropy: Arc<Mutex<HashMap<DateTime<Utc>, f32>>>,
    entropy_zscore_threshold: f32,
    dns_servers: Vec<IpAddr>
}

struct ZScoreResult {
    zscore: f32,
    mean: f32
}

impl DnsProcessor {

    pub fn new(system_state: Arc<SystemState>,
               state: Arc<State>,
               context_engine: Arc<ContextEngine>,
               dns_table: Arc<Mutex<DnsTable>>, 
               metrics: Arc<Mutex<Metrics>>,
               configuration: &Configuration) -> Self {
        let query_entropy = Arc::new(Mutex::new(HashMap::new()));
        let query_entropy_ret = query_entropy.clone();
        let query_entropy_metrics = query_entropy.clone();

        let response_entropy = Arc::new(Mutex::new(HashMap::new()));
        let response_entropy_ret = response_entropy.clone();
        let response_entropy_metrics = response_entropy.clone();

        // Clean and update entropy table.
        let mut scheduler = Scheduler::new();
        scheduler.every(5.minutes()).run(move || {
            Self::clean_entropy_table(&query_entropy_ret);
            Self::clean_entropy_table(&response_entropy_ret);
        });

        scheduler.every(10.seconds()).run(move || {
            match metrics.lock() {
                Ok(mut metrics) => {
                    let query_table_size: i128 = match query_entropy_metrics.lock() {
                        Ok(data) => data.len() as i128,
                        Err(e) => {
                            error!("Could not acquire query entropy mutex for metrics: {}", e);
                            -1
                        }
                    };

                    let response_table_size: i128 = match response_entropy_metrics.lock() {
                        Ok(data) => data.len() as i128,
                        Err(e) => {
                            error!("Could not acquire response entropy mutex for metrics: {}", e);
                            -1
                        }
                    };

                    metrics.set_gauge("processors.dns.entropy.queries.table_size", query_table_size);
                    metrics.set_gauge("processors.dns.entropy.responses.table_size", response_table_size);
                }, 
                Err(e) => error!("Could not acquire metrics mutex: {}", e)
            }
        });

        thread::spawn(move || {
            loop {
                scheduler.run_pending();
                thread::sleep(std::time::Duration::from_millis(100));
            }
        });

        // Build a list of our own DNS servers, according to network config.
        let mut dns_servers = Vec::new();
        if let Some(interfaces) = &configuration.ethernet_interfaces {
            for interface in interfaces.values() {
                if let Some(networks) = &interface.networks {
                    dns_servers.extend(
                        networks.iter()
                            .flat_map(|network| network.dns_servers.iter()
                                    .map(|dns_server| dns_server.ip()))
                    );
                }
            }
        }

        DnsProcessor {
            system_state,
            state,
            context_engine,
            dns_table,
            query_entropy,
            response_entropy,
            dns_servers,
            entropy_zscore_threshold: configuration.protocols.dns.entropy_zscore_threshold,
        }
    }

    pub fn process(&mut self, packet: Arc<DNSPacket>) {
        // Decided before context registration below, which consumes the transaction ID of an answered lookup.
        let own_lookup = match packet.dns_type {
            DNSType::Query => self.context_engine.is_own_dns_transaction(packet.transaction_id, &packet.source_address),
            DNSType::QueryResponse => self.context_engine.is_own_dns_transaction(packet.transaction_id, &packet.destination_address)
        };

        // Is this a PTR response for an internal host that we can use for context?
        if packet.dns_type == QueryResponse && packet.queries.is_some()
            && packet.transaction_id.is_some() {

            if let Some(queries) = &packet.queries {
                for query in queries {
                    if query.dns_type == PTR && self.dns_servers.contains(&packet.source_address) {
                        // This is a PTR response from one of our servers.
                        if let Some(responses) = &packet.responses {
                            for response in responses {
                                if response.value.is_none() {
                                   continue; 
                                }
                                
                                match self.state.arp.lock() {
                                    Ok(arp) => {
                                        let response_ip = match Self::reverse_dns_to_ip(&response.name) {
                                            Some(ip) => ip,
                                            None => {
                                                info!("{:?}", packet);

                                                error!("Could not parse PTR response [{}] to IP.",
                                                    response.name);
                                                continue;
                                            }
                                        };

                                        let mac = match arp.mac_address_of_ip_address(response_ip) {
                                            Some(mac) => mac,
                                            None => {
                                                debug!("We don't have MAC address of IP [{}] in \
                                                    ARP table.", response_ip);
                                                continue;
                                            }
                                        };

                                        self.context_engine.register_mac_address_hostname(
                                            mac,
                                            response.value.clone().unwrap(),
                                            packet.transaction_id,
                                            ContextSource::PtrDns
                                        );
                                    },
                                    Err(e) => {
                                        error!("Could not acquire ARP state: {}", e);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if own_lookup {
            return;
        }

        match self.dns_table.lock() {
            Ok(mut table) => {
                match packet.dns_type {
                    DNSType::Query => {
                        debug!("DNS request: {:?}", packet);

                        // Walk all queries.
                        if let Some(queries) = &packet.queries {
                            for query in queries {
                                // Entropy.
                                if let Some(transaction_id) = packet.transaction_id { // Skip MDNS.
                                    if let Some(entropy) = query.entropy {
                                        // Add entropy to table.
                                        Self::record_entropy(&mut self.query_entropy, entropy);

                                        // Calculate zscore of entropy.
                                        let zscore = Self::entropy_zscore(&mut self.query_entropy, entropy);

                                        // Handle outlier if we have a zscore, training is over, and we are above threshold.
                                        if let Some(zscore) = zscore {
                                            if !self.system_state.is_in_training() && zscore.zscore > self.entropy_zscore_threshold {
                                                table.register_exceeded_entropy(
                                                    transaction_id,
                                                    entropy,
                                                    zscore.zscore,
                                                    zscore.mean,
                                                    packet.timestamp
                                                );
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        table.register_request(packet);
                    },
                    DNSType::QueryResponse => {
                        debug!("DNS response: {:?}", packet);
                        
                        // Walk all responses.
                        if let Some(responses) = &packet.responses {
                            for response in responses {
                                if response.value.is_none() {
                                    continue
                                }

                                // Entropy.
                                if let Some(transaction_id) = packet.transaction_id { // Skip MDNS.
                                    if let Some(entropy) = response.entropy {
                                        // Add entropy to table.
                                        Self::record_entropy(&mut self.response_entropy, entropy);

                                        // Calculate zscore of entropy if training period is over.
                                        let zscore = Self::entropy_zscore(&mut self.response_entropy, entropy);

                                        // Handle outlier if we have a zscore, training is over, and we are above threshold.
                                        if let Some(zscore) = zscore {
                                            if !self.system_state.is_in_training() && zscore.zscore > self.entropy_zscore_threshold {
                                                table.register_exceeded_entropy(
                                                    transaction_id,
                                                    entropy,
                                                    zscore.zscore,
                                                    zscore.mean,
                                                    packet.timestamp
                                                );
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        table.register_response(packet);
                    }
                }
            },
            Err(e) => {
                error!("Could not process DNS packet: {}", e);
            }
        }
    }

    fn record_entropy(table: &mut Arc<Mutex<HashMap<DateTime<Utc>, f32>>>, entropy: f32) {
        match table.lock() {
            Ok(mut table) => {
                table.insert(Utc::now(), entropy);
            }
            Err(e) => error!("Could not acquire DNS entropy table mutex: {}", e)
        }
    }

    #[allow(clippy::needless_return)]
    fn entropy_zscore(table: &mut Arc<Mutex<HashMap<DateTime<Utc>, f32>>>, entropy: f32)
        -> Option<ZScoreResult> {

        match table.lock() {
            Ok(table) => {
                let values: Vec<f32> = table.values().copied().collect();

                let mean = math::mean(&values);
                let stddev = math::std_deviation(&values);

                return match (mean, stddev) {
                    (Some(mean), Some(std_deviation)) => {
                        let diff = entropy - mean;
                        Some(ZScoreResult { zscore: diff / std_deviation, mean })
                    },
                    _ => None
                };
            },
            Err(e) => {
                error!("Could not acquire entropy table mutex for zscore calculation: {}", e);
                None
            }
        }
    }

    fn clean_entropy_table(table: &Arc<Mutex<HashMap<DateTime<Utc>, f32>>>) {
        match table.lock() {
            Ok(mut table) => {
                let mut new_table: HashMap<DateTime<Utc>, f32> = HashMap::new();

                #[allow(deprecated)] let cutoff = Utc::now() - Duration::minutes(10);
                for (dt, val) in &*table {
                    if *dt > cutoff {
                        new_table.insert(*dt, *val);
                    }
                }

                *table = new_table;
            },
            Err(e) => error!("Could not acquire entropy table mutex for retention cleaning: {}", e)
        }
    }

    fn reverse_dns_to_ip(dns_str: &str) -> Option<IpAddr> {
        // Split the string by the '.' and take the first four parts (the IP segments).
        let parts: Vec<&str> = dns_str.split('.').take(4).collect();

        // Ensure we have exactly four parts (this is necessary for a valid IPv4 address).
        if parts.len() == 4 {
            // Reverse the parts and try to parse them as u8, which is valid for IPv4 segments.
            let octets: Vec<u8> = parts.iter()
                .rev()  // Reverse the parts to get the correct order.
                .filter_map(|part| part.parse().ok())  // Parse each segment to u8.
                .collect();

            // Ensure we got exactly 4 valid octets.
            if octets.len() == 4 {
                // Construct the Ipv4Addr and return it wrapped in an IpAddr.
                return Some(IpAddr::V4(Ipv4Addr::new(octets[0], octets[1], octets[2], octets[3])));
            }
        }

        // Return None if parsing failed.
        None
    }

}
