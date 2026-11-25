use std::net::IpAddr;
use anyhow::{bail, Result};
use byteorder::{BigEndian, ByteOrder};
use crate::helpers::network::{parse_domain_names, to_ipv6_address, to_mac_address_string};
use crate::protocols::parsers::ipv6_parser;
use crate::wired::packets::{
    IcmpPacket, IcmpQuotedHeader, IpPacket, NdpMessageType, NdpPacket, NdpPrefix, NdpRoute, NdpRouterAdvertisement
};

const HEADER_LENGTH: usize = 8;

const ROUTER_SOLICITATION: u8 = 133;
const ROUTER_ADVERTISEMENT: u8 = 134;
const NEIGHBOR_SOLICITATION: u8 = 135;
const NEIGHBOR_ADVERTISEMENT: u8 = 136;
const REDIRECT: u8 = 137;

const OPTION_SOURCE_LINK_LAYER_ADDRESS: u8 = 1;
const OPTION_TARGET_LINK_LAYER_ADDRESS: u8 = 2;
const OPTION_PREFIX_INFORMATION: u8 = 3;
const OPTION_REDIRECTED_HEADER: u8 = 4;
const OPTION_MTU: u8 = 5;
const OPTION_ROUTE_INFORMATION: u8 = 24;
const OPTION_RECURSIVE_DNS_SERVER: u8 = 25;
const OPTION_DNS_SEARCH_LIST: u8 = 31;

const MAX_PREFIXES: usize = 32;
const MAX_ROUTES: usize = 32;
const MAX_DNS_SERVERS: usize = 16;
const MAX_DNS_SEARCH_DOMAINS: usize = 16;

pub enum Icmpv6Message {
    Icmp(IcmpPacket),
    Ndp(NdpPacket),
    Ignored
}

#[derive(Default)]
struct NdpOptions {
    source_link_layer_address: Option<String>,
    target_link_layer_address: Option<String>,
    mtu: Option<u32>,
    prefixes: Vec<NdpPrefix>,
    routes: Vec<NdpRoute>,
    dns_servers: Vec<IpAddr>,
    dns_servers_lifetime: Option<u32>,
    dns_search_list: Vec<String>,
    redirected: Option<IcmpQuotedHeader>
}

pub fn parse(ip: IpPacket) -> Result<Icmpv6Message> {
    if ip.payload.len() < HEADER_LENGTH {
        bail!("Payload too short for ICMPv6 packet.");
    }

    let icmp_type = ip.payload[0];

    match icmp_type {
        130 | 131 | 132 | 143 | 151 | 152 | 153 => Ok(Icmpv6Message::Ignored),
        ROUTER_SOLICITATION | ROUTER_ADVERTISEMENT | NEIGHBOR_SOLICITATION | NEIGHBOR_ADVERTISEMENT =>
            Ok(Icmpv6Message::Ndp(parse_ndp(ip)?)),
        _ => Ok(Icmpv6Message::Icmp(parse_icmp(ip)?))
    }
}

