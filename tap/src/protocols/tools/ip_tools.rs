use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};
use std::str::FromStr;
use std::sync::{Arc, OnceLock};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use cidr::IpCidr;
use log::{debug, error, trace};
use crate::messagebus::bus::Bus;
use crate::messagebus::channel_names::WiredChannelName;
use crate::protocols::parsers::tcp::tcp_parser;
use crate::protocols::parsers::udp_parser;
use crate::protocols::parsers::icmp_parser;
use crate::protocols::parsers::icmpv6_parser::{self, Icmpv6Message};
use crate::protocols::parsers::ipv6_in_ipv4_parser;
use crate::to_pipeline;
use crate::wired::packets::{IpPacket, Ipv6TransitionObservation};
use crate::wired::types;

pub static DROPPED_FRAGMENTS_IPV4: AtomicU64 = AtomicU64::new(0);
pub static DROPPED_FRAGMENTS_IPV6: AtomicU64 = AtomicU64::new(0);

pub fn route_ip_packet(packet: IpPacket, bus: &Arc<Bus>) {
    // Counted with its fragments, which carry tunneled data like any other packet of the tunnel.
    if packet.protocol == ipv6_in_ipv4_parser::PROTOCOL_IPV6_ENCAPSULATION && packet.source_address.is_ipv4() {
        match ipv6_in_ipv4_parser::parse(packet) {
            Ok(tunneled) => {
                let observation = Ipv6TransitionObservation::Ipv6InIpv4(tunneled);
                let size = observation.estimate_struct_size();
                to_pipeline!(
                    WiredChannelName::Ipv6TransitionPipeline,
                    bus.ipv6_transition_pipeline.sender,
                    Arc::new(observation),
                    size
                );
            },
            Err(e) => debug!("Could not parse IPv6 in IPv4 packet: {}", e)
        }

        return;
    }

    // Only the first fragment carries a transport header. Fragments are not reassembled.
    if packet.fragment_offset > 0 {
        let counter = if packet.source_address.is_ipv4() { &DROPPED_FRAGMENTS_IPV4 } else { &DROPPED_FRAGMENTS_IPV6 };
        counter.fetch_add(1, Ordering::Relaxed);
        trace!("Dropping IP fragment at offset [{}] from [{}] to [{}].",
            packet.fragment_offset, packet.source_address, packet.destination_address);
        return;
    }

    match types::ProtocolType::try_from(packet.protocol) {
        Ok(protocol_type) => {
            match protocol_type {
                types::ProtocolType::Icmp => {
                    if !packet.source_address.is_ipv4() || !packet.destination_address.is_ipv4() {
                        debug!("Ignoring ICMP in a packet that is not IPv4.");
                        return;
                    }

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
                types::ProtocolType::Icmpv6 => {
                    if !packet.source_address.is_ipv6() && !packet.destination_address.is_ipv6() {
                        debug!("Ignoring ICMPv6 in a packet that is not IPv6.");
                        return;
                    }

                    match icmpv6_parser::parse(packet) {
                        Ok(Icmpv6Message::Icmp(icmp)) => {
                            let icmp = Arc::new(icmp);
                            let size = icmp.size;
                            to_pipeline!(
                                WiredChannelName::IcmpPipeline,
                                bus.icmp_pipeline.sender,
                                icmp,
                                size
                            );
                        },
                        Ok(Icmpv6Message::Ndp(ndp)) => {
                            let ndp = Arc::new(ndp);
                            let size = ndp.size;
                            to_pipeline!(
                                WiredChannelName::NdpPipeline,
                                bus.ndp_pipeline.sender,
                                ndp,
                                size
                            );
                        },
                        Ok(Icmpv6Message::Ignored) => {},
                        Err(e) => debug!("Could not parse ICMPv6 packet: {}", e)
                    }
                },
                types::ProtocolType::Tcp  => {
                    match tcp_parser::parse(packet) {
                        Ok(tcp) => {
                            let tcp = Arc::new(tcp);
                            let size = tcp.size;
                            to_pipeline!(
                                WiredChannelName::TcpPipeline,
                                bus.tcp_pipeline.sender,
                                tcp,
                                size
                            );
                        },
                        Err(e) => debug!("Could not parse TCP segment: {}", e)
                    }
                },
                types::ProtocolType::Udp  => {
                    match udp_parser::parse(packet) {
                        Ok(udp) => {
                            let udp = Arc::new(udp);
                            let size = udp.size;
                            to_pipeline!(
                                WiredChannelName::UdpPipeline,
                                bus.udp_pipeline.sender,
                                udp,
                                size
                            );
                        },
                        Err(e) => debug!("Could not parse UDP datagram: {}", e)
                    }
                }
            }
        },
        Err(..) => {
            debug!("IP protocol not implemented: {}", packet.protocol);
        }
    }
}

static CGNAT_IS_INTERNAL: AtomicBool = AtomicBool::new(false);
static INTERNAL_NETWORKS: OnceLock<Vec<IpCidr>> = OnceLock::new();

// Set once from the configuration at startup.
pub fn set_cgnat_is_internal(cgnat_is_internal: bool) {
    CGNAT_IS_INTERNAL.store(cgnat_is_internal, Ordering::Relaxed);
}

pub fn set_internal_networks(networks: &[String]) {
    let parsed = networks.iter()
        .filter_map(|network| match IpCidr::from_str(network) {
            Ok(cidr) => Some(cidr),
            Err(e) => {
                error!("Ignoring invalid internal network [{}]: {}", network, e);
                None
            }
        })
        .collect();

    if INTERNAL_NETWORKS.set(parsed).is_err() {
        error!("Internal networks are already set.");
    }
}

pub fn is_site_local(addr: IpAddr) -> bool {
    is_site_local_with(
        addr,
        CGNAT_IS_INTERNAL.load(Ordering::Relaxed),
        INTERNAL_NETWORKS.get().map_or(&[], |networks| networks.as_slice())
    )
}

pub fn is_site_local_with(addr: IpAddr, cgnat_is_internal: bool, internal_networks: &[IpCidr]) -> bool {
    let by_range = match addr {
        IpAddr::V4(v4) => v4.is_private() || v4.is_link_local() || (cgnat_is_internal && is_cgnat(v4)),
        IpAddr::V6(v6) => v6.is_unique_local() || v6.is_unicast_link_local() || is_deprecated_site_local(v6),
    };

    by_range || internal_networks.iter().any(|network| network.contains(&addr))
}

// 100.64.0.0/10
fn is_cgnat(addr: Ipv4Addr) -> bool {
    let octets = addr.octets();
    octets[0] == 100 && (octets[1] & 0xc0) == 64
}

// fec0::/10
fn is_deprecated_site_local(addr: Ipv6Addr) -> bool {
    (addr.segments()[0] & 0xffc0) == 0xfec0
}

#[cfg(test)]
mod tests {
    use super::*;

    fn site_local(addr: &str, cgnat_is_internal: bool) -> bool {
        is_site_local_with(addr.parse::<IpAddr>().unwrap(), cgnat_is_internal, &[])
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

    #[test]
    fn link_local_and_unique_local_are_internal() {
        assert!(site_local("169.254.10.20", false));
        assert!(site_local("fe80::1c2d:3eff:fe4f:5a6b", false));
        assert!(site_local("febf::1", false));
        assert!(site_local("fec0::1", false));
        assert!(site_local("fc00::1", false));
        assert!(site_local("fdff:ffff::1", false));
        assert!(!site_local("fe00::1", false));
        assert!(!site_local("2001:db8::1", false));
        assert!(!site_local("ff02::1", false));
        assert!(!site_local("::1", false));
    }

    #[test]
    fn configured_networks_are_internal() {
        let networks = vec![
            IpCidr::from_str("2001:db8:aa00::/40").unwrap(),
            IpCidr::from_str("198.51.100.0/24").unwrap()
        ];

        let internal = |addr: &str| is_site_local_with(addr.parse::<IpAddr>().unwrap(), false, &networks);

        assert!(internal("2001:db8:aa12::5"));
        assert!(!internal("2001:db8:ab00::5"));
        assert!(internal("198.51.100.9"));
        assert!(!internal("198.51.101.9"));
        assert!(internal("10.0.0.1"));
    }
}