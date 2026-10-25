use anyhow::{Result, bail};
use byteorder::{BigEndian, ByteOrder};
use chrono::Utc;
use crate::wired::packets::{IPv4Packet, TcpFlags, TcpSegment};
use crate::protocols::parsers::l4_key::L4Key;

#[allow(clippy::cast_possible_truncation)]
pub fn parse(packet: IPv4Packet) -> Result<TcpSegment> {
    if packet.payload.len() < 20 {
        bail!("Payload too short for TCP packet.");
    }

    let source_port = BigEndian::read_u16(&packet.payload[0..2]);
    let destination_port = BigEndian::read_u16(&packet.payload[2..4]);

    let session_key = L4Key::new(
        packet.source_address, source_port, packet.destination_address, destination_port
    );

    let sequence_number = BigEndian::read_u32(&packet.payload[4..8]);
    let ack_number = BigEndian::read_u32(&packet.payload[8..12]);

    let header_length: usize = ((packet.payload[12] >> 4)*4) as usize;

    // Data_offset must be at least 5 words (20 bytes), and no more than what we actually have.
    if header_length < 20 || header_length > packet.payload.len() {
        bail!("Invalid TCP header length: <{}> (payload is <{}> bytes)",
            header_length,packet.payload.len());
    }
    
    let flags_byte = packet.payload[13];
    let ack = flags_byte & 0x10 != 0;
    let reset = flags_byte & 0x04 != 0;
    let syn = flags_byte & 0x02 != 0;
    let fin = flags_byte & 0x01 != 0;
    let psh = flags_byte & 0x08 != 0;
    let urg = flags_byte & 0x20 != 0;
    let ece = flags_byte & 0x40 != 0;
    let cwr = flags_byte & 0x80 != 0;

    let flags = TcpFlags { ack, reset, syn, fin, psh, urg, ece, cwr };

    let window_size = BigEndian::read_u16(&packet.payload[14..16]);

    let mut cursor = 20;
    let mut options = Vec::new();
    let mut maximum_segment_size: Option<u16> = None;
    let mut window_scale_multiplier: Option<u8> = None;

    while cursor < header_length {
        let kind = packet.payload[cursor];
        options.push(kind);

        match kind {
            0 => {
                // End of Option List.
                break;
            }
            1 => {
                // No-Operation.
                cursor += 1;
            }
            _ => {
                // Need at least 2 bytes for kind + length.
                if cursor + 1 >= header_length {
                    break;
                }
                
                let length = packet.payload[cursor + 1] as usize;
                
                // Length must be >= 2 and not run past the header.
                if length < 2 || cursor + length > header_length {
                    break;
                }

                match kind {
                    2 if length == 4 => {
                        // Maximum Segment Size: 2 bytes after kind+len.
                        let start = cursor + 2;
                        maximum_segment_size = Some(
                            BigEndian::read_u16(&packet.payload[start..start + 2])
                        );
                    }
                    3 if length == 3 => {
                        // Window Scale: 1 byte after kind+len.
                        let shift = packet.payload[cursor + 2];
                        window_scale_multiplier = Some(shift);
                    }
                    _ => {
                        // Other options - NOOP.
                    }
                }

                // Advance past this entire option
                cursor += length;
            }
        }
    }
    
    if packet.payload.len() < header_length {
        bail!("Payload shorter than header length.");
    }

    if packet.total_length < packet.header_length + header_length {
        bail!("Total length less than combined header lengths.");
    }

    let payload = packet.payload[header_length..].to_vec();

    // This payload + this header + IPv4 (20) + ethernet (14)
    let size = (payload.len() + header_length + 34) as u32;

    Ok(TcpSegment {
        ip_ttl: packet.ttl,
        ip_tos: packet.ip_tos,
        ip_df: packet.df,
        source_mac: packet.source_mac,
        destination_mac: packet.destination_mac,
        source_address: packet.source_address,
        destination_address: packet.destination_address,
        source_port,
        destination_port,
        session_key,
        sequence_number,
        ack_number,
        flags,
        payload,
        window_size,
        maximum_segment_size,
        window_scale_multiplier,
        options,
        size, 
        timestamp: Utc::now() 
    })
}