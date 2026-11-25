use std::collections::VecDeque;
use std::net::Ipv6Addr;
use crate::protocols::detection::l7_tagger::L7Tag;
use crate::protocols::parsers::ipv6_parser;
use crate::protocols::parsers::l4_key::L4Key;
use crate::state::tables::udp_table::UdpConversation;
use crate::tracemark;
use crate::wired::packets::{TeredoConversation, TeredoMessage as Message, TeredoQualification};

/*
 * Teredo (RFC 4380) is not tied to a port: only the server listens on 3544, peers talk on whatever port their NAT
 * gave them. So every datagram of the conversation has to be a well-formed Teredo packet.
 */

const LEN_ORIGIN_INDICATION: usize = 8;

const NEXT_HEADER_ICMPV6: u8 = 58;
const NEXT_HEADER_NONE: u8 = 59;

const ICMPV6_ROUTER_SOLICITATION: u8 = 133;
const ICMPV6_ROUTER_ADVERTISEMENT: u8 = 134;

const ALL_NODES: Ipv6Addr = Ipv6Addr::new(0xff02, 0, 0, 0, 0, 0, 0, 1);

pub fn tag_udp(conversation: &UdpConversation) -> Option<TeredoConversation> {
    let inherited = conversation.tags.contains(&L7Tag::Teredo);

    let (c2s, s2c) = match (scan(&conversation.datagrams_client_to_server),
                            scan(&conversation.datagrams_server_to_client)) {
        (Some(c2s), Some(s2c)) => (c2s, s2c),
        _ if inherited => (Vec::new(), Vec::new()),
        _ => return None
    };

    if c2s.is_empty() && s2c.is_empty() && !inherited {
        return None;
    }

    let solicited = c2s.iter().chain(s2c.iter()).any(|m| *m == Message::RouterSolicitation);
    let advertised = c2s.iter().chain(s2c.iter()).any(|m| *m == Message::RouterAdvertisement);
    let qualification = if solicited && advertised {
        TeredoQualification::Complete
    } else if solicited || advertised {
        TeredoQualification::Partial
    } else {
        TeredoQualification::NotObserved
    };

    let mut messages: Vec<Message> = Vec::new();
    for m in c2s.iter().chain(s2c.iter()) {
        if !messages.contains(m) {
            messages.push(*m);
        }
    }

    Some(TeredoConversation {
        session_key: L4Key::new(
            conversation.source_address, conversation.source_port,
            conversation.destination_address, conversation.destination_port
        ),
        source_mac: conversation.source_mac.clone(),
        destination_mac: conversation.destination_mac.clone(),
        source_address: conversation.source_address,
        destination_address: conversation.destination_address,
        source_port: conversation.source_port,
        destination_port: conversation.destination_port,
        messages,
        qualification,
        start_time: conversation.start_time,
        end_time: conversation.end_time,
        most_recent_segment_time: conversation.most_recent_segment_time,
        bytes: conversation.bytes_count_rx + conversation.bytes_count_tx
    })
}

fn scan(datagrams: &VecDeque<Vec<u8>>) -> Option<Vec<Message>> {
    let mut messages = Vec::with_capacity(datagrams.len());

    for datagram in datagrams {
        match classify(datagram) {
            Some(m) => messages.push(m),
            None => {
                tracemark!("Teredo: Datagram is not a Teredo packet.");
                return None;
            }
        }
    }

    Some(messages)
}

