use std::sync::Arc;
use byteorder::{BigEndian, ByteOrder};
use crate::helpers::network::{parse_domain_names, to_ipv6_address, to_mac_address_string};
use crate::wired::packets::{Datagram, Dhcpv6Address, Dhcpv6MessageType, Dhcpv6Packet, Dhcpv6Prefix};

const CLIENT_PORT: u16 = 546;
const SERVER_PORT: u16 = 547;

const RELAY_FORWARD: u8 = 12;
const RELAY_REPLY: u8 = 13;
const RELAY_HEADER_LENGTH: usize = 34;
const MAX_RELAY_DEPTH: usize = 4;

const OPTION_CLIENT_ID: u16 = 1;
const OPTION_SERVER_ID: u16 = 2;
const OPTION_IA_NA: u16 = 3;
const OPTION_IA_TA: u16 = 4;
const OPTION_IA_ADDRESS: u16 = 5;
const OPTION_REQUEST_OPTION: u16 = 6;
const OPTION_PREFERENCE: u16 = 7;
const OPTION_RELAY_MESSAGE: u16 = 9;
const OPTION_STATUS_CODE: u16 = 13;
const OPTION_RAPID_COMMIT: u16 = 14;
const OPTION_VENDOR_CLASS: u16 = 16;
const OPTION_DNS_SERVERS: u16 = 23;
const OPTION_DOMAIN_LIST: u16 = 24;
const OPTION_IA_PD: u16 = 25;
const OPTION_IA_PREFIX: u16 = 26;
const OPTION_CLIENT_FQDN: u16 = 39;
const OPTION_CLIENT_LINK_LAYER_ADDRESS: u16 = 79;

const MAX_OPTIONS: usize = 64;
const MAX_ADDRESSES: usize = 16;
const MAX_DNS_SERVERS: usize = 16;
const MAX_DOMAINS: usize = 16;
const MAX_NAME_LENGTH: usize = 255;
const MAX_DUID_LENGTH: usize = 130;
const MAX_VENDOR_CLASS_LENGTH: usize = 255;

pub fn parse(udp: &Arc<Datagram>) -> Option<Dhcpv6Packet> {
    if !udp.source_address.is_ipv6() {
        return None;
    }

    let ports = (udp.source_port, udp.destination_port);
    if !matches!(ports, (CLIENT_PORT, SERVER_PORT) | (SERVER_PORT, CLIENT_PORT) | (SERVER_PORT, SERVER_PORT)) {
        return None;
    }

    let (message, relayed, relay_client_link_layer_address) = unwrap_relays(&udp.payload)?;
    if message.len() < 4 {
        return None;
    }

    let message_type = Dhcpv6MessageType::from_code(message[0])?;
    let transaction_id = BigEndian::read_u32(&[0, message[1], message[2], message[3]]);

    let mut packet = Dhcpv6Packet {
        source_mac: udp.source_mac.clone(),
        destination_mac: udp.destination_mac.clone(),
        source_address: udp.source_address,
        destination_address: udp.destination_address,
        message_type,
        transaction_id,
        relayed,
        client_duid: None,
        server_duid: None,
        client_link_layer_address: relay_client_link_layer_address,
        addresses: Vec::new(),
        prefixes: Vec::new(),
        options: Vec::new(),
        option_request_list: Vec::new(),
        vendor_class: None,
        fqdn: None,
        dns_servers: Vec::new(),
        domain_search_list: Vec::new(),
        preference: None,
        rapid_commit: false,
        status_code: None,
        size: udp.size,
        timestamp: udp.timestamp
    };

    for (code, data) in options(&message[4..]) {
        packet.options.push(code);

        match code {
            OPTION_CLIENT_ID => {
                packet.client_duid = duid(data);
                if packet.client_link_layer_address.is_none() {
                    packet.client_link_layer_address = duid_link_layer_address(data);
                }
            },
            OPTION_SERVER_ID => packet.server_duid = duid(data),
            OPTION_IA_NA if data.len() >= 12 => identity_association(&data[12..], &mut packet),
            OPTION_IA_TA if data.len() >= 4 => identity_association(&data[4..], &mut packet),
            OPTION_IA_PD if data.len() >= 12 => identity_association(&data[12..], &mut packet),
            OPTION_REQUEST_OPTION => {
                packet.option_request_list = data.chunks_exact(2).take(MAX_OPTIONS).map(BigEndian::read_u16).collect();
            },
            OPTION_PREFERENCE if data.len() == 1 => packet.preference = Some(data[0]),
            OPTION_STATUS_CODE if data.len() >= 2 => packet.status_code = Some(BigEndian::read_u16(&data[0..2])),
            OPTION_RAPID_COMMIT => packet.rapid_commit = true,
            OPTION_VENDOR_CLASS if data.len() >= 4 => packet.vendor_class = Some(vendor_class(data)),
            OPTION_DNS_SERVERS => {
                packet.dns_servers = data.chunks_exact(16).take(MAX_DNS_SERVERS).map(to_ipv6_address).collect();
            },
            OPTION_DOMAIN_LIST => {
                packet.domain_search_list = parse_domain_names(data).into_iter().take(MAX_DOMAINS).collect();
            },
            OPTION_CLIENT_FQDN if data.len() > 1 => {
                // A client may send a name that is not fully qualified, which has no terminating zero.
                let mut name = data[1..].to_vec();
                name.push(0);
                packet.fqdn = parse_domain_names(&name).into_iter().next().filter(|n| n.len() <= MAX_NAME_LENGTH);
            },
            _ => {}
        }
    }

    Some(packet)
}

