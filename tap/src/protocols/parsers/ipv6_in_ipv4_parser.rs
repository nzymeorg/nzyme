use std::net::IpAddr;

use anyhow::{bail, Result};

use crate::protocols::parsers::ipv6_parser;
use crate::wired::packets::{IpPacket, Ipv6InIpv4Packet};

pub const PROTOCOL_IPV6_ENCAPSULATION: u8 = 41;

pub fn parse(packet: IpPacket) -> Result<Ipv6InIpv4Packet> {
    let (IpAddr::V4(source_address), IpAddr::V4(destination_address)) = (packet.source_address, packet.destination_address) else {
        bail!("Not carried in IPv4.");
    };

    // Only the first fragment starts with the IPv6 header. Later fragments are counted, not read.
    let tunneled_addresses = if packet.fragment_offset == 0 {
        match ipv6_parser::read_fixed_header(&packet.payload) {
            Some(header) => Some((header.source_address, header.destination_address)),
            None => bail!("Payload is not an IPv6 packet.")
        }
    } else {
        None
    };

    Ok(Ipv6InIpv4Packet {
        source_mac: packet.source_mac,
        destination_mac: packet.destination_mac,
        source_address,
        destination_address,
        tunneled_addresses,
        tunneled_bytes: packet.payload.len() as u32,
        size: packet.size,
        timestamp: packet.timestamp
    })
}

#[cfg(test)]
pub mod tests {
    use std::net::Ipv6Addr;
    use chrono::{DateTime, Utc};
    use super::*;

    pub fn at(seconds: i64) -> DateTime<Utc> {
        DateTime::from_timestamp(1_700_000_000 + seconds, 0).unwrap()
    }

    pub fn inner(source: &str, destination: &str, payload_length: usize) -> Vec<u8> {
        let mut packet = vec![0x60, 0, 0, 0];
        packet.extend_from_slice(&(payload_length as u16).to_be_bytes());
        packet.push(59);
        packet.push(64);
        packet.extend_from_slice(&source.parse::<Ipv6Addr>().unwrap().octets());
        packet.extend_from_slice(&destination.parse::<Ipv6Addr>().unwrap().octets());
        packet.extend(std::iter::repeat_n(0u8, payload_length));
        packet
    }

    pub fn outer(source: &str, destination: &str, payload: Vec<u8>, fragment_offset: u16, seconds: i64) -> IpPacket {
        IpPacket {
            source_mac: Some(format!("mac-of-{}", source)),
            destination_mac: Some(format!("mac-of-{}", destination)),
            header_length: 20,
            total_length: 20 + payload.len(),
            source_address: source.parse().unwrap(),
            destination_address: destination.parse().unwrap(),
            ttl: 64,
            ip_tos: 0,
            df: Some(false),
            more_fragments: false,
            fragment_offset,
            identification: Some(1),
            protocol: PROTOCOL_IPV6_ENCAPSULATION,
            size: 34 + payload.len() as u32,
            payload,
            timestamp: at(seconds)
        }
    }

    #[test]
    fn reads_the_tunneled_addresses() {
        let p = parse(outer("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 60), 0, 0)).unwrap();

        assert_eq!(p.source_address.to_string(), "192.0.2.10");
        assert_eq!(p.destination_address.to_string(), "198.51.100.1");
        assert_eq!(p.source_mac.as_deref(), Some("mac-of-192.0.2.10"));
        assert_eq!(p.tunneled_addresses, Some(("2001:db8:1::10".parse().unwrap(), "2001:db8:ffff::1".parse().unwrap())));
        assert_eq!(p.tunneled_bytes, 100);
        assert_eq!(p.timestamp, at(0));
    }

    #[test]
    fn later_fragments_are_not_read() {
        let p = parse(outer("192.0.2.10", "198.51.100.1", vec![0xab; 100], 185, 0)).unwrap();

        assert_eq!(p.tunneled_addresses, None);
        assert_eq!(p.tunneled_bytes, 100);
    }

    #[test]
    fn rejects_what_does_not_carry_ipv6() {
        assert!(parse(outer("192.0.2.10", "198.51.100.1", vec![0x45; 60], 0, 0)).is_err());
        assert!(parse(outer("192.0.2.10", "198.51.100.1", vec![0x60; 12], 0, 0)).is_err());
        assert!(parse(outer("192.0.2.10", "198.51.100.1", Vec::new(), 0, 0)).is_err());
        assert!(parse(outer("2001:db8::1", "2001:db8::2", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 0)).is_err());
    }
}
