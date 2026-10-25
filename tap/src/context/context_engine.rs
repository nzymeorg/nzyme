use std::collections::{HashMap, HashSet};
use std::net::{IpAddr, Ipv4Addr};
use std::str::FromStr;
use std::sync::{Arc, Mutex};
use std::thread;
use chrono::{Duration, Utc};
use clokwerk::{Scheduler, TimeUnits};
use log::{debug, error, info, warn};
use pnet::util::MacAddr;
use rand::random;
use crate::configuration::{Configuration, EthernetInterfaceNetwork};
use crate::context::context_source::ContextSource;
use crate::context::context_data::{HostnameContextData, IpAddressContextData};
use crate::context::mac_address_context::MacAddressContext;
use crate::wired::ethernet::injection::injector::Injector;
use crate::wired::ethernet::injection::protocols::dns;
use crate::wired::ethernet::injection::udp::generate_ipv4_udp_packet;
use crate::wired::interfaces;
use crate::helpers::network::{ip_in_cidr, mac_address_from_string, to_mac_address_string};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::context_report::{ContextDataReport, ContextReport, MacContextReport};
use crate::metrics::Metrics;
use crate::state::state::State;

pub struct ContextEngine {
    state: Arc<State>,
    metrics: Arc<Mutex<Metrics>>,
    leaderlink: Arc<Mutex<Leaderlink>>,
    configuration: Configuration,
    macs: Arc<Mutex<HashMap<String, MacAddressContext>>>,
    local_metrics: Arc<Mutex<ContextEngineMetrics>>,
    used_transaction_ids: Arc<Mutex<Vec<u16>>>,
    injection_addresses: Arc<Mutex<HashSet<IpAddr>>>
}

#[derive(Default)]
pub struct ContextEngineMetrics {
    total_ptr_queries: i128
}

impl ContextEngine {

    pub fn new(state: Arc<State>,
               leaderlink: Arc<Mutex<Leaderlink>>,
               metrics: Arc<Mutex<Metrics>>,
               configuration: Configuration) -> Self {
        Self {
            state,
            configuration,
            metrics,
            leaderlink,
            macs: Arc::new(Mutex::new(HashMap::new())),
            local_metrics: Arc::new(Mutex::new(ContextEngineMetrics::default())),
            used_transaction_ids: Arc::new(Mutex::new(Vec::new())),
            injection_addresses: Arc::new(Mutex::new(HashSet::new()))
        }
    }

    /*
     * The PTR lookups this tap injects for context come back through its own capture. They are sensor traffic,
     * not activity of the network, and the DNS processor keeps them out of the DNS log. A lookup is ours when its
     * transaction ID is one we issued and its client is one of our injection addresses.
     */
    pub fn is_own_dns_transaction(&self, transaction_id: Option<u16>, client: &IpAddr) -> bool {
        let Some(transaction_id) = transaction_id else { return false; };

        let own_address = match self.injection_addresses.lock() {
            Ok(addresses) => addresses.contains(client),
            Err(e) => {
                error!("Could not acquire injection addresses mutex: {}", e);
                false
            }
        };

        if !own_address {
            return false;
        }

        match self.used_transaction_ids.lock() {
            Ok(ids) => ids.contains(&transaction_id),
            Err(e) => {
                error!("Could not acquire used transaction ID vector mutex: {}", e);
                false
            }
        }
    }

