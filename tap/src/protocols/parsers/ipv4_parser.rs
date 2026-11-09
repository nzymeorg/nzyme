use std::net::Ipv4Addr;
use std::sync::Arc;

use anyhow::{bail, Result};
use byteorder::{BigEndian, ByteOrder};
use chrono::{DateTime, Utc};
use crate::helpers::network::to_ipv4_address;
use crate::wired::packets::{EthernetPacket, IPv4Packet};

pub fn parse_from_ethernet(packet: &Arc<EthernetPacket>) -> Result<IPv4Packet> {
    if packet.data.is_empty() {
        bail!("Empty payload.")
    }

    parse(
        &packet.data,
        Some(packet.source_mac.clone()),
        Some(packet.destination_mac.clone()),
        packet.timestamp
    )
}

// Parses a Raw IPv4 Packet that does not include any prepending headers like Ethernet.
pub fn parse_raw(packet: &[u8], timestamp: DateTime<Utc>) -> Result<IPv4Packet> {
    parse(packet, None, None, timestamp)
}

fn parse(packet: &[u8], 
         source_mac: Option<String>,
         destination_mac: Option<String>,
         timestamp: DateTime<Utc>) -> Result<IPv4Packet> {
    let ihl = (packet[0] & 0x0F) as usize;
    let header_length = ihl.checked_mul(4)
        .ok_or_else(|| anyhow::anyhow!("Header length overflow."))?;

    if packet.len() < header_length || packet.len() < 20 {
        bail!("Payload too short.");
    }

    // Total Length field.
    let total_length = BigEndian::read_u16(&packet[2..4]) as usize;
    if total_length < header_length || total_length > packet.len() {
        bail!("Invalid total length.");
    }

    // Type of Service. (DSCP + ECN)
    let ip_tos = packet[1];

    let identification = BigEndian::read_u16(&packet[4..6]);

    // Flags and Fragment Offset. (16 bits)
    let flags_frag = BigEndian::read_u16(&packet[6..8]);
    // Don't Fragment flag is 0x4000.
    let df = (flags_frag & 0x4000) != 0;
    let more_fragments = (flags_frag & 0x2000) != 0;
    // In units of eight bytes.
    let fragment_offset = flags_frag & 0x1FFF;

    let ttl = packet[8];
    let protocol = packet[9];
    let source_address = to_ipv4_address(&packet[12..16]);
    let destination_address = to_ipv4_address(&packet[16..20]);

    let payload = packet[header_length..total_length].to_vec();
    let size = packet.len();

    Ok(IPv4Packet {
        source_mac,
        destination_mac,
        header_length,
        total_length,
        source_address,
        destination_address,
        ttl,
        protocol,
        payload,
        size: size as u32,
        timestamp,
        ip_tos,
        df,
        more_fragments,
        fragment_offset,
        identification
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn header(flags_frag: u16, protocol: u8, payload: &[u8]) -> Vec<u8> {
        let total = 20 + payload.len();
        let mut p = vec![0x45, 0, (total >> 8) as u8, total as u8, 0x12, 0x34, (flags_frag >> 8) as u8, flags_frag as u8,
                         64, protocol, 0, 0, 10, 0, 0, 1, 10, 0, 0, 2];
        p.extend_from_slice(payload);
        p
    }

    #[test]
    fn parses_unfragmented_packet() {
        let p = parse_raw(&header(0x4000, 1, &[8, 0, 0, 0, 0, 1, 0, 1]), Utc::now()).unwrap();
        assert!(p.df);
        assert!(!p.more_fragments);
        assert_eq!(p.fragment_offset, 0);
        assert_eq!(p.identification, 0x1234);
        assert_eq!(p.protocol, 1);
        assert_eq!(p.payload.len(), 8);
    }

    #[test]
    fn parses_first_fragment() {
        let p = parse_raw(&header(0x2000, 17, &[0, 53, 0, 53, 0, 8, 0, 0]), Utc::now()).unwrap();
        assert!(!p.df);
        assert!(p.more_fragments);
        assert_eq!(p.fragment_offset, 0);
    }

    #[test]
    fn parses_trailing_fragment() {
        let p = parse_raw(&header(185, 1, &[0xAB, 0xCD, 0, 0, 0, 0, 0, 0]), Utc::now()).unwrap();
        assert!(!p.more_fragments);
        assert_eq!(p.fragment_offset, 185);
        assert_eq!(p.payload[0], 0xAB);
    }
}