fn parse_icmp(ip: IpPacket) -> Result<IcmpPacket> {
    let icmp_type = ip.payload[0];
    let icmp_code = ip.payload[1];
    let rest = &ip.payload[4..8];
    let body = &ip.payload[HEADER_LENGTH..];

    let (identifier, sequence) = match icmp_type {
        128 | 129 => (
            Some(BigEndian::read_u16(&rest[0..2])),
            Some(BigEndian::read_u16(&rest[2..4]))
        ),
        _ => (None, None)
    };

    let (redirect_gateway, quoted) = match icmp_type {
        1..=4 => (None, parse_quoted_header(body)),
        REDIRECT => {
            if body.len() < 32 {
                bail!("Payload too short for ICMPv6 redirect.");
            }

            let target = to_ipv6_address(&body[0..16]);
            let destination = to_ipv6_address(&body[16..32]);

            // The redirect goes to the sender of the redirected packet. The packet itself is optional.
            let quoted = parse_options(&body[32..]).redirected.unwrap_or(IcmpQuotedHeader {
                protocol: None,
                source_address: ip.destination_address,
                destination_address: destination,
                ttl: None,
                source_port: None,
                destination_port: None
            });

            (Some(target), Some(quoted))
        },
        _ => (None, None)
    };

    // Body + ICMP header + IP header + Ethernet (14).
    let size = (ip.payload.len() + ip.header_length + 14) as u32;

    Ok(IcmpPacket {
        source_mac: ip.source_mac,
        destination_mac: ip.destination_mac,
        source_address: ip.source_address,
        destination_address: ip.destination_address,
        ip_version: 6,
        ip_ttl: ip.ttl,
        ip_tos: ip.ip_tos,
        ip_df: None,
        icmp_type,
        icmp_code,
        identifier,
        sequence,
        redirect_gateway,
        quoted,
        payload_size: body.len() as u32,
        size,
        timestamp: ip.timestamp
    })
}

fn parse_ndp(ip: IpPacket) -> Result<NdpPacket> {
    let payload = &ip.payload;

    let (message_type, options_offset) = match payload[0] {
        ROUTER_SOLICITATION => (NdpMessageType::RouterSolicitation, 8),
        ROUTER_ADVERTISEMENT => (NdpMessageType::RouterAdvertisement, 16),
        NEIGHBOR_SOLICITATION => (NdpMessageType::NeighborSolicitation, 24),
        NEIGHBOR_ADVERTISEMENT => (NdpMessageType::NeighborAdvertisement, 24),
        other => bail!("Not a neighbor discovery message: {}", other)
    };

    if payload.len() < options_offset {
        bail!("Payload too short for neighbor discovery message.");
    }

    let options = parse_options(&payload[options_offset..]);

    let target_address = match message_type {
        NdpMessageType::NeighborSolicitation | NdpMessageType::NeighborAdvertisement =>
            Some(to_ipv6_address(&payload[8..24])),
        _ => None
    };

    let (router, solicited, overrides) = if message_type == NdpMessageType::NeighborAdvertisement {
        (Some(payload[4] & 0x80 != 0), Some(payload[4] & 0x40 != 0), Some(payload[4] & 0x20 != 0))
    } else {
        (None, None, None)
    };

    let router_advertisement = if message_type == NdpMessageType::RouterAdvertisement {
        Some(NdpRouterAdvertisement {
            current_hop_limit: payload[4],
            managed: payload[5] & 0x80 != 0,
            other_configuration: payload[5] & 0x40 != 0,
            preference: preference((payload[5] >> 3) & 0x03),
            router_lifetime: BigEndian::read_u16(&payload[6..8]),
            reachable_time: BigEndian::read_u32(&payload[8..12]),
            retransmit_timer: BigEndian::read_u32(&payload[12..16]),
            mtu: options.mtu,
            prefixes: options.prefixes,
            routes: options.routes,
            dns_servers: options.dns_servers,
            dns_servers_lifetime: options.dns_servers_lifetime,
            dns_search_list: options.dns_search_list
        })
    } else {
        None
    };

    let size = (ip.payload.len() + ip.header_length + 14) as u32;

    Ok(NdpPacket {
        source_mac: ip.source_mac,
        destination_mac: ip.destination_mac,
        source_address: ip.source_address,
        destination_address: ip.destination_address,
        hop_limit: ip.ttl,
        message_type,
        target_address,
        source_link_layer_address: options.source_link_layer_address,
        target_link_layer_address: options.target_link_layer_address,
        router,
        solicited,
        overrides,
        router_advertisement,
        size,
        timestamp: ip.timestamp
    })
}

