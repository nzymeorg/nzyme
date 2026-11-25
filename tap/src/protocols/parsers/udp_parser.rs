use std::collections::HashSet;
use std::sync::Mutex;
use anyhow::{Result, bail};
use byteorder::{BigEndian, ByteOrder};
use crate::protocols::parsers::l4_key::L4Key;
use crate::wired::packets::{Datagram, IpPacket};

#[allow(clippy::cast_possible_truncation)]
pub fn parse(ip: IpPacket) -> Result<Datagram> {

    if ip.payload.len() < 8 {
        bail!("Payload too short for UDP packet.");
    }

    let source_port = BigEndian::read_u16(&ip.payload[0..2]);
    let destination_port = BigEndian::read_u16(&ip.payload[2..4]);

    let session_key = L4Key::new(
        ip.source_address, source_port, ip.destination_address, destination_port
    );
    
    let payload = ip.payload[8..ip.payload.len()].to_vec();

    // This payload + this header (8) + IP header + ethernet (14)
    let size = (payload.len() + 8 + ip.header_length + 14) as u32;

    Ok(Datagram {
        source_mac: ip.source_mac,
        destination_mac: ip.destination_mac,
        source_address: ip.source_address,
        destination_address: ip.destination_address,
        session_key,
        source_port,
        destination_port,
        payload,
        size,
        timestamp: ip.timestamp,
        tags: Mutex::new(HashSet::new())
    })

}
