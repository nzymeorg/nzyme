use std::net::{IpAddr, Ipv4Addr};
use anyhow::{bail, Result};
use byteorder::{BigEndian, ByteOrder};
use crate::wired::packets::{IcmpPacket, IcmpQuotedHeader, IPv4Packet};

const HEADER_LENGTH: usize = 8;

pub fn parse(ip4: IPv4Packet) -> Result<IcmpPacket> {
    if ip4.payload.len() < HEADER_LENGTH {
        bail!("Payload too short for ICMP packet.");
    }

    let icmp_type = ip4.payload[0];
    let icmp_code = ip4.payload[1];
    let rest = &ip4.payload[4..8];
    let body = &ip4.payload[HEADER_LENGTH..];

    let (identifier, sequence) = match icmp_type {
        0 | 8 | 13 | 14 | 15 | 16 | 17 | 18 => (
            Some(BigEndian::read_u16(&rest[0..2])),
            Some(BigEndian::read_u16(&rest[2..4]))
        ),
        _ => (None, None)
    };

    let redirect_gateway = if icmp_type == 5 {
        Some(IpAddr::V4(Ipv4Addr::new(rest[0], rest[1], rest[2], rest[3])))
    } else {
        None
    };

    let quoted = match icmp_type {
        3 | 4 | 5 | 11 | 12 => parse_quoted_header(body),
        _ => None
    };

    // Body + ICMP header + IPv4 header (20) + Ethernet (14).
    let size = (ip4.payload.len() + 34) as u32;

    Ok(IcmpPacket {
        source_mac: ip4.source_mac,
        destination_mac: ip4.destination_mac,
        source_address: ip4.source_address,
        destination_address: ip4.destination_address,
        ip_ttl: ip4.ttl,
        ip_tos: ip4.ip_tos,
        ip_df: ip4.df,
        icmp_type,
        icmp_code,
        identifier,
        sequence,
        redirect_gateway,
        quoted,
        payload_size: body.len() as u32,
        size,
        timestamp: ip4.timestamp
    })
}

// Returns None rather than an error for a malformed quote: the ICMP message itself is still valid.
fn parse_quoted_header(body: &[u8]) -> Option<IcmpQuotedHeader> {
    if body.len() < 20 {
        return None;
    }

    if body[0] >> 4 != 4 {
        return None;
    }

    let header_length = ((body[0] & 0x0F) as usize) * 4;
    if header_length < 20 || body.len() < header_length {
        return None;
    }

    let protocol = body[9];
    let ttl = body[8];
    let source_address = IpAddr::V4(Ipv4Addr::new(body[12], body[13], body[14], body[15]));
    let destination_address = IpAddr::V4(Ipv4Addr::new(body[16], body[17], body[18], body[19]));

    let transport = &body[header_length..];
    let (source_port, destination_port) = if (protocol == 6 || protocol == 17) && transport.len() >= 4 {
        (
            Some(BigEndian::read_u16(&transport[0..2])),
            Some(BigEndian::read_u16(&transport[2..4]))
        )
    } else {
        (None, None)
    };

    Some(IcmpQuotedHeader {
        protocol,
        source_address,
        destination_address,
        ttl,
        source_port,
        destination_port
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::Utc;

    fn ip4(payload: Vec<u8>) -> IPv4Packet {
        IPv4Packet {
            source_mac: None,
            destination_mac: None,
            header_length: 20,
            total_length: 20 + payload.len(),
            source_address: "10.0.0.1".parse().unwrap(),
            destination_address: "10.0.0.2".parse().unwrap(),
            ttl: 64,
            ip_tos: 0,
            df: false,
            protocol: 1,
            payload,
            size: 0,
            timestamp: Utc::now()
        }
    }

    #[test]
    fn parses_echo_request() {
        let mut payload = vec![8, 0, 0, 0, 0x12, 0x34, 0x00, 0x07];
        payload.extend_from_slice(&[0xAA; 56]);

        let p = parse(ip4(payload)).unwrap();
        assert_eq!(p.icmp_type, 8);
        assert_eq!(p.identifier, Some(0x1234));
        assert_eq!(p.sequence, Some(7));
        assert_eq!(p.payload_size, 56);
        assert!(p.quoted.is_none());
        assert!(!p.is_error());
    }

    #[test]
    fn parses_port_unreachable_with_quote() {
        let mut payload = vec![3, 3, 0, 0, 0, 0, 0, 0];
        // Quoted IPv4 header: UDP from 10.0.0.2:40000 to 10.0.0.9:4444.
        let mut quoted = vec![0x45, 0, 0, 0x1c, 0, 0, 0, 0, 0x40, 17, 0, 0, 10, 0, 0, 2, 10, 0, 0, 9];
        quoted.extend_from_slice(&[0x9C, 0x40, 0x11, 0x5C, 0, 8, 0, 0]);
        payload.extend(quoted);

        let p = parse(ip4(payload)).unwrap();
        assert!(p.is_error());
        let q = p.quoted.unwrap();
        assert_eq!(q.protocol, 17);
        assert_eq!(q.source_address.to_string(), "10.0.0.2");
        assert_eq!(q.destination_address.to_string(), "10.0.0.9");
        assert_eq!(q.source_port, Some(40000));
        assert_eq!(q.destination_port, Some(4444));
        assert_eq!(q.ttl, 64);
    }

    #[test]
    fn parses_redirect_gateway() {
        let mut payload = vec![5, 1, 0, 0, 10, 0, 0, 254];
        payload.extend_from_slice(&[0x45, 0, 0, 0x1c, 0, 0, 0, 0, 0x40, 6, 0, 0, 10, 0, 0, 2, 1, 1, 1, 1]);
        payload.extend_from_slice(&[0, 80, 0, 80, 0, 0, 0, 0]);

        let p = parse(ip4(payload)).unwrap();
        assert_eq!(p.redirect_gateway.map(|g| g.to_string()), Some("10.0.0.254".to_string()));
        assert!(p.quoted.is_some());
    }

    #[test]
    fn tolerates_short_quote() {
        let payload = vec![11, 0, 0, 0, 0, 0, 0, 0, 0x45, 0, 0];
        let p = parse(ip4(payload)).unwrap();
        assert!(p.quoted.is_none());
    }

    #[test]
    fn rejects_short_packet() {
        assert!(parse(ip4(vec![8, 0, 0])).is_err());
    }
}