// Stops at the first malformed option and keeps what came before it: the message itself is still valid.
fn parse_options(data: &[u8]) -> NdpOptions {
    let mut options = NdpOptions::default();
    let mut cursor = 0;

    while cursor + 2 <= data.len() {
        let option_type = data[cursor];
        let length = data[cursor + 1] as usize * 8;

        if length == 0 || cursor + length > data.len() {
            break;
        }

        let option = &data[cursor..cursor + length];

        match option_type {
            OPTION_SOURCE_LINK_LAYER_ADDRESS if length == 8 => {
                options.source_link_layer_address = Some(to_mac_address_string(&option[2..8]));
            },
            OPTION_TARGET_LINK_LAYER_ADDRESS if length == 8 => {
                options.target_link_layer_address = Some(to_mac_address_string(&option[2..8]));
            },
            OPTION_PREFIX_INFORMATION if length == 32 && options.prefixes.len() < MAX_PREFIXES => {
                if option[2] <= 128 {
                    options.prefixes.push(NdpPrefix {
                        prefix: to_ipv6_address(&option[16..32]),
                        prefix_length: option[2],
                        on_link: option[3] & 0x80 != 0,
                        autonomous: option[3] & 0x40 != 0,
                        valid_lifetime: BigEndian::read_u32(&option[4..8]),
                        preferred_lifetime: BigEndian::read_u32(&option[8..12])
                    });
                }
            },
            OPTION_REDIRECTED_HEADER if length > 8 => {
                options.redirected = parse_quoted_header(&option[8..]);
            },
            OPTION_MTU if length == 8 => {
                options.mtu = Some(BigEndian::read_u32(&option[4..8]));
            },
            OPTION_ROUTE_INFORMATION if options.routes.len() < MAX_ROUTES => {
                // The prefix is cut to what its length needs: none, eight or sixteen bytes.
                let mut prefix = [0u8; 16];
                let available = (length - 8).min(16);
                prefix[..available].copy_from_slice(&option[8..8 + available]);

                if option[2] <= 128 {
                    options.routes.push(NdpRoute {
                        prefix: to_ipv6_address(&prefix),
                        prefix_length: option[2],
                        preference: preference((option[3] >> 3) & 0x03),
                        lifetime: BigEndian::read_u32(&option[4..8])
                    });
                }
            },
            OPTION_RECURSIVE_DNS_SERVER if length >= 24 => {
                options.dns_servers_lifetime = Some(BigEndian::read_u32(&option[4..8]));
                for address in option[8..].chunks_exact(16) {
                    if options.dns_servers.len() < MAX_DNS_SERVERS {
                        options.dns_servers.push(to_ipv6_address(address));
                    }
                }
            },
            OPTION_DNS_SEARCH_LIST if length >= 16 => {
                for domain in parse_domain_names(&option[8..]) {
                    if options.dns_search_list.len() < MAX_DNS_SEARCH_DOMAINS {
                        options.dns_search_list.push(domain);
                    }
                }
            },
            _ => {}
        }

        cursor += length;
    }

    options
}

fn preference(bits: u8) -> String {
    match bits {
        1 => "HIGH",
        3 => "LOW",
        _ => "MEDIUM"
    }.to_string()
}

// Returns None rather than an error for a malformed quote: the ICMP message itself is still valid.
fn parse_quoted_header(body: &[u8]) -> Option<IcmpQuotedHeader> {
    let header = ipv6_parser::read_fixed_header(body)?;

    let ttl = header.hop_limit;
    let source_address = IpAddr::V6(header.source_address);
    let destination_address = IpAddr::V6(header.destination_address);

    // The quote is cut off at some point, so the walk over extension headers ends where the data does.
    let mut protocol = header.next_header;
    let mut cursor = ipv6_parser::HEADER_LENGTH;
    loop {
        let length = match protocol {
            ipv6_parser::HOP_BY_HOP | ipv6_parser::ROUTING | ipv6_parser::DESTINATION_OPTIONS => {
                match body.get(cursor + 1) {
                    Some(length) => (*length as usize + 1) * 8,
                    None => break
                }
            },
            ipv6_parser::FRAGMENT => 8,
            _ => break
        };

        match body.get(cursor) {
            Some(next) => protocol = *next,
            None => break
        }

        cursor += length;
    }

    let (source_port, destination_port) = if (protocol == 6 || protocol == 17) && body.len() >= cursor + 4 {
        (
            Some(BigEndian::read_u16(&body[cursor..cursor + 2])),
            Some(BigEndian::read_u16(&body[cursor + 2..cursor + 4]))
        )
    } else {
        (None, None)
    };

    Some(IcmpQuotedHeader {
        protocol: Some(protocol),
        source_address,
        destination_address,
        ttl: Some(ttl),
        source_port,
        destination_port
    })
}