// Returns the innermost message, if it came through a relay, and the client's MAC address if a relay named it.
fn unwrap_relays(payload: &[u8]) -> Option<(&[u8], bool, Option<String>)> {
    let mut message = payload;
    let mut relayed = false;
    let mut client_link_layer_address = None;

    for _ in 0..=MAX_RELAY_DEPTH {
        let message_type = *message.first()?;
        if message_type != RELAY_FORWARD && message_type != RELAY_REPLY {
            return Some((message, relayed, client_link_layer_address));
        }

        if message.len() < RELAY_HEADER_LENGTH {
            return None;
        }

        relayed = true;
        let mut inner = None;
        for (code, data) in options(&message[RELAY_HEADER_LENGTH..]) {
            match code {
                OPTION_RELAY_MESSAGE => inner = Some(data),
                // Hardware type, then the address.
                OPTION_CLIENT_LINK_LAYER_ADDRESS if data.len() == 8 && BigEndian::read_u16(&data[0..2]) == 1 => {
                    client_link_layer_address = Some(to_mac_address_string(&data[2..8]));
                },
                _ => {}
            }
        }

        message = inner?;
    }

    None
}

// Stops at the first option that runs past the end and keeps what came before it.
fn options(data: &[u8]) -> Vec<(u16, &[u8])> {
    let mut options = Vec::new();
    let mut cursor = 0;

    while cursor + 4 <= data.len() && options.len() < MAX_OPTIONS {
        let code = BigEndian::read_u16(&data[cursor..cursor + 2]);
        let length = BigEndian::read_u16(&data[cursor + 2..cursor + 4]) as usize;
        cursor += 4;

        if cursor + length > data.len() {
            break;
        }

        options.push((code, &data[cursor..cursor + length]));
        cursor += length;
    }

    options
}

fn identity_association(data: &[u8], packet: &mut Dhcpv6Packet) {
    for (code, option) in options(data) {
        match code {
            OPTION_IA_ADDRESS if option.len() >= 24 && packet.addresses.len() < MAX_ADDRESSES => {
                packet.addresses.push(Dhcpv6Address {
                    address: to_ipv6_address(&option[0..16]),
                    preferred_lifetime: BigEndian::read_u32(&option[16..20]),
                    valid_lifetime: BigEndian::read_u32(&option[20..24])
                });
            },
            OPTION_IA_PREFIX if option.len() >= 25 && option[8] <= 128 && packet.prefixes.len() < MAX_ADDRESSES => {
                packet.prefixes.push(Dhcpv6Prefix {
                    prefix: to_ipv6_address(&option[9..25]),
                    prefix_length: option[8],
                    preferred_lifetime: BigEndian::read_u32(&option[0..4]),
                    valid_lifetime: BigEndian::read_u32(&option[4..8])
                });
            },
            OPTION_STATUS_CODE if option.len() >= 2 && packet.status_code.is_none() => {
                packet.status_code = Some(BigEndian::read_u16(&option[0..2]));
            },
            _ => {}
        }
    }
}

fn duid(data: &[u8]) -> Option<String> {
    if data.is_empty() || data.len() > MAX_DUID_LENGTH {
        return None;
    }

    Some(data.iter().map(|b| format!("{:02x}", b)).collect())
}

// DUID-LLT (type 1) and DUID-LL (type 3) end in the link-layer address the client had when it made the DUID.
fn duid_link_layer_address(data: &[u8]) -> Option<String> {
    if data.len() < 4 || BigEndian::read_u16(&data[2..4]) != 1 {
        return None;
    }

    let address = match BigEndian::read_u16(&data[0..2]) {
        1 if data.len() == 14 => &data[8..14],
        3 if data.len() == 10 => &data[4..10],
        _ => return None
    };

    Some(to_mac_address_string(address))
}

