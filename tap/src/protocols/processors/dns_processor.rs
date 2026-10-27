use std::sync::{Arc, Mutex};
use std::net::{IpAddr, Ipv4Addr};
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
    state: Arc<State>,
    context_engine: Arc<ContextEngine>,
    dns_table: Arc<Mutex<DnsTable>>,
    dns_servers: Vec<IpAddr>
}

impl DnsProcessor {

    pub fn new(state: Arc<State>,
               context_engine: Arc<ContextEngine>,
               dns_table: Arc<Mutex<DnsTable>>,
               configuration: &Configuration) -> Self {
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
            state,
            context_engine,
            dns_table,
            dns_servers
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

                        table.register_request(packet);
                    },
                    DNSType::QueryResponse => {
                        debug!("DNS response: {:?}", packet);

                        table.register_response(packet);
                    }
                }
            },
            Err(e) => {
                error!("Could not process DNS packet: {}", e);
            }
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
