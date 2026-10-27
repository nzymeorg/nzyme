use std::{net::{Ipv4Addr, Ipv6Addr}, sync::Arc};
use crate::wired::packets::{DNSData, DNSPacket, Datagram};
use anyhow::{bail, Result};
use bitvec::{order::Msb0, view::BitView};
use byteorder::{BigEndian, ByteOrder};
use bitreader::BitReader;
use entropy::shannon_entropy;
use crate::helpers::network::is_ip_address;
use crate::tracemark;
use crate::wired::types::{DNSClass, DNSDataType, DNSType};

#[allow(clippy::cast_possible_truncation)]
pub fn parse(udp: &Arc<Datagram>) -> Option<DNSPacket> {
    if udp.payload.len() < 13 {
        tracemark!("Payload too short to hold a DNS request or response.");
        return None;
    }

    let transaction_id = BigEndian::read_u16(&udp.payload[0..2]);

    // We are currently only looking at first bit but may parse all flags in the future.
    let mut flags = BitReader::new(&udp.payload[2..4]);
    let dns_type = match flags.read_bool() {
        Ok(b) => {
            if b {
                DNSType::QueryResponse
            } else {
                DNSType::Query
            }
        },
        Err(e) => {
            tracemark!("Unexpected type flag: {}", e);
            return None;
        }
    };

    let response_code = match dns_type {
        DNSType::QueryResponse => Some(response_code_name(udp.payload[3] & 0x0F)),
        DNSType::Query => None
    };

    let question_count = BigEndian::read_u16(&udp.payload[4..6]);
    let answer_count = BigEndian::read_u16(&udp.payload[6..8]);

    // Some UDP payloads will reach here, but have excessively large question/answer counts.
    if question_count > 512 || answer_count > 512 {
        tracemark!("Too many questions or answers.");
        return None;
    }

    // Queries.
    let mut cursor: usize = 12;
    let queries = if question_count > 0 {
        let mut query_list = Vec::new();
        for _ in 0..question_count {
            if udp.payload.len() < cursor+5 {
                tracemark!("Payload too short to fit next question.");
                return None;
            }

            match parse_query_element(&udp.payload[cursor..udp.payload.len()]) {
                Ok((pos, data)) => {
                    cursor += pos;
                    query_list.push(data);
                },
                Err(e) => {
                    tracemark!("Could not parse DNS query data element: {}", e);
                    return None;
                }
            };
        }

        if query_list.len() as u16 != question_count {
            tracemark!("Question count does not match number of included questions.");
            return None;
        }
        
        Some(query_list)
    } else {
        None
    };

    // Responses.
    let responses = if answer_count > 0 {
        let mut response_list = Vec::new();
        for _ in 0..answer_count {
            if udp.payload.len() < cursor+5 {
                tracemark!("Payload too short to fit next answer.");
                return None;
            }

            match parse_answer_element(&udp.payload[cursor..udp.payload.len()], &udp.payload) {
                Ok((pos, data)) => {
                    cursor += pos;
                    response_list.push(data);
                },
                Err(e) => {
                    tracemark!("Could not parse DNS response data element: {}", e);
                    return None;
                }
            };
        }

        if response_list.len() as u16 != answer_count {
            tracemark!("Answer count does not match number of included answers.");
            return None;
        }

        Some(response_list)
    } else {
        None
    };

    if question_count == 0 && answer_count == 0 {
        return None;
    }

    let size = udp.payload.len() as u32;

    Some(DNSPacket {
        transaction_id: if transaction_id == 0 { None } else { Some(transaction_id) },
        source_mac: udp.source_mac.clone(),
        destination_mac: udp.destination_mac.clone(),
        source_address: udp.source_address,
        destination_address: udp.destination_address,
        source_port: udp.source_port,
        destination_port: udp.destination_port,
        dns_type,
        question_count,
        answer_count,
        queries,
        responses,
        response_code,
        size,
        timestamp: udp.timestamp
    })
}

// RFC 1035 and RFC 2136 response codes. Anything else is reported by number.
fn response_code_name(code: u8) -> String {
    match code {
        0 => "NOERROR".to_string(),
        1 => "FORMERR".to_string(),
        2 => "SERVFAIL".to_string(),
        3 => "NXDOMAIN".to_string(),
        4 => "NOTIMP".to_string(),
        5 => "REFUSED".to_string(),
        6 => "YXDOMAIN".to_string(),
        7 => "YXRRSET".to_string(),
        8 => "NXRRSET".to_string(),
        9 => "NOTAUTH".to_string(),
        10 => "NOTZONE".to_string(),
        other => format!("RCODE{}", other)
    }
}

fn is_pointer(mask: u8) -> bool {
    let mask = mask.view_bits::<Msb0>();
    *mask.get(0).unwrap() && *mask.get(1).unwrap()
}