#[cfg(test)]
mod tests {
    use std::net::Ipv6Addr;
    use chrono::Utc;
    use super::*;

    fn octets(address: &str) -> [u8; 16] {
        address.parse::<Ipv6Addr>().unwrap().octets()
    }

    fn ip(payload: Vec<u8>) -> IpPacket {
        IpPacket {
            source_mac: Some("AA:AA:AA:AA:AA:01".to_string()),
            destination_mac: Some("33:33:00:00:00:01".to_string()),
            header_length: 40,
            total_length: 40 + payload.len(),
            source_address: "fe80::1".parse().unwrap(),
            destination_address: "fe80::2".parse().unwrap(),
            ttl: 255,
            ip_tos: 0,
            df: None,
            more_fragments: false,
            fragment_offset: 0,
            identification: None,
            protocol: 58,
            payload,
            size: 0,
            timestamp: Utc::now()
        }
    }

    fn icmp(payload: Vec<u8>) -> IcmpPacket {
        match parse(ip(payload)).unwrap() {
            Icmpv6Message::Icmp(packet) => packet,
            _ => panic!("Not an ICMP message.")
        }
    }

    fn ndp(payload: Vec<u8>) -> NdpPacket {
        match parse(ip(payload)).unwrap() {
            Icmpv6Message::Ndp(packet) => packet,
            _ => panic!("Not a neighbor discovery message.")
        }
    }

    fn quoted_udp() -> Vec<u8> {
        let mut quoted = vec![0x60, 0, 0, 0, 0, 8, 17, 3];
        quoted.extend_from_slice(&octets("2001:db8::1"));
        quoted.extend_from_slice(&octets("2001:db8::9"));
        quoted.extend_from_slice(&[0x9C, 0x40, 0x11, 0x5C, 0, 8, 0, 0]);
        quoted
    }

    #[test]
    fn parses_echo_request() {
        let p = icmp(vec![128, 0, 0, 0, 0x12, 0x34, 0, 7, 1, 2, 3, 4]);

        assert_eq!(p.ip_version, 6);
        assert_eq!(p.icmp_type, 128);
        assert_eq!(p.identifier, Some(0x1234));
        assert_eq!(p.sequence, Some(7));
        assert_eq!(p.ip_ttl, 255);
        assert_eq!(p.ip_df, None);
        assert_eq!(p.payload_size, 4);
        assert_eq!(p.size, 12 + 40 + 14);
        assert!(!p.is_error());
    }

    #[test]
    fn parses_port_unreachable_with_quote() {
        let mut payload = vec![1, 4, 0, 0, 0, 0, 0, 0];
        payload.extend_from_slice(&quoted_udp());

        let p = icmp(payload);
        assert!(p.is_error());

        let q = p.quoted.unwrap();
        assert_eq!(q.protocol, Some(17));
        assert_eq!(q.ttl, Some(3));
        assert_eq!(q.source_address, "2001:db8::1".parse::<IpAddr>().unwrap());
        assert_eq!(q.destination_address, "2001:db8::9".parse::<IpAddr>().unwrap());
        assert_eq!(q.source_port, Some(40000));
        assert_eq!(q.destination_port, Some(4444));
    }

