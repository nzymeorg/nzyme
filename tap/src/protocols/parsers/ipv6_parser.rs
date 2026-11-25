use std::net::Ipv6Addr;
use std::sync::Arc;

use anyhow::{bail, Result};
use byteorder::{BigEndian, ByteOrder};
use chrono::{DateTime, Utc};
use crate::helpers::network::to_ipv6_address;
use crate::wired::packets::{EthernetPacket, IpPacket};

pub const HEADER_LENGTH: usize = 40;

pub const HOP_BY_HOP: u8 = 0;
pub const ROUTING: u8 = 43;
pub const FRAGMENT: u8 = 44;
const AUTHENTICATION: u8 = 51;
pub const DESTINATION_OPTIONS: u8 = 60;
const MOBILITY: u8 = 135;
const HOST_IDENTITY: u8 = 139;
const SHIM6: u8 = 140;

pub fn parse_from_ethernet(packet: &Arc<EthernetPacket>) -> Result<IpPacket> {
    parse(
        &packet.data,
        Some(packet.source_mac.clone()),
        Some(packet.destination_mac.clone()),
        packet.timestamp
    )
}

pub struct FixedHeader {
    pub payload_length: usize,
    pub next_header: u8,
    pub hop_limit: u8,
    pub source_address: Ipv6Addr,
    pub destination_address: Ipv6Addr
}

pub fn read_fixed_header(packet: &[u8]) -> Option<FixedHeader> {
    if packet.len() < HEADER_LENGTH || packet[0] >> 4 != 6 {
        return None;
    }

    Some(FixedHeader {
        payload_length: BigEndian::read_u16(&packet[4..6]) as usize,
        next_header: packet[6],
        hop_limit: packet[7],
        source_address: Ipv6Addr::from(<[u8; 16]>::try_from(&packet[8..24]).ok()?),
        destination_address: Ipv6Addr::from(<[u8; 16]>::try_from(&packet[24..40]).ok()?)
    })
}

pub fn parse_raw(packet: &[u8], timestamp: DateTime<Utc>) -> Result<IpPacket> {
    parse(packet, None, None, timestamp)
}

fn parse(packet: &[u8],
         source_mac: Option<String>,
         destination_mac: Option<String>,
         timestamp: DateTime<Utc>) -> Result<IpPacket> {
    if packet.len() < HEADER_LENGTH {
        bail!("Payload too short.");
    }

    if packet[0] >> 4 != 6 {
        bail!("Not an IPv6 packet.");
    }

    let traffic_class = ((packet[0] & 0x0F) << 4) | (packet[1] >> 4);
    let payload_length = BigEndian::read_u16(&packet[4..6]) as usize;
    let hop_limit = packet[7];
    let source_address = to_ipv6_address(&packet[8..24]);
    let destination_address = to_ipv6_address(&packet[24..40]);

    // The capture can be longer than the packet: Ethernet pads short frames.
    let total_length = HEADER_LENGTH + payload_length;
    if total_length > packet.len() {
        bail!("Invalid payload length.");
    }

    let mut next_header = packet[6];
    let mut cursor = HEADER_LENGTH;
    let mut more_fragments = false;
    let mut fragment_offset: u16 = 0;

    loop {
        let extension_length = match next_header {
            HOP_BY_HOP | ROUTING | DESTINATION_OPTIONS | MOBILITY | HOST_IDENTITY | SHIM6 => {
                if cursor + 2 > total_length {
                    bail!("Truncated extension header.");
                }

                // In units of eight bytes, not counting the first eight.
                (packet[cursor + 1] as usize + 1) * 8
            },
            AUTHENTICATION => {
                if cursor + 2 > total_length {
                    bail!("Truncated extension header.");
                }

                // In units of four bytes, minus two.
                (packet[cursor + 1] as usize + 2) * 4
            },
            FRAGMENT => 8,
            _ => break
        };

        if cursor + extension_length > total_length {
            bail!("Truncated extension header.");
        }

        if next_header == FRAGMENT {
            let offset_and_flags = BigEndian::read_u16(&packet[cursor + 2..cursor + 4]);
            fragment_offset = offset_and_flags >> 3;
            more_fragments = offset_and_flags & 0x0001 != 0;
        }

        next_header = packet[cursor];
        cursor += extension_length;

        // What follows the fragment header of a later fragment is data, not another header.
        if fragment_offset > 0 {
            break;
        }
    }

    Ok(IpPacket {
        source_mac,
        destination_mac,
        header_length: cursor,
        total_length,
        source_address,
        destination_address,
        ttl: hop_limit,
        protocol: next_header,
        payload: packet[cursor..total_length].to_vec(),
        size: packet.len() as u32,
        timestamp,
        ip_tos: traffic_class,
        df: None,
        more_fragments,
        fragment_offset,
        identification: None
    })
}

#[cfg(test)]
mod tests {
    use std::net::IpAddr;
    use crate::protocols::parsers::tcp::tcp_parser;
    use crate::protocols::parsers::udp_parser;
    use super::*;

    fn header(next_header: u8, payload: &[u8]) -> Vec<u8> {
        let mut p = vec![0x6A, 0xB0, 0, 0, (payload.len() >> 8) as u8, payload.len() as u8, next_header, 57];
        p.extend_from_slice(&"2001:db8::1".parse::<std::net::Ipv6Addr>().unwrap().octets());
        p.extend_from_slice(&"2001:db8::2".parse::<std::net::Ipv6Addr>().unwrap().octets());
        p.extend_from_slice(payload);
        p
    }

