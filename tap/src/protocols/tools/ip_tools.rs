use std::net::{IpAddr, Ipv4Addr};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use log::{debug, error, trace};
use crate::messagebus::bus::Bus;
use crate::messagebus::channel_names::WiredChannelName;
use crate::protocols::parsers::tcp::tcp_parser;
use crate::protocols::parsers::udp_parser;
use crate::protocols::parsers::icmp_parser;
use crate::to_pipeline;
use crate::wired::packets::IPv4Packet;
use crate::wired::types;

pub static DROPPED_FRAGMENTS: AtomicU64 = AtomicU64::new(0);

pub fn route_ipv4_packet(packet: IPv4Packet, bus: &Arc<Bus>) {
    // Only the first fragment carries a transport header. Fragments are not reassembled.
    if packet.fragment_offset > 0 {
        DROPPED_FRAGMENTS.fetch_add(1, Ordering::Relaxed);
        trace!("Dropping IPv4 fragment at offset [{}] from [{}] to [{}].",
            packet.fragment_offset, packet.source_address, packet.destination_address);
        return;
    }

    match types::ProtocolType::try_from(packet.protocol) {
        Ok(protocol_type) => {
            match protocol_type {
                types::ProtocolType::Icmp => {
                    match icmp_parser::parse(packet) {
                        Ok(icmp) => {
                            let icmp = Arc::new(icmp);
                            let size = icmp.size;
                            to_pipeline!(
                                WiredChannelName::IcmpPipeline,
                                bus.icmp_pipeline.sender,
                                icmp,
                                size
                            );
                        },
                        Err(e) => debug!("Could not parse ICMP packet: {}", e)
                    }
                },
                types::ProtocolType::Tcp  => {
                    let tcp = Arc::new(tcp_parser::parse(packet).unwrap()); // TODO unwrap
                    let size = tcp.size;
                    // To TCP pipeline.
                    to_pipeline!(
                                WiredChannelName::TcpPipeline,
                                bus.tcp_pipeline.sender,
                                tcp,
                                size
                    );
                },
                types::ProtocolType::Udp  => {
                    let udp = Arc::new(udp_parser::parse(packet).unwrap()); // TODO unwrap
                    let size = udp.size;

                    // To UDP pipeline.
                    to_pipeline!(
                                WiredChannelName::UdpPipeline,
                                bus.udp_pipeline.sender,
                                udp,
                                size
                    );
                }
            }
        },
        Err(..) => {
            debug!("IPv4 type not implemented: {}", packet.protocol);
        }
    }
}

static CGNAT_IS_INTERNAL: AtomicBool = AtomicBool::new(false);

// Set once from the configuration at startup.
pub fn set_cgnat_is_internal(cgnat_is_internal: bool) {
    CGNAT_IS_INTERNAL.store(cgnat_is_internal, Ordering::Relaxed);
}

pub fn is_site_local(addr: IpAddr) -> bool {
    is_site_local_with(addr, CGNAT_IS_INTERNAL.load(Ordering::Relaxed))
}

pub fn is_site_local_with(addr: IpAddr, cgnat_is_internal: bool) -> bool {
    match addr {
        IpAddr::V4(v4) => v4.is_private() || (cgnat_is_internal && is_cgnat(v4)),
        IpAddr::V6(v6) => (v6.segments()[0] & 0xfe00) == 0xfc00,
    }
}

// 100.64.0.0/10
fn is_cgnat(addr: Ipv4Addr) -> bool {
    let octets = addr.octets();
    octets[0] == 100 && (octets[1] & 0xc0) == 64
}

#[cfg(test)]
mod tests {
    use super::*;

    fn site_local(addr: &str, cgnat_is_internal: bool) -> bool {
        is_site_local_with(addr.parse::<IpAddr>().unwrap(), cgnat_is_internal)
    }

    #[test]
    fn cgnat_is_external_by_default() {
        assert!(!site_local("100.64.0.1", false));
        assert!(!site_local("100.100.100.100", false));
        assert!(site_local("10.0.0.1", false));
        assert!(site_local("172.16.5.5", false));
        assert!(site_local("192.168.1.1", false));
        assert!(!site_local("8.8.8.8", false));
    }

    #[test]
    fn cgnat_is_internal_where_configured() {
        assert!(site_local("100.64.0.0", true));
        assert!(site_local("100.100.100.100", true));
        assert!(site_local("100.127.255.255", true));
        assert!(site_local("10.0.0.1", true));
    }

    #[test]
    fn only_the_shared_address_space_is_added() {
        assert!(!site_local("100.63.255.255", true));
        assert!(!site_local("100.128.0.0", true));
        assert!(!site_local("101.64.0.1", true));
        assert!(!site_local("8.8.8.8", true));
        assert!(!site_local("2606:4700:4700::1111", true));
        assert!(site_local("fd7a:115c:a1e0::1", true));
    }
}