// The enterprise number, then the class strings: "311:MSFT 5.0".
fn vendor_class(data: &[u8]) -> String {
    let enterprise = BigEndian::read_u32(&data[0..4]);
    let mut classes: Vec<String> = Vec::new();
    let mut cursor = 4;

    while cursor + 2 <= data.len() {
        let length = BigEndian::read_u16(&data[cursor..cursor + 2]) as usize;
        cursor += 2;

        if cursor + length > data.len() {
            break;
        }

        classes.push(data[cursor..cursor + length].iter()
            .map(|b| if b.is_ascii_graphic() || *b == b' ' { *b as char } else { '.' })
            .collect());
        cursor += length;
    }

    let mut class = format!("{}:{}", enterprise, classes.join(","));
    class.truncate(MAX_VENDOR_CLASS_LENGTH);

    class
}

#[cfg(test)]
mod tests {
    use std::collections::HashSet;
    use std::net::{IpAddr, Ipv6Addr};
    use std::sync::Mutex;
    use chrono::Utc;
    use crate::protocols::parsers::l4_key::L4Key;
    use super::*;

    const CLIENT_DUID: [u8; 14] = [0, 1, 0, 1, 0x2a, 0x2b, 0x2c, 0x2d, 0xAA, 0xAA, 0xAA, 0xAA, 0xAA, 0x01];

    fn option(code: u16, data: &[u8]) -> Vec<u8> {
        let mut option = vec![(code >> 8) as u8, code as u8, (data.len() >> 8) as u8, data.len() as u8];
        option.extend_from_slice(data);
        option
    }

    fn datagram(source_port: u16, destination_port: u16, payload: Vec<u8>) -> Arc<Datagram> {
        let source: IpAddr = "fe80::5".parse().unwrap();
        let destination: IpAddr = "ff02::1:2".parse().unwrap();

        Arc::new(Datagram {
            source_mac: Some("AA:AA:AA:AA:AA:01".to_string()),
            destination_mac: Some("33:33:00:01:00:02".to_string()),
            source_address: source,
            destination_address: destination,
            session_key: L4Key::new(source, source_port, destination, destination_port),
            source_port,
            destination_port,
            payload,
            size: 150,
            timestamp: Utc::now(),
            tags: Mutex::new(HashSet::new())
        })
    }

    fn solicit() -> Vec<u8> {
        let mut message = vec![1, 0x12, 0x34, 0x56];
        message.extend(option(OPTION_CLIENT_ID, &CLIENT_DUID));
        message.extend(option(OPTION_REQUEST_OPTION, &[0, 23, 0, 24, 0, 39]));
        message.extend(option(8, &[0, 0]));
        message.extend(option(OPTION_RAPID_COMMIT, &[]));

        let mut fqdn = vec![0];
        fqdn.extend_from_slice(&[6, b'l', b'a', b'p', b't', b'o', b'p']);
        message.extend(option(OPTION_CLIENT_FQDN, &fqdn));

        let mut vendor = vec![0, 0, 0x01, 0x37, 0, 8];
        vendor.extend_from_slice(b"MSFT 5.0");
        message.extend(option(OPTION_VENDOR_CLASS, &vendor));

        message.extend(option(OPTION_IA_NA, &[0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0]));
        message
    }

    fn reply() -> Vec<u8> {
        let mut address = "2001:db8:1200:34::100".parse::<Ipv6Addr>().unwrap().octets().to_vec();
        address.extend_from_slice(&[0, 0, 0x0E, 0x10, 0, 0, 0x1C, 0x20]);

        let mut association = vec![0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0];
        association.extend(option(OPTION_IA_ADDRESS, &address));

        let mut prefix = vec![0, 0, 0x0E, 0x10, 0, 0, 0x1C, 0x20, 56];
        prefix.extend_from_slice(&"2001:db8:1200:ff00::".parse::<Ipv6Addr>().unwrap().octets());
        let mut delegation = vec![0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0];
        delegation.extend(option(OPTION_IA_PREFIX, &prefix));

        let mut message = vec![7, 0x12, 0x34, 0x56];
        message.extend(option(OPTION_CLIENT_ID, &CLIENT_DUID));
        message.extend(option(OPTION_SERVER_ID, &[0, 3, 0, 1, 0xBB, 0xBB, 0xBB, 0xBB, 0xBB, 0x02]));
        message.extend(option(OPTION_IA_NA, &association));
        message.extend(option(OPTION_IA_PD, &delegation));
        message.extend(option(OPTION_DNS_SERVERS, &"2001:db8::53".parse::<Ipv6Addr>().unwrap().octets()));
        message.extend(option(OPTION_DOMAIN_LIST, &[7, b'e', b'x', b'a', b'm', b'p', b'l', b'e', 3, b'o', b'r', b'g', 0]));
        message.extend(option(OPTION_PREFERENCE, &[255]));
        message
    }

