use std::net::{IpAddr, Ipv4Addr};
use std::sync::Arc;
use byteorder::{BigEndian, ByteOrder};
use crate::tracemark;
use crate::wired::packets::{Datagram, NameResolutionAnswer, NameResolutionPacket};
use crate::wired::types::NameResolutionProtocol;

const NBTNS_PORT: u16 = 137;
const TYPE_NB: u16 = 0x20;
const MAX_RECORDS: u16 = 16;

pub fn parse(udp: &Arc<Datagram>) -> Option<NameResolutionPacket> {
    if udp.source_port != NBTNS_PORT && udp.destination_port != NBTNS_PORT {
        return None;
    }

    let p = &udp.payload;
    if p.len() < 12 {
        tracemark!("NBT-NS");
        return None;
    }

    let flags = BigEndian::read_u16(&p[2..4]);
    let is_response = flags & 0x8000 != 0;
    let opcode = (flags >> 11) & 0x0F;
    let rcode = flags & 0x000F;

    // Only name queries. Registrations, releases, WACKs and refreshes are not resolution.
    if opcode != 0 {
        return None;
    }

    let qdcount = BigEndian::read_u16(&p[4..6]);
    let ancount = BigEndian::read_u16(&p[6..8]);
    if qdcount > MAX_RECORDS || ancount > MAX_RECORDS {
        tracemark!("NBT-NS");
        return None;
    }

    let mut cursor: usize = 12;
    let mut queries = Vec::new();
    for _ in 0..qdcount {
        let (consumed, name) = parse_name(&p[cursor..])?;
        cursor += consumed;

        if p.len() < cursor + 4 {
            tracemark!("NBT-NS");
            return None;
        }

        let qtype = BigEndian::read_u16(&p[cursor..cursor + 2]);
        cursor += 4;

        if qtype == TYPE_NB {
            if let Some(name) = name {
                queries.push(name);
            }
        }
    }

    let mut answers = Vec::new();
    if is_response && rcode == 0 {
        for _ in 0..ancount {
            if p.len() < cursor + 2 {
                tracemark!("NBT-NS");
                return None;
            }

            let (consumed, name) = if p[cursor] & 0xC0 == 0xC0 {
                let offset = (BigEndian::read_u16(&p[cursor..cursor + 2]) & 0x3FFF) as usize;
                if offset >= p.len() {
                    tracemark!("NBT-NS");
                    return None;
                }

                let (_, name) = parse_name(&p[offset..])?;
                (2, name)
            } else {
                parse_name(&p[cursor..])?
            };
            cursor += consumed;

            if p.len() < cursor + 10 {
                tracemark!("NBT-NS");
                return None;
            }

            let rtype = BigEndian::read_u16(&p[cursor..cursor + 2]);
            let rdlength = BigEndian::read_u16(&p[cursor + 8..cursor + 10]) as usize;
            cursor += 10;

            if p.len() < cursor + rdlength {
                tracemark!("NBT-NS");
                return None;
            }

            if rtype == TYPE_NB {
                if let Some(name) = &name {
                    // RDATA is a list of entries of two flag bytes followed by an IPv4 address.
                    let mut i = 0;
                    while i + 6 <= rdlength {
                        let a = &p[cursor + i + 2..cursor + i + 6];
                        answers.push(NameResolutionAnswer {
                            name: name.clone(),
                            address: IpAddr::V4(Ipv4Addr::new(a[0], a[1], a[2], a[3]))
                        });
                        i += 6;
                    }
                }
            }

            cursor += rdlength;
        }
    }

    if queries.is_empty() && answers.is_empty() {
        return None;
    }

    Some(NameResolutionPacket {
        protocol: NameResolutionProtocol::NbtNs,
        source_mac: udp.source_mac.clone(),
        destination_mac: udp.destination_mac.clone(),
        source_address: udp.source_address,
        destination_address: udp.destination_address,
        is_response,
        queries,
        answers,
        timestamp: udp.timestamp
    })
}

/*
 * NetBIOS first-level encoding: the 16 byte name is written as 32 characters, one per nibble, each 'A' plus the
 * nibble. Byte 16 of the decoded name is the suffix that says what kind of service the name belongs to. Scope
 * labels that follow are skipped. The name is None for the NBSTAT wildcard and for suffixes that are not a host
 * (workstation or file server), such as browser and domain names.
 */
fn parse_name(data: &[u8]) -> Option<(usize, Option<String>)> {
    if data.len() < 33 || data[0] != 32 {
        return None;
    }

    let encoded = &data[1..33];
    let mut decoded = [0u8; 16];
    for i in 0..16 {
        let hi = encoded[2 * i].checked_sub(b'A')?;
        let lo = encoded[2 * i + 1].checked_sub(b'A')?;
        if hi > 15 || lo > 15 {
            return None;
        }

        decoded[i] = (hi << 4) | lo;
    }

    let mut cursor: usize = 33;
    loop {
        if cursor >= data.len() {
            return None;
        }

        let label_length = data[cursor] as usize;
        cursor += 1;

        if label_length == 0 {
            break;
        }

        cursor += label_length;
    }

    let suffix = decoded[15];
    let name = String::from_utf8_lossy(&decoded[0..15]).trim_end().to_string();

    if name.is_empty() || name.starts_with('*') || (suffix != 0x00 && suffix != 0x20) {
        return Some((cursor, None));
    }

    Some((cursor, Some(name.to_lowercase())))
}

