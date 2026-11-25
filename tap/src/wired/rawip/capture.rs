use std::sync::{Arc, Mutex};
use chrono::Utc;
use log::{debug, error, info, trace, warn};
use crate::messagebus::bus::Bus;
use crate::metrics::Metrics;
use crate::protocols::parsers::{ipv4_parser, ipv6_parser};
use crate::protocols::tools::ip_tools::route_ip_packet;
use crate::wired::types::EtherType;

pub struct Capture {
    pub metrics: Arc<Mutex<Metrics>>,
    pub bus: Arc<Bus>
}

impl Capture {
    
    pub fn run(&mut self, device_name: &str) {
        info!("Starting Raw IP capture on [{}].", &device_name);

        let device = match pcap::Capture::from_device(device_name) {
            Ok(device) => {
                device
                    .immediate_mode(true)
                    .promisc(true)
            },
            Err(e) => {
                error!("Could not get PCAP device handle on [{}]: {}", &device_name, e);
                return;
            }
        };

        let mut handle = match device.open() {
            Ok(handle) => handle,
            Err(e) => {
                error!("Could not get PCAP capture handle on [{}]: {}", &device_name, e);
                return;
            }
        };

        let stats = handle.stats();

        loop {
            let packet = match handle.next_packet() {
                Ok(packet) => packet,
                Err(e) => {
                    debug!("Raw IP capture exception: {}", e);
                    continue;
                }
            };
            
            if packet.data.is_empty() {
                continue;
            }

            let len = packet.data.len();

            match self.metrics.lock() {
                Ok(mut metrics) => {
                    match stats {
                        Ok(stats) => {
                            metrics.increment_processed_bytes_total(len as u32);
                            metrics.update_capture(device_name, true, stats.dropped, stats.if_dropped, true);
                        },
                        Err(ref e) => {
                            error!("Could not fetch handle stats for capture [{}] metrics update: {}", device_name, e);
                        }
                    }
                },
                Err(e) => error!("Could not acquire metrics mutex: {}", e)
            }
            
            match Self::translate_ethertype(packet.data[0]) {
                EtherType::IPv4 => {
                    let ipv4 = match ipv4_parser::parse_raw(&packet, Utc::now()) {
                        Ok(x) => x,
                        Err(err) => {
                            warn!("Could not parse IPv4 packet: {}", err);
                            continue;
                        }
                    };

                    route_ip_packet(ipv4, &self.bus);
                },
                EtherType::IPv6 => {
                    let ipv6 = match ipv6_parser::parse_raw(&packet, Utc::now()) {
                        Ok(x) => x,
                        Err(err) => {
                            debug!("Could not parse IPv6 packet: {}", err);
                            continue;
                        }
                    };

                    route_ip_packet(ipv6, &self.bus);
                },
                _ => {
                    trace!("Raw IP type [{}] is not implemented.", packet.data[0]);
                    continue;
                }
            }
        }
    }
    
    /*
     * Even though this is the Raw IP capture, and we have no Ethernet data, we can easily re-use
     * the rest of the tap architecture (especially channel/pipeline routing) if we construct the
     * equivalent EtherType of a raw IP packet. We already know that it's IP data, so there is
     * only a chance of IPv4, IPv6 or supporting protocols like ICMP, ICMPv6 etc.
     *
     * This is really mostly a naming question. 
     */
    fn translate_ethertype(first_ip_header_byte: u8) -> EtherType {
        match first_ip_header_byte >> 4 {
            4 => EtherType::IPv4,
            6 => EtherType::IPv6,
            _ => EtherType::NotImplemented,
        }
    }
    
}