    pub fn initialize(&self) {
        info!("Initializing context engine.");

        let mut scheduler = Scheduler::new();

        let state = self.state.clone();
        let configuration = self.configuration.clone();
        let local_metrics = self.local_metrics.clone();
        let used_transaction_ids = self.used_transaction_ids.clone();
        let injection_addresses = self.injection_addresses.clone();
        scheduler.every(1.minute()).run(move || {
            Self::determine_additional_mac_address_context(&state,
                                                           &configuration,
                                                           &local_metrics,
                                                           &used_transaction_ids,
                                                           &injection_addresses);
        });

        // Retention cleaners.
        let retention_macs = self.macs.clone();
        let retention_configuration = self.configuration.clone();
        scheduler.every(1.minutes()).run(move || {
            Self::retention_clean(&retention_macs, &retention_configuration);
        });

        // Metrics.
        let metrics_macs = self.macs.clone();
        let metrics_metrics = self.metrics.clone();
        let metrics_localmetrics = self.local_metrics.clone();
        let metrics_used_transaction_ids = self.used_transaction_ids.clone();
        scheduler.every(10.seconds()).run(move || {
            Self::calculate_metrics(&metrics_macs,
                                    &metrics_used_transaction_ids,
                                    &metrics_metrics,
                                    &metrics_localmetrics);
        });

        // Reports.
        let report_macs = self.macs.clone();
        let leaderlink = self.leaderlink.clone();
        scheduler.every(60.seconds()).run(move || {
            Self::process_report(&report_macs, &leaderlink);
        });

        // Debug printer.
        let debug_macs = self.macs.clone();
        scheduler.every(10.seconds()).run(move || {
            match debug_macs.lock() {
                Ok(macs) => {
                    debug!("MAC Address Context: {:?}", macs);
                },
                Err(e) => error!("Could not acquire MAC context table: {}", e)
            }
        });

        thread::spawn(move || {
            loop {
                scheduler.run_pending();
                thread::sleep(std::time::Duration::from_secs(10));
            }
        });
    }

    /*
     * The registration functions are very overlapping but kept this way because there may likely
     * be more specific insertion logic per data type in the future. If this does not turn out
     * to be true, this should be refactored using a AddressContextData trait or something
     * similar.
     */

    pub fn register_mac_address_ip(&self, mac: String, ip_addr: IpAddr, source: ContextSource) {
        if Self::skip_ip_address(&ip_addr) || Self::skip_mac_address(&mac) {
            return
        }

        match self.macs.lock() {
            Ok(mut macs) => {
                let entry = macs
                    .entry(mac.clone())
                    .or_default();

                // Update timestamp of IP if we have it.
                if let Some(ip) = entry.ip_addresses.iter_mut()
                    .find(|ip| ip.source == source && ip.address == ip_addr) {

                    // IP address from this source already exists, update timestamp.
                    ip.timestamp = Utc::now();
                } else {
                    entry.mac = mac;

                    // New IP address or not previously seen from this context.
                    entry.ip_addresses.push(IpAddressContextData{
                        address: ip_addr,
                        source,
                        timestamp: Utc::now(),
                    });
                }
            },
            Err(e) => error!("Could not acquire MAC context table: {}", e)
        }
    }

    pub fn register_mac_address_hostname(&self, mac: String,
                                         new_hostname: String,
                                         dns_transaction_id: Option<u16>,
                                         source: ContextSource) {
        if Self::skip_mac_address(&mac) {
            return
        }

        match dns_transaction_id {
            Some(id) => {
                match self.used_transaction_ids.lock() {
                    Ok(mut ids) => {
                        if ids.contains(&id) {
                            // Immediately remove transaction ID from list.
                            if let Some(pos) = ids.iter().position(|&x| x == id) {
                                ids.remove(pos);
                            }
                        } else {
                            debug!("Ignoring hostname from unknown DNS transaction.");
                                return;
                        }
                    }
                    Err(e) => {
                        error!("Could not acquire used transaction ID vector mutex: {}", e);
                        return;
                    }
                }
            },
            None => {
                // No transaction ID check requested.
            }
        }

        match self.macs.lock() {
            Ok(mut macs) => {
                let entry = macs
                    .entry(mac.clone())
                    .or_default();

                // Update timestamp of IP if we have it.
                if let Some(hostname) = entry.hostnames.iter_mut()
                    .find(|h| h.source == source && h.hostname.eq(&new_hostname)) {

                    // IP address from this source already exists, update timestamp.
                    hostname.timestamp = Utc::now();
                } else {
                    entry.mac = mac;

                    // New IP address or not previously seen from this context.
                    entry.hostnames.push(HostnameContextData{
                        hostname: new_hostname,
                        source,
                        timestamp: Utc::now(),
                    });
                }
            },
            Err(e) => error!("Could not acquire MAC context table: {}", e)
        }
    }

