use std::net::{IpAddr, Ipv6Addr};

use chrono::Utc;

use crate::protocols::parsers::icmpv6_parser::{self, Icmpv6Message};
use crate::protocols::parsers::ipv6_parser;
use crate::wired::packets::{NdpMessageType, NdpPacket};

// Stateless address autoconfiguration (RFC 4862) with DNS servers from the advertisement (RFC 8106). No DHCPv6.

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Offer {
    pub router: Ipv6Addr,
    pub prefix: Ipv6Addr,
    pub dns_servers: Vec<Ipv6Addr>
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Unusable {
    NotARouterAdvertisement,
    NotFromTheLink,
    NotADefaultRouter,
    AddressesOverDhcpv6Only,
    NoPrefix
}

impl Unusable {
    pub fn describe(&self) -> &'static str {
        match self {
            Unusable::NotARouterAdvertisement | Unusable::NotFromTheLink => "No router answered the solicitation.",
            Unusable::NotADefaultRouter => "The only router that answered does not offer itself as a default router.",
            Unusable::AddressesOverDhcpv6Only => "The router assigns addresses over DHCPv6 only, which the probe does not speak.",
            Unusable::NoPrefix => "The router advertised no prefix to form an address from."
        }
    }

    pub fn rank(&self) -> u8 {
        match self {
            Unusable::NotARouterAdvertisement => 0,
            Unusable::NotFromTheLink => 1,
            Unusable::NotADefaultRouter => 2,
            Unusable::NoPrefix => 3,
            Unusable::AddressesOverDhcpv6Only => 4
        }
    }
}

pub fn read_offer(packet: &[u8]) -> Result<Offer, Unusable> {
    let ip = ipv6_parser::parse_raw(packet, Utc::now()).map_err(|_| Unusable::NotARouterAdvertisement)?;

    match icmpv6_parser::parse(ip) {
        Ok(Icmpv6Message::Ndp(ndp)) => offer_of(&ndp),
        _ => Err(Unusable::NotARouterAdvertisement)
    }
}

pub fn offer_of(ndp: &NdpPacket) -> Result<Offer, Unusable> {
    let advertisement = match (&ndp.message_type, &ndp.router_advertisement) {
        (NdpMessageType::RouterAdvertisement, Some(advertisement)) => advertisement,
        _ => return Err(Unusable::NotARouterAdvertisement)
    };

    let router = match ndp.source_address {
        IpAddr::V6(source) if ndp.hop_limit == 255 && source.is_unicast_link_local() => source,
        _ => return Err(Unusable::NotFromTheLink)
    };

    if advertisement.router_lifetime == 0 {
        return Err(Unusable::NotADefaultRouter);
    }

    let prefix = advertisement.prefixes.iter()
        .filter(|p| p.autonomous && p.prefix_length == 64 && p.valid_lifetime > 0)
        .find_map(|p| match p.prefix {
            IpAddr::V6(prefix) if !prefix.is_unicast_link_local() && !prefix.is_multicast() => Some(prefix),
            _ => None
        });

    let prefix = match prefix {
        Some(prefix) => prefix,
        None if advertisement.managed => return Err(Unusable::AddressesOverDhcpv6Only),
        None => return Err(Unusable::NoPrefix)
    };

    let dns_servers = if advertisement.dns_servers_lifetime == Some(0) {
        Vec::new()
    } else {
        advertisement.dns_servers.iter()
            .filter_map(|server| match server {
                IpAddr::V6(server) => Some(*server),
                IpAddr::V4(_) => None
            })
            .collect()
    };

    Ok(Offer { router, prefix, dns_servers })
}

// Modified EUI-64 (RFC 4291, appendix A): ff:fe in the middle of the MAC address, universal/local bit flipped.
pub fn interface_identifier(mac: [u8; 6]) -> [u8; 8] {
    [mac[0] ^ 0x02, mac[1], mac[2], 0xff, 0xfe, mac[3], mac[4], mac[5]]
}

pub fn address(prefix: Ipv6Addr, mac: [u8; 6]) -> Ipv6Addr {
    let mut octets = prefix.octets();
    octets[8..16].copy_from_slice(&interface_identifier(mac));
    Ipv6Addr::from(octets)
}

pub fn link_local_address(mac: [u8; 6]) -> Ipv6Addr {
    address(Ipv6Addr::new(0xfe80, 0, 0, 0, 0, 0, 0, 0), mac)
}

#[cfg(test)]
mod tests {
    use super::*;

    const MAC: [u8; 6] = [0xae, 0x3f, 0x99, 0xdf, 0x3d, 0xf5];