    #[test]
    fn quote_behind_extension_header_and_truncated_quotes() {
        let mut quoted = vec![0x60, 0, 0, 0, 0, 16, 60, 1];
        quoted.extend_from_slice(&octets("2001:db8::1"));
        quoted.extend_from_slice(&octets("2001:db8::9"));
        quoted.extend_from_slice(&[6, 0, 1, 4, 0, 0, 0, 0]);
        quoted.extend_from_slice(&[0xC0, 0x00, 0x01, 0xBB]);

        let mut payload = vec![3, 0, 0, 0, 0, 0, 0, 0];
        payload.extend_from_slice(&quoted);
        let q = icmp(payload.clone()).quoted.unwrap();
        assert_eq!(q.protocol, Some(6));
        assert_eq!(q.destination_port, Some(443));

        // Cut inside the extension header: the addresses are still there, the ports are not.
        payload.truncate(8 + 41);
        let q = icmp(payload.clone()).quoted.unwrap();
        assert_eq!(q.source_port, None);

        payload.truncate(8 + 39);
        assert!(icmp(payload).quoted.is_none());
    }

    #[test]
    fn parses_redirect_without_and_with_redirected_packet() {
        let mut payload = vec![137, 0, 0, 0, 0, 0, 0, 0];
        payload.extend_from_slice(&octets("fe80::99"));
        payload.extend_from_slice(&octets("2001:db8:5::7"));

        let p = icmp(payload.clone());
        assert!(p.is_error());
        assert_eq!(p.redirect_gateway, Some("fe80::99".parse().unwrap()));
        let q = p.quoted.unwrap();
        assert_eq!(q.destination_address, "2001:db8:5::7".parse::<IpAddr>().unwrap());
        assert_eq!(q.source_address, "fe80::2".parse::<IpAddr>().unwrap());
        assert_eq!(q.protocol, None);

        // Redirected header option: 8 bytes of option header, then the packet.
        payload.extend_from_slice(&[4, 7, 0, 0, 0, 0, 0, 0]);
        payload.extend_from_slice(&quoted_udp());
        let q = icmp(payload).quoted.unwrap();
        assert_eq!(q.protocol, Some(17));
        assert_eq!(q.destination_port, Some(4444));
    }

    #[test]
    fn ignores_multicast_listener_discovery() {
        assert!(matches!(parse(ip(vec![143, 0, 0, 0, 0, 0, 0, 1])).unwrap(), Icmpv6Message::Ignored));
        assert!(matches!(parse(ip(vec![130, 0, 0, 0, 0, 0, 0, 0])).unwrap(), Icmpv6Message::Ignored));
    }

    #[test]
    fn parses_neighbor_solicitation_and_advertisement() {
        let mut solicitation = vec![135, 0, 0, 0, 0, 0, 0, 0];
        solicitation.extend_from_slice(&octets("fe80::5"));
        solicitation.extend_from_slice(&[1, 1, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0x01]);

        let p = ndp(solicitation);
        assert_eq!(p.message_type, NdpMessageType::NeighborSolicitation);
        assert_eq!(p.target_address, Some("fe80::5".parse().unwrap()));
        assert_eq!(p.source_link_layer_address, Some("AA:AA:AA:AA:AA:01".to_string()));
        assert_eq!(p.hop_limit, 255);
        assert_eq!(p.router, None);

        let mut advertisement = vec![136, 0, 0, 0, 0x60, 0, 0, 0];
        advertisement.extend_from_slice(&octets("fe80::5"));
        advertisement.extend_from_slice(&[2, 1, 0xBB, 0xBB, 0xBB, 0xBB, 0xBB, 0x05]);

        let p = ndp(advertisement);
        assert_eq!(p.message_type, NdpMessageType::NeighborAdvertisement);
        assert_eq!(p.target_link_layer_address, Some("BB:BB:BB:BB:BB:05".to_string()));
        assert_eq!(p.router, Some(false));
        assert_eq!(p.solicited, Some(true));
        assert_eq!(p.overrides, Some(true));
    }