    /*
     * This function is actively trying to pull additional context from other sources. It should
     * not run very frequently because it can be slow and introduce significant load on the sources
     * it pulls information from.
     */
    fn determine_additional_mac_address_context(state: &Arc<State>,
                                                configuration: &Configuration,
                                                metrics: &Arc<Mutex<ContextEngineMetrics>>,
                                                used_transaction_ids: &Arc<Mutex<Vec<u16>>>,
                                                injection_addresses: &Arc<Mutex<HashSet<IpAddr>>>) {
        match used_transaction_ids.lock() {
            Ok(mut ids) => ids.clear(),
            Err(e) => {
                error!("Could not acquire used transaction ID vector mutex: {}", e)
            }
        }

        match state.arp.lock() {
            Ok(arp) => {
                for (mac, ip_addresses) in arp.get_mapping() {
                    for ip in ip_addresses {
                        let lookup_ip: Ipv4Addr = match ip.addr {
                            IpAddr::V4(addr) => addr,
                            IpAddr::V6(_) => {
                                error!("IPv6 reverse lookups are not supported yet.");
                                continue;
                            }
                        };

                        if let Some(network) = Self::determine_network(ip.addr, configuration) {
                            // We have an Ethernet interface responsible for the IP address.
                            if network.injection_interface.is_none() {
                                debug!("Network [{}] has no configured injection interface.",
                                        network.cidr);
                                continue;
                            }

                            let injection_interface = network.injection_interface.unwrap();
                            let mut injector = match Injector::new(injection_interface.clone()) {
                                Ok(injector) => injector,
                                Err(e) => {
                                    error!("Could not create Injector for interface [{}]: {}",
                                        injection_interface, e);
                                    continue;
                                }
                            };

                            if let Err(e) = injector.open() {
                                error!("Could not open Injector handle for interface [{}]: {}",
                                        injection_interface, e);
                                continue;
                            }

                            let injection_mac = match network.injection_interface_mac_address {
                                Some(ref custom_mac) => {
                                    mac_address_from_string(custom_mac).unwrap_or_else(|| {
                                        error!("Invalid custom MAC address: {}", custom_mac);
                                        MacAddr::new(0, 0, 0, 0, 0, 0)
                                    })
                                }
                                None => {
                                    match interfaces::get_mac_address_of_interface(&injection_interface) {
                                        Some(mac) => mac,
                                        None => {
                                            error!("Could not determine MAC address of injection interface [{}]",
                                                injection_interface);
                                            MacAddr::new(0, 0, 0, 0, 0, 0)
                                        }
                                    }
                                }
                            };

                            let injection_ip: Ipv4Addr = match network.injection_interface_ip_address {
                                Some(ref custom_ip) => {
                                    custom_ip.parse().ok().unwrap_or_else(|| {
                                        error!("Invalid custom IP address: {}", custom_ip);
                                        Ipv4Addr::UNSPECIFIED
                                    })
                                },
                                None => {
                                    match interfaces::get_first_ipv4_address_of_interface(&injection_interface) {
                                        Some(ip) => ip,
                                        None => {
                                            error!("Could not determine IP address of injection interface [{}]",
                                                injection_interface);
                                            Ipv4Addr::UNSPECIFIED
                                        }
                                    }
                                }
                            };

                            match injection_addresses.lock() {
                                Ok(mut addresses) => { addresses.insert(IpAddr::V4(injection_ip)); },
                                Err(e) => error!("Could not acquire injection addresses mutex: {}", e)
                            }

                            for dns_server in network.dns_servers {
                                let server_ip: Ipv4Addr = match dns_server.ip() {
                                    IpAddr::V4(addr) => addr,
                                    IpAddr::V6(_) => {
                                        error!("IPv6 DNS servers for reverse lookups are not \
                                            supported yet.");
                                        continue;
                                    }
                                };

                                let server_mac = match arp.mac_address_of_ip_address(dns_server.ip()) {
                                    Some(mac) => {
                                        match MacAddr::from_str(&mac) {
                                            Ok(parsed_mac) => parsed_mac,
                                            Err(e) => {
                                                error!("Could not parse MAC address [{}]: {}", mac, e);
                                                continue;
                                            }
                                        }
                                    },
                                    None => {
                                        debug!("Could not determine MAC address of DNS server \
                                            at [{}] because it is not in ARP table.", mac);
                                        continue;
                                    }
                                };

                                let transaction_id: u16 = random();

                                // Record transaction ID we use for later reply matching.
                                match used_transaction_ids.lock() {
                                    Ok(mut ids) => ids.push(transaction_id),
                                    Err(e) => {
                                        error!("Could not acquire used transaction ID vector mutex: {}", e)
                                    }
                                }

                                let dns = generate_ipv4_udp_packet(
                                    injection_mac,
                                    server_mac,
                                    injection_ip,
                                    server_ip,
                                    random(),
                                    dns_server.port(),
                                    dns::ptr_query(lookup_ip, transaction_id)
                                );

                                match injector.inject(dns) { Err(e) => {
                                    error!("Could not inject PTR DNS to [{}] on interface [{}]: {}",
                                        dns_server, injection_interface, e);
                                    continue;
                                } _ => {
                                    // Sent. No need to query other DNS servers there may be.
                                    match metrics.lock() {
                                        Ok(mut metrics) => metrics.total_ptr_queries += 1,
                                        Err(e) => warn!("Could not acquire local metrics mutex: {}", e)
                                    }

                                    break;
                                }}
                            }
                        }
                    }
                }
            },
            Err(e) => error!("Could not acquire ARP state mutex lock: {}", e)
        }
    }