    #[test]
    fn parses_solicit() {
        let p = parse(&datagram(546, 547, solicit())).unwrap();

        assert_eq!(p.message_type, Dhcpv6MessageType::Solicit);
        assert_eq!(p.transaction_id, 0x123456);
        assert_eq!(p.client_duid, Some("000100012a2b2c2daaaaaaaaaa01".to_string()));
        assert_eq!(p.client_link_layer_address, Some("AA:AA:AA:AA:AA:01".to_string()));
        assert_eq!(p.options, vec![1, 6, 8, 14, 39, 16, 3]);
        assert_eq!(p.option_request_list, vec![23, 24, 39]);
        assert!(p.rapid_commit);
        assert_eq!(p.fqdn, Some("laptop".to_string()));
        assert_eq!(p.vendor_class, Some("311:MSFT 5.0".to_string()));
        assert!(p.addresses.is_empty());
        assert!(!p.relayed);
    }

    #[test]
    fn parses_reply_with_address_prefix_and_dns() {
        let p = parse(&datagram(547, 546, reply())).unwrap();

        assert_eq!(p.message_type, Dhcpv6MessageType::Reply);
        assert_eq!(p.server_duid, Some("00030001bbbbbbbbbb02".to_string()));
        assert_eq!(p.addresses, vec![Dhcpv6Address {
            address: "2001:db8:1200:34::100".parse().unwrap(),
            preferred_lifetime: 3600,
            valid_lifetime: 7200
        }]);
        assert_eq!(p.prefixes, vec![Dhcpv6Prefix {
            prefix: "2001:db8:1200:ff00::".parse().unwrap(),
            prefix_length: 56,
            preferred_lifetime: 3600,
            valid_lifetime: 7200
        }]);
        assert_eq!(p.dns_servers, vec!["2001:db8::53".parse::<IpAddr>().unwrap()]);
        assert_eq!(p.domain_search_list, vec!["example.org".to_string()]);
        assert_eq!(p.preference, Some(255));
        assert_eq!(p.status_code, None);
    }

    #[test]
    fn status_code_inside_an_association_counts() {
        let mut association = vec![0, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0];
        association.extend(option(OPTION_STATUS_CODE, &[0, 2, b'n', b'o']));

        let mut message = vec![7, 0, 0, 1];
        message.extend(option(OPTION_IA_NA, &association));

        assert_eq!(parse(&datagram(547, 546, message)).unwrap().status_code, Some(2));
    }

    #[test]
    fn unwraps_relayed_messages() {
        let mut inner = vec![RELAY_FORWARD, 0];
        inner.extend_from_slice(&[0; 32]);
        inner.extend(option(OPTION_RELAY_MESSAGE, &solicit()));
        inner.extend(option(OPTION_CLIENT_LINK_LAYER_ADDRESS, &[0, 1, 0xCC, 0xCC, 0xCC, 0xCC, 0xCC, 0x03]));

        let mut outer = vec![RELAY_FORWARD, 1];
        outer.extend_from_slice(&[0; 32]);
        outer.extend(option(OPTION_RELAY_MESSAGE, &inner));

        let p = parse(&datagram(547, 547, outer)).unwrap();
        assert!(p.relayed);
        assert_eq!(p.message_type, Dhcpv6MessageType::Solicit);
        assert_eq!(p.client_link_layer_address, Some("CC:CC:CC:CC:CC:03".to_string()));
    }

    #[test]
    fn relay_loops_and_truncated_messages_are_dropped() {
        let mut message = solicit();
        for _ in 0..8 {
            let mut relay = vec![RELAY_FORWARD, 0];
            relay.extend_from_slice(&[0; 32]);
            relay.extend(option(OPTION_RELAY_MESSAGE, &message));
            message = relay;
        }
        assert!(parse(&datagram(547, 547, message)).is_none());

        assert!(parse(&datagram(546, 547, vec![1, 0, 0])).is_none());
        assert!(parse(&datagram(546, 547, vec![99, 0, 0, 1])).is_none());
        assert!(parse(&datagram(547, 547, vec![RELAY_FORWARD, 0, 0])).is_none());
    }

    #[test]
    fn option_that_runs_past_the_end_keeps_what_came_before() {
        let mut message = vec![1, 0, 0, 1];
        message.extend(option(OPTION_CLIENT_ID, &CLIENT_DUID));
        message.extend_from_slice(&[0, 6, 0, 200, 0, 23]);

        let p = parse(&datagram(546, 547, message)).unwrap();
        assert!(p.client_duid.is_some());
        assert!(p.option_request_list.is_empty());
    }

    #[test]
    fn other_ports_and_ipv4_are_not_dhcpv6() {
        assert!(parse(&datagram(5353, 5353, solicit())).is_none());
        assert!(parse(&datagram(546, 53, solicit())).is_none());
    }
}