    #[test]
    fn parses_router_advertisement_options() {
        // Hop limit 64, managed and high preference, lifetime 1800.
        let mut payload = vec![134, 0, 0, 0, 64, 0x88, 0x07, 0x08, 0, 0, 0, 0, 0, 0, 0, 0];
        payload.extend_from_slice(&[1, 1, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0x01]);
        payload.extend_from_slice(&[5, 1, 0, 0, 0, 0, 0x05, 0xDC]);

        payload.extend_from_slice(&[3, 4, 64, 0xC0, 0, 0x27, 0x8D, 0, 0, 0x09, 0x3A, 0x80, 0, 0, 0, 0]);
        payload.extend_from_slice(&octets("2001:db8:1200:34::"));

        payload.extend_from_slice(&[25, 3, 0, 0, 0, 0, 0x0E, 0x10]);
        payload.extend_from_slice(&octets("2001:db8::53"));

        payload.extend_from_slice(&[31, 3, 0, 0, 0, 0, 0x0E, 0x10]);
        payload.extend_from_slice(&[7, b'e', b'x', b'a', b'm', b'p', b'l', b'e', 3, b'o', b'r', b'g', 0, 0, 0, 0]);

        // Route information for 2001:db8:ff00::/40 with a prefix cut to eight bytes, low preference.
        payload.extend_from_slice(&[24, 2, 40, 0x18, 0, 0, 0x07, 0x08, 0x20, 0x01, 0x0d, 0xb8, 0xff, 0, 0, 0]);

        let p = ndp(payload);
        assert_eq!(p.source_link_layer_address, Some("AA:AA:AA:AA:AA:01".to_string()));

        let ra = p.router_advertisement.unwrap();
        assert_eq!(ra.current_hop_limit, 64);
        assert!(ra.managed);
        assert!(!ra.other_configuration);
        assert_eq!(ra.preference, "HIGH");
        assert_eq!(ra.router_lifetime, 1800);
        assert_eq!(ra.mtu, Some(1500));
        assert_eq!(ra.prefixes, vec![NdpPrefix {
            prefix: "2001:db8:1200:34::".parse().unwrap(),
            prefix_length: 64,
            on_link: true,
            autonomous: true,
            valid_lifetime: 2592000,
            preferred_lifetime: 604800
        }]);
        assert_eq!(ra.dns_servers, vec!["2001:db8::53".parse::<IpAddr>().unwrap()]);
        assert_eq!(ra.dns_servers_lifetime, Some(3600));
        assert_eq!(ra.dns_search_list, vec!["example.org".to_string()]);
        assert_eq!(ra.routes, vec![NdpRoute {
            prefix: "2001:db8:ff00::".parse().unwrap(),
            prefix_length: 40,
            preference: "LOW".to_string(),
            lifetime: 1800
        }]);
    }

    #[test]
    fn malformed_options_keep_what_came_before() {
        let mut payload = vec![134, 0, 0, 0, 64, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
        payload.extend_from_slice(&[5, 1, 0, 0, 0, 0, 0x05, 0xDC]);
        let mut zero = payload.clone();
        zero.extend_from_slice(&[3, 0, 0, 0, 0, 0, 0, 0]);
        assert_eq!(ndp(zero).router_advertisement.unwrap().mtu, Some(1500));

        payload.extend_from_slice(&[3, 4, 64, 0xC0]);
        let ra = ndp(payload).router_advertisement.unwrap();
        assert_eq!(ra.mtu, Some(1500));
        assert!(ra.prefixes.is_empty());
    }

    #[test]
    fn rejects_truncated_messages() {
        assert!(parse(ip(vec![135, 0, 0, 0])).is_err());
        assert!(parse(ip(vec![135, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3])).is_err());
        assert!(parse(ip(vec![137, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3])).is_err());
        assert!(parse(ip(vec![134, 0, 0, 0, 0, 0, 0, 0])).is_err());
    }
}