    fn determine_network(address: IpAddr, configuration: &Configuration)
        -> Option<EthernetInterfaceNetwork> {

        if let Some(interfaces) = configuration.clone().ethernet_interfaces {
            for interface in interfaces.values() {
                if let Some(networks) = &interface.networks {
                    for network in networks {
                        if ip_in_cidr(address, &network.cidr) {
                            return Some(network.clone());
                        }
                    }
                }
            }

            // No interface network CIDR contains IP address.
            None
        } else {
            // No ethernet interfaces configured.
            None
        }
    }

    fn process_report(macs: &Arc<Mutex<HashMap<String, MacAddressContext>>>,
                      leaderlink: &Arc<Mutex<Leaderlink>>) {
        match leaderlink.lock() {
            Ok(leaderlink) => {
                // Build report.
                let macs_report = match macs.lock() {
                    Ok(macs) => {
                        let mut macs_report: Vec<MacContextReport> = Vec::new();

                        for mac in macs.values() {
                            let mut ip_addresses: Vec<ContextDataReport> = Vec::new();
                            let mut hostnames: Vec<ContextDataReport> = Vec::new();

                            for ip in &mac.ip_addresses {
                                ip_addresses.push(ContextDataReport {
                                    value: ip.address.to_string(),
                                    source: ip.source.to_string(),
                                    last_seen: ip.timestamp
                                })
                            }

                            for hostname in &mac.hostnames {
                                hostnames.push(ContextDataReport {
                                    value: hostname.hostname.clone(),
                                    source: hostname.source.to_string(),
                                    last_seen: hostname.timestamp
                                })
                            }

                            macs_report.push(MacContextReport {
                                mac: mac.mac.clone(),
                                ip_addresses,
                                hostnames
                            })
                        }

                        macs_report
                    },
                    Err(e) => {
                        error!("Could not acquire MAC context table: {}", e);
                        return;
                    }
                };

                let report = ContextReport { macs: macs_report };

                // Generate JSON.
                let report = match serde_json::to_string(&report) {
                    Ok(report) => report,
                    Err(e) => {
                        error!("Could not serialize context report: {}", e);
                        return;
                    }
                };

                // Send report.
                if let Err(e) = leaderlink.send_context(report) {
                    error!("Could not submit context report: {}", e);
                }

            },
            Err(e) => {
                error!("Could not acquire leaderlink mutex: {}", e)
            }
        }
    }