fn parse_label(data: &[u8], full_packet: &[u8]) -> Result<(usize, String)> {
    let mut visited_pointer_offsets: Vec<u16> = vec![];

    if is_pointer(data[0]) {
        // Value is a pointer.
        if data.len() < 3 {
            bail!("Label pointer too short.");
        }

        let offset = BigEndian::read_u16(&[data[0] & 0b0011_1111, data[1]]);

        if offset as usize > full_packet.len() {
            bail!("Offset larger than packet size.");
        }

        return match parse_string(&full_packet[(offset as usize)..full_packet.len()], full_packet, &mut visited_pointer_offsets) {
            Ok((_, s)) => Ok((2, s)), // 2 is the pointer length
            Err(e) => bail!("Could not parse answer name: {}", e)
        };
    }

    visited_pointer_offsets.clear();

    parse_string(data, full_packet, &mut visited_pointer_offsets)
}

fn parse_string(data: &[u8], full_packet: &[u8], visited_pointer_offsets: &mut Vec<u16>) -> Result<(usize, String)> {
    let mut cursor: usize = 0;
    let mut chars = Vec::new();
    let mut section_length = 0;
    let mut initial = true;

    for b in data {
        cursor += 1;

        if *b == 0 {
            // Reached the end of the complete label.
            break;
        }
    
        if is_pointer(*b) {
            if cursor >= data.len() {
                bail!("Data too short to fit offset.")
            }
            let offset = BigEndian::read_u16(&[data[cursor-1] & 0b0011_1111, data[cursor]]);

            /*
             * We keep track of the pointers we already visited to avoid
             * infinite recursion, especially in payloads that made it this
             * far but are not actually valid DNS.
             */
            if visited_pointer_offsets.contains(&offset) {
               bail!("Recursive DNS pointer. Skipping.");
            }

            visited_pointer_offsets.push(offset);

            if full_packet.len() < offset as usize {
                bail!("Offset <{}> does not match provided full packet length <{}>.", offset as usize, full_packet.len());
            }

            let (_,res) = match parse_string(&full_packet[(offset as usize)..full_packet.len()], full_packet, visited_pointer_offsets) {
                Ok((c,s)) => (c,s),
                Err(e) => bail!("Recursive pointer parsing failed: {}", e)
            };

            let prev = String::from_utf8_lossy(&chars).to_string();
            let full = prev + "." + res.as_str();
            cursor += 2; // The pointer size.

            return Ok((cursor, full));
        }

        if section_length == 0 {
            if initial {
                initial = false;
            } else {
                // A new label section begins.
                chars.push(46);
            }

            section_length = *b;
            continue;
        }

        chars.push(*b);
        section_length -= 1;
    }

    Ok((cursor, String::from_utf8_lossy(&chars).to_string().to_lowercase()))
}

fn parse_query_element(data: &[u8]) -> Result<(usize, DNSData)> {
    let mut visited_pointer_offsets: Vec<u16> = vec![];
    let (mut cursor, name) = match parse_string(&data[0..data.len()], data, &mut visited_pointer_offsets) {
        Ok((c,s)) => (c, s),
        Err(e) => bail!("Could not parse query name: {}", e)
    };

    let name_etld = if !is_ip_address(&name) {
        psl::domain_str(&name).map(|name| name.to_string())
    } else {
        None
    };

    if data.len() < cursor+4 {
        bail!("DNS data element too short.");
    }

    let dns_type_num = BigEndian::read_u16(&data[cursor..cursor+2]);
    let Ok(dns_type) = DNSDataType::try_from(dns_type_num) else {
        bail!("Unknown DNS type <{}>.", dns_type_num)
    };
    cursor += 2;

    // mDNS uses the top bit of the class as unicast-response (queries) and cache-flush (answers) flag.
    let class_num = BigEndian::read_u16(&data[cursor..cursor+2]) & 0x7FFF;
    let Ok(class) = DNSClass::try_from(class_num) else {
        bail!("Unknown DNS class <{}>.", class_num)
    };
    cursor += 2;

    let entropy = shannon_entropy(name.as_bytes());

    Ok((cursor, DNSData {
       name,
       name_etld,
       dns_type,
       class,
       entropy: Some(entropy),
       value: None,
       value_etld: None,
       ttl: None
    }))
}