    #[test]
    fn parses_packet_without_extension_headers() {
        let p = parse_raw(&header(17, &[0, 53, 0, 53, 0, 8, 0, 0]), Utc::now()).unwrap();

        assert_eq!(p.source_address, "2001:db8::1".parse::<IpAddr>().unwrap());
        assert_eq!(p.destination_address, "2001:db8::2".parse::<IpAddr>().unwrap());
        assert_eq!(p.protocol, 17);
        assert_eq!(p.ttl, 57);
        assert_eq!(p.ip_tos, 0xAB);
        assert_eq!(p.header_length, 40);
        assert_eq!(p.total_length, 48);
        assert_eq!(p.payload.len(), 8);
        assert_eq!(p.df, None);
        assert_eq!(p.identification, None);
        assert_eq!(p.fragment_offset, 0);
    }

    #[test]
    fn ignores_ethernet_padding() {
        let mut packet = header(17, &[0, 53, 0, 53, 0, 8, 0, 0]);
        packet.extend_from_slice(&[0; 6]);

        let p = parse_raw(&packet, Utc::now()).unwrap();
        assert_eq!(p.payload, vec![0, 53, 0, 53, 0, 8, 0, 0]);
    }

    #[test]
    fn walks_extension_headers() {
        // Hop-by-hop (8 bytes), destination options (16 bytes), then TCP.
        let mut payload = vec![60, 0, 1, 4, 0, 0, 0, 0];
        payload.extend_from_slice(&[6, 1, 1, 12, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
        payload.extend_from_slice(&[0xAA, 0xBB]);

        let p = parse_raw(&header(0, &payload), Utc::now()).unwrap();
        assert_eq!(p.protocol, 6);
        assert_eq!(p.header_length, 64);
        assert_eq!(p.payload, vec![0xAA, 0xBB]);
    }

    #[test]
    fn walks_authentication_header() {
        // Length 4 means (4 + 2) * 4 = 24 bytes.
        let mut payload = vec![17, 4];
        payload.extend_from_slice(&[0; 22]);
        payload.extend_from_slice(&[0xCC]);

        let p = parse_raw(&header(51, &payload), Utc::now()).unwrap();
        assert_eq!(p.protocol, 17);
        assert_eq!(p.header_length, 64);
        assert_eq!(p.payload, vec![0xCC]);
    }

    #[test]
    fn parses_first_fragment() {
        let p = parse_raw(&header(44, &[17, 0, 0x00, 0x01, 0, 0, 0, 1, 0, 53, 0, 53]), Utc::now()).unwrap();
        assert!(p.more_fragments);
        assert_eq!(p.fragment_offset, 0);
        assert_eq!(p.protocol, 17);
        assert_eq!(p.payload, vec![0, 53, 0, 53]);
    }

    #[test]
    fn parses_trailing_fragment() {
        // Offset 185, no more fragments. The data starts with what looks like a hop-by-hop header.
        let p = parse_raw(&header(44, &[0, 0, 0x05, 0xC8, 0, 0, 0, 1, 0xAB, 0xCD]), Utc::now()).unwrap();
        assert!(!p.more_fragments);
        assert_eq!(p.fragment_offset, 185);
        assert_eq!(p.payload, vec![0xAB, 0xCD]);
    }

    #[test]
    fn rejects_truncated_packets() {
        assert!(parse_raw(&[0x60; 39], Utc::now()).is_err());

        let mut short = header(17, &[0, 53, 0, 53, 0, 8, 0, 0]);
        short.truncate(44);
        assert!(parse_raw(&short, Utc::now()).is_err());

        assert!(parse_raw(&header(0, &[6, 9, 0, 0, 0, 0, 0, 0]), Utc::now()).is_err());
        assert!(parse_raw(&header(0, &[6]), Utc::now()).is_err());
    }

    #[test]
    fn rejects_other_versions() {
        let mut packet = header(17, &[0; 8]);
        packet[0] = 0x45;
        assert!(parse_raw(&packet, Utc::now()).is_err());
    }

    #[test]
    fn transport_parsers_take_ipv6_packets() {
        let ip = parse_raw(&header(17, &[0x14, 0xE9, 0x14, 0xE9, 0, 12, 0, 0, 1, 2, 3, 4]), Utc::now()).unwrap();
        let datagram = udp_parser::parse(ip).unwrap();
        assert_eq!(datagram.source_port, 5353);
        assert_eq!(datagram.payload, vec![1, 2, 3, 4]);
        assert_eq!(datagram.size, 4 + 8 + 40 + 14);

        let mut payload = vec![6, 0, 1, 4, 0, 0, 0, 0];
        payload.extend_from_slice(&[0xC0, 0x00, 0x01, 0xBB, 0, 0, 0, 1, 0, 0, 0, 0, 0x50, 0x02, 0xFF, 0xFF, 0, 0, 0, 0]);
        let ip = parse_raw(&header(60, &payload), Utc::now()).unwrap();
        let segment = tcp_parser::parse(ip).unwrap();
        assert_eq!(segment.destination_port, 443);
        assert!(segment.flags.syn);
        assert_eq!(segment.ip_ttl, 57);
        assert_eq!(segment.ip_df, None);
        assert_eq!(segment.ip_id, None);
        assert_eq!(segment.size, 20 + 48 + 14);
        assert_eq!(segment.source_address, "2001:db8::1".parse::<IpAddr>().unwrap());
    }

    #[test]
    fn reports_mapped_addresses_as_ipv4() {
        let mut packet = header(17, &[0; 8]);
        packet[8..24].copy_from_slice(&"::ffff:192.0.2.7".parse::<std::net::Ipv6Addr>().unwrap().octets());

        let p = parse_raw(&packet, Utc::now()).unwrap();
        assert_eq!(p.source_address, "192.0.2.7".parse::<IpAddr>().unwrap());
    }
}