    fn skip_ip_address(ip_addr: &IpAddr) -> bool {
        ip_addr.is_unspecified()
            || ip_addr.is_multicast()
            || ip_addr.is_loopback()
            || *ip_addr == Ipv4Addr::new(255, 255, 255, 255)
    }

    fn skip_mac_address(mac_addr: &str) -> bool {
        mac_addr.eq_ignore_ascii_case("FF:FF:FF:FF:FF:FF")
    }

    fn calculate_metrics(macs: &Arc<Mutex<HashMap<String, MacAddressContext>>>,
                         used_transaction_ids: &Arc<Mutex<Vec<u16>>>,
                         metrics: &Arc<Mutex<Metrics>>,
                         local_metrics: &Arc<Mutex<ContextEngineMetrics>>) {
        match macs.lock() {
            Ok(macs) => {
                match metrics.lock() {
                    Ok(mut metrics) => {
                        metrics.set_gauge("context.macs.size", macs.len() as i128);
                        metrics.set_gauge("context.macs.ips.size",
                                          macs.values().map(|v| v.ip_addresses.len() as i128).sum());
                        metrics.set_gauge("context.macs.hostnames.size",
                                          macs.values().map(|v| v.hostnames.len() as i128).sum());

                        // Used DNS transaction IDs table.
                        match used_transaction_ids.lock() {
                            Ok(mut ids) => {
                                metrics.set_gauge("context.ptr.tixs.size", ids.len() as i128);
                            },
                            Err(e) => {
                                error!("Could not acquire used transaction ID vector mutex: {}", e)
                            }
                        }

                        // Other locally maintained metrics.
                        match local_metrics.lock() {
                            Ok(local_metrics) => {
                                metrics.set_gauge("context.ptr.queries", local_metrics.total_ptr_queries);
                            },
                            Err(e) => error!("Could not acquire local metrics mutex: {}", e)
                        }
                    },
                    Err(e) => error!("Could not acquire metrics mutex: {}", e)
                }
            },
            Err(e) => error!("Could not acquire MAC context table mutex: {}", e)
        }
    }

    fn retention_clean(macs: &Arc<Mutex<HashMap<String, MacAddressContext>>>,
                       configuration: &Configuration) {
        let mac_ip_expiration_time = Utc::now() - Duration::try_hours(
            configuration.misc.context_mac_ip_retention_hours as i64
        ).unwrap();
        let mac_hostname_expiration_time = Utc::now() - Duration::try_hours(
            configuration.misc.context_mac_hostname_retention_hours as i64
        ).unwrap();

        match macs.lock() {
            Ok(mut macs) => {
                macs.retain(|_, data| {
                    data.ip_addresses.retain(|ip|
                        ip.timestamp > mac_ip_expiration_time);
                    data.hostnames.retain(|hostname|
                        hostname.timestamp > mac_hostname_expiration_time);

                    // Return true to keep the entry in the map if it still has any data attached.
                    !data.ip_addresses.is_empty() || !data.hostnames.is_empty()
                });
            },
            Err(e) => error!("Could not acquire MAC context table mutex: {}", e)
        }
    }

}