fn classify(datagram: &[u8]) -> Option<Message> {
    let mut offset = 0;

    // Authentication header: 0x0001, two lengths, client ID, authentication value, nonce and confirmation byte.
    if datagram.len() >= 4 && datagram[0] == 0 && datagram[1] == 1 {
        offset = 4 + datagram[2] as usize + datagram[3] as usize + 8 + 1;
    }

    // Origin indication: 0x0000, then the obfuscated port and address.
    let rest = datagram.get(offset..)?;
    if rest.len() >= LEN_ORIGIN_INDICATION && rest[0] == 0 && rest[1] == 0 {
        offset += LEN_ORIGIN_INDICATION;
    }

    let ip = datagram.get(offset..)?;
    let header = ipv6_parser::read_fixed_header(ip)?;

    let payload_length = header.payload_length;
    if ipv6_parser::HEADER_LENGTH + payload_length != ip.len() {
        return None;
    }

    let next_header = header.next_header;
    let source = header.source_address;
    let destination = header.destination_address;
    let teredo = is_teredo(&source) || is_teredo(&destination);

    if next_header == NEXT_HEADER_ICMPV6 && payload_length >= 8 && source.is_unicast_link_local() {
        match ip[ipv6_parser::HEADER_LENGTH] {
            ICMPV6_ROUTER_SOLICITATION => return Some(Message::RouterSolicitation),
            ICMPV6_ROUTER_ADVERTISEMENT => return Some(Message::RouterAdvertisement),
            _ => {}
        }
    }

    let bubble = next_header == NEXT_HEADER_NONE && payload_length == 0;

    // Local discovery: a bubble to all nodes, sent to the Teredo multicast group. The sender may not be qualified yet.
    if bubble && destination == ALL_NODES {
        return Some(Message::Bubble);
    }

    if !teredo {
        return None;
    }

    if bubble {
        return Some(Message::Bubble);
    }

    Some(Message::Data)
}

fn is_teredo(address: &Ipv6Addr) -> bool {
    address.segments()[0] == 0x2001 && address.segments()[1] == 0
}


#[cfg(test)]
mod tests {
    use super::*;

    fn packet(source: &str, destination: &str, next_header: u8, payload: &[u8]) -> Vec<u8> {
        let mut p = vec![0x60, 0, 0, 0];
        p.extend_from_slice(&(payload.len() as u16).to_be_bytes());
        p.push(next_header);
        p.push(64);
        p.extend_from_slice(&source.parse::<Ipv6Addr>().unwrap().octets());
        p.extend_from_slice(&destination.parse::<Ipv6Addr>().unwrap().octets());
        p.extend_from_slice(payload);
        p
    }

    #[test]
    fn classifies_the_qualification() {
        let solicitation = packet("fe80::ffff:ffff:ffff", "ff02::2", 58, &[133, 0, 0, 0, 0, 0, 0, 0]);
        assert_eq!(classify(&solicitation), Some(Message::RouterSolicitation));

        let mut advertisement = vec![0, 0, 0x12, 0x34, 1, 2, 3, 4];
        advertisement.extend(packet("fe80::8000:f227:bec7:9b4f", "fe80::ffff:ffff:ffff", 58,
                                    &[134, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]));
        assert_eq!(classify(&advertisement), Some(Message::RouterAdvertisement));

        let mut authenticated = vec![0, 1, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 0];
        authenticated.extend(solicitation);
        assert_eq!(classify(&authenticated), Some(Message::RouterSolicitation));
    }

    #[test]
    fn classifies_bubbles_and_data() {
        let client = "2001:0:4136:e378:8000:63bf:3fff:fdd2";

        assert_eq!(classify(&packet(client, "2001:0:4136:e378:8000:f227:bec7:9b4f", 59, &[])), Some(Message::Bubble));
        assert_eq!(classify(&packet(client, "2001:db8::1", 6, &[0u8; 20])), Some(Message::Data));
        assert_eq!(classify(&packet("2001:db8::1", client, 17, &[0u8; 12])), Some(Message::Data));
    }

    #[test]
    fn classifies_local_discovery_bubbles_of_unqualified_clients() {
        assert_eq!(classify(&packet("fe80::ffff:ffff:fffe", "ff02::1", 59, &[])), Some(Message::Bubble));
        assert_eq!(classify(&packet("fe80::ffff:ffff:fffe", "ff02::1", 17, &[0u8; 12])), None);
        assert_eq!(classify(&packet("fe80::ffff:ffff:fffe", "ff02::2", 59, &[])), None);
    }

    #[test]
    fn rejects_what_is_not_teredo() {
        assert_eq!(classify(&packet("2001:db8::1", "2001:db8::2", 6, &[0u8; 20])), None);

        let mut long = packet("2001:0:4136:e378:8000:63bf:3fff:fdd2", "2001:db8::1", 6, &[0u8; 20]);
        long.push(0);
        assert_eq!(classify(&long), None);
        assert_eq!(classify(&long[..30]), None);

        assert_eq!(classify(&[0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0]), None);
        assert_eq!(classify(&[0xc0; 1200]), None);
        assert_eq!(classify(&[0, 1, 200, 200, 0, 0]), None);
        assert_eq!(classify(&[]), None);
    }
}