#[allow(clippy::too_many_lines)]
fn parse_answer_element(data: &[u8], full_packet: &[u8]) -> Result<(usize, DNSData)> {
    let (mut cursor, name) = match parse_label(data, full_packet) {
        Ok(x) => x,
        Err(e) => bail!("Could not parse answer name: {}", e)
    };

    let name_etld = if !is_ip_address(&name) {
        psl::domain_str(&name).map(|name| name.to_string())
    } else {
        None
    };

    if data.len() < cursor+11 {
       bail!("DNS answer element too short"); 
    }

    let dns_type_num = BigEndian::read_u16(&data[cursor..cursor+2]);
    let Ok(dns_type) = DNSDataType::try_from(dns_type_num) else {
        bail!("Unknown DNS type <{}>.", dns_type_num)
    };
    cursor += 2;
   
    let class_num = BigEndian::read_u16(&data[cursor..cursor+2]) & 0x7FFF;
    let Ok(class) = DNSClass::try_from(class_num) else {
        bail!("Unknown DNS class <{}>.", class_num)
    };
    cursor += 2;

    let ttl = BigEndian::read_u32(&data[cursor..cursor+4]);
    cursor += 4;

    let data_length = BigEndian::read_u16(&data[cursor..cursor+2]) as usize;
    cursor += 2;
   
    if data.len() < cursor+data_length {
        bail!("DNS answer element too short to fit announced data length.");
    }

    // Not collapsing branches here because logic might become more specific in the future.
    let dns_type_has_entropy;
    let mut visited_pointer_offsets: Vec<u16> = vec![];
    let value = match dns_type {
        DNSDataType::A => {
            dns_type_has_entropy = false;
            match parse_ipv4(&data[cursor..cursor+data_length]) {
                Ok(addr) => {
                    Some(addr.to_lowercase())
                },
                Err(e) => bail!("Could not parse A value: {}", e) 
            }
        },
        DNSDataType::CNAME => {
            dns_type_has_entropy = true;
            match parse_string(&data[cursor..cursor+data_length], full_packet, &mut visited_pointer_offsets) {
                Ok((_, cname)) => {
                    Some(cname.to_lowercase())
                },
                Err(e) => bail!("Could not parse CNAME value: {}", e)
            }
        },
        DNSDataType::AAAA => {
            dns_type_has_entropy = false;
            match parse_ipv6(&data[cursor..cursor+data_length]) {
                Ok(addr) => {
                    Some(addr.to_lowercase())
                },
                Err(e) => bail!("Could not parse AAAA value: {}", e) 
            }
        },
        DNSDataType::NS => {
            dns_type_has_entropy = true;
            match parse_string(&data[cursor..cursor+data_length], full_packet, &mut visited_pointer_offsets) {
                Ok((_, ns)) => {
                    Some(ns.to_lowercase())
                },
                Err(e) => bail!("Could not parse NS value: {}", e)
            }
        },
        DNSDataType::PTR => {
            dns_type_has_entropy = true;
            match parse_string(&data[cursor..cursor+data_length], full_packet, &mut visited_pointer_offsets) {
                Ok((_, ptr)) => {
                    Some(ptr.to_lowercase())
                },
                Err(e) => bail!("Could not parse PTR value: {}", e)
            }
        },
        DNSDataType::MX => {
            dns_type_has_entropy = true;
            if data_length < 3 {
                bail!("MX data is too short.");
            }

            // We are skipping two bytes because we don't parse the MX priority.
            match parse_string(&data[cursor+2..cursor+data_length], full_packet, &mut visited_pointer_offsets) {
                Ok((_, mx)) => {
                    Some(mx.to_lowercase())
                },
                Err(e) => bail!("Could not parse MX value: {}", e)
            }
        },
        DNSDataType::TXT => {
            dns_type_has_entropy = true;
            match parse_string(&data[cursor..cursor+data_length], full_packet, &mut visited_pointer_offsets) {
                Ok((_, txt)) => {
                    Some(txt.to_lowercase())
                },
                Err(e) => bail!("Could not parse TXT value: {}", e)
            }
        },
        _ => {
            dns_type_has_entropy = false;
            None
        }
    };

    let (entropy, value_etld) = match value.clone() {
        Some(v) => {
            let entropy = if dns_type_has_entropy {
                Some(shannon_entropy(v.as_bytes()))
            } else {
                None
            };

            let etld = if !is_ip_address(&v) {
                psl::domain_str(&v).map(|d| d.to_string())
            } else {
                None
            };

            (entropy, etld)
        },
        None => (None, None)
    };

    cursor += data_length;

    Ok((cursor, DNSData {
        name,
        name_etld,
        class,
        dns_type,
        value,
        value_etld,
        entropy,
        ttl: Some(ttl)
    }))
}

fn parse_ipv4(data: &[u8]) -> Result<String> {
    if data.len() != 4 {
        bail!("Invalid IPv4 address length.");
    }

    Ok(Ipv4Addr::new(data[0], data[1], data[2], data[3]).to_string())
}

fn parse_ipv6(data: &[u8]) -> Result<String> {
    if data.len() != 16 {
        bail!("Invalid IPv4 address length.");
    }
    
    Ok(Ipv6Addr::new(
            BigEndian::read_u16(&[data[0], data[1]]),
            BigEndian::read_u16(&[data[2], data[3]]),
            BigEndian::read_u16(&[data[4], data[5]]),
            BigEndian::read_u16(&[data[6], data[7]]),
            BigEndian::read_u16(&[data[8], data[9]]),
            BigEndian::read_u16(&[data[10], data[11]]),
            BigEndian::read_u16(&[data[12], data[13]]),
            BigEndian::read_u16(&[data[14], data[15]])
    ).to_string())
}