    struct Advertisement {
        source: &'static str,
        hop_limit: u8,
        flags: u8,
        router_lifetime: u16,
        prefixes: Vec<(&'static str, u8, u8, u32)>,
        dns: Option<(u32, Vec<&'static str>)>
    }

    impl Default for Advertisement {
        fn default() -> Self {
            Advertisement {
                source: "fe80::1",
                hop_limit: 255,
                flags: 0,
                router_lifetime: 1800,
                prefixes: vec![("2001:db8:1:2::", 64, 0xc0, 86400)],
                dns: Some((600, vec!["2001:db8:1:2::53"]))
            }
        }
    }

    fn packet(a: Advertisement) -> Vec<u8> {
        let mut icmp = vec![134, 0, 0, 0, 64, a.flags];
        icmp.extend_from_slice(&a.router_lifetime.to_be_bytes());
        icmp.extend_from_slice(&[0; 8]);

        for (prefix, length, flags, valid) in &a.prefixes {
            icmp.extend_from_slice(&[3, 4, *length, *flags]);
            icmp.extend_from_slice(&valid.to_be_bytes());
            icmp.extend_from_slice(&valid.to_be_bytes());
            icmp.extend_from_slice(&[0; 4]);
            icmp.extend_from_slice(&prefix.parse::<Ipv6Addr>().unwrap().octets());
        }

        if let Some((lifetime, servers)) = &a.dns {
            icmp.extend_from_slice(&[25, 1 + 2 * servers.len() as u8, 0, 0]);
            icmp.extend_from_slice(&lifetime.to_be_bytes());
            for server in servers {
                icmp.extend_from_slice(&server.parse::<Ipv6Addr>().unwrap().octets());
            }
        }

        let mut packet = vec![0x60, 0, 0, 0];
        packet.extend_from_slice(&(icmp.len() as u16).to_be_bytes());
        packet.push(58);
        packet.push(a.hop_limit);
        packet.extend_from_slice(&a.source.parse::<Ipv6Addr>().unwrap().octets());
        packet.extend_from_slice(&"ff02::1".parse::<Ipv6Addr>().unwrap().octets());
        packet.extend_from_slice(&icmp);
        packet
    }

    #[test]
    fn forms_the_address_from_the_mac_address() {
        assert_eq!(interface_identifier(MAC), [0xac, 0x3f, 0x99, 0xff, 0xfe, 0xdf, 0x3d, 0xf5]);
        assert_eq!(link_local_address(MAC), "fe80::ac3f:99ff:fedf:3df5".parse::<Ipv6Addr>().unwrap());
        assert_eq!(
            address("2001:db8:1:2::".parse().unwrap(), MAC),
            "2001:db8:1:2:ac3f:99ff:fedf:3df5".parse::<Ipv6Addr>().unwrap()
        );
    }

    #[test]
    fn reads_router_prefix_and_dns_servers() {
        let offer = read_offer(&packet(Advertisement::default())).unwrap();

        assert_eq!(offer.router, "fe80::1".parse::<Ipv6Addr>().unwrap());
        assert_eq!(offer.prefix, "2001:db8:1:2::".parse::<Ipv6Addr>().unwrap());
        assert_eq!(offer.dns_servers, vec!["2001:db8:1:2::53".parse::<Ipv6Addr>().unwrap()]);
    }

    #[test]
    fn skips_prefixes_that_are_not_for_autoconfiguration() {
        let offer = read_offer(&packet(Advertisement {
            prefixes: vec![
                ("2001:db8:a::", 64, 0x80, 86400),
                ("2001:db8:b::", 56, 0xc0, 86400),
                ("2001:db8:c::", 64, 0xc0, 0),
                ("fe80::", 64, 0xc0, 86400),
                ("2001:db8:d::", 64, 0xc0, 86400)
            ],
            ..Advertisement::default()
        })).unwrap();

        assert_eq!(offer.prefix, "2001:db8:d::".parse::<Ipv6Addr>().unwrap());
    }

    #[test]
    fn works_without_dns_servers() {
        let offer = read_offer(&packet(Advertisement { dns: None, ..Advertisement::default() })).unwrap();
        assert!(offer.dns_servers.is_empty());

        let offer = read_offer(&packet(Advertisement {
            dns: Some((0, vec!["2001:db8:1:2::53"])),
            ..Advertisement::default()
        })).unwrap();
        assert!(offer.dns_servers.is_empty());
    }

    #[test]
    fn rejects_advertisements_that_cannot_be_used() {
        assert_eq!(
            read_offer(&packet(Advertisement { hop_limit: 64, ..Advertisement::default() })),
            Err(Unusable::NotFromTheLink)
        );
        assert_eq!(
            read_offer(&packet(Advertisement { source: "2001:db8::1", ..Advertisement::default() })),
            Err(Unusable::NotFromTheLink)
        );
        assert_eq!(
            read_offer(&packet(Advertisement { router_lifetime: 0, ..Advertisement::default() })),
            Err(Unusable::NotADefaultRouter)
        );
        assert_eq!(
            read_offer(&packet(Advertisement { prefixes: Vec::new(), ..Advertisement::default() })),
            Err(Unusable::NoPrefix)
        );
        assert_eq!(
            read_offer(&packet(Advertisement { prefixes: Vec::new(), flags: 0x80, ..Advertisement::default() })),
            Err(Unusable::AddressesOverDhcpv6Only)
        );
    }

    #[test]
    fn rejects_other_packets() {
        // A neighbor solicitation.
        let mut icmp = vec![135, 0, 0, 0, 0, 0, 0, 0];
        icmp.extend_from_slice(&"fe80::2".parse::<Ipv6Addr>().unwrap().octets());
        let mut solicitation = vec![0x60, 0, 0, 0, 0, icmp.len() as u8, 58, 255];
        solicitation.extend_from_slice(&"fe80::1".parse::<Ipv6Addr>().unwrap().octets());
        solicitation.extend_from_slice(&"ff02::1:ff00:2".parse::<Ipv6Addr>().unwrap().octets());
        solicitation.extend_from_slice(&icmp);

        assert_eq!(read_offer(&solicitation), Err(Unusable::NotARouterAdvertisement));
        assert_eq!(read_offer(&[0x60, 0, 0]), Err(Unusable::NotARouterAdvertisement));
        assert_eq!(read_offer(&[]), Err(Unusable::NotARouterAdvertisement));
    }
}
