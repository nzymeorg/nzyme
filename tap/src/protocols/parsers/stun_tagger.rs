use std::mem;
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};
use chrono::{DateTime, Utc};
use crate::protocols::parsers::l4_key::L4Key;
use crate::protocols::parsers::tcp::tcp_tools::determine_tcp_session_state;
use crate::state::tables::tcp_table::TcpSession;
use crate::state::tables::udp_table::UdpConversation;
use crate::wired::packets::GenericConnectionStatus;

const STUN_MAGIC_COOKIE: [u8; 4] = [0x21, 0x12, 0xA4, 0x42];
const STUN_HEADER_LEN: usize = 20;

// High 16 bits of the magic cookie, used to de-XOR the port.
const STUN_MAGIC_COOKIE_HIGH: u16 = 0x2112;

const ATTR_XOR_PEER_ADDRESS: u16 = 0x0012;
const ATTR_XOR_RELAYED_ADDRESS: u16 = 0x0016;
const ATTR_XOR_MAPPED_ADDRESS: u16 = 0x0020;
const ATTR_USERNAME: u16 = 0x0006;

const ADDRESS_FAMILY_IPV4: u8 = 0x01;
const ADDRESS_FAMILY_IPV6: u8 = 0x02;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[allow(dead_code)]
pub enum StunClass {
    Request,
    Indication,
    SuccessResponse,
    ErrorResponse,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StunTransport {
    Tcp,
    Udp,
}

#[derive(Debug, Clone)]
pub struct StunFlow {
    pub session_key: L4Key,
    pub transport: StunTransport,
    pub source_address: IpAddr,
    pub source_mac: Option<String>,
    pub source_port: u16,
    pub destination_address: IpAddr,
    pub destination_port: u16,

    pub connection_status: Option<GenericConnectionStatus>,

    pub established_at: DateTime<Utc>,
    pub terminated_at: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>,

    pub is_turn: bool,
    pub turn_usernames: Vec<String>,
    pub ufrags: Vec<String>,
    pub mapped_addresses: Vec<SocketAddr>,
    pub relayed_addresses: Vec<SocketAddr>,
    pub peer_addresses: Vec<SocketAddr>,
}

impl StunFlow {

    pub fn estimate_struct_size(&self) -> u32 {
        let mut size = mem::size_of::<Self>() as u32;

        if let Some(mac) = &self.source_mac {
            size += mac.len() as u32;
        }

        for username in &self.turn_usernames {
            size += mem::size_of::<String>() as u32 + username.len() as u32;
        }
        for ufrag in &self.ufrags {
            size += mem::size_of::<String>() as u32 + ufrag.len() as u32;
        }

        size += self.mapped_addresses.len() as u32 * mem::size_of::<SocketAddr>() as u32;
        size += self.relayed_addresses.len() as u32 * mem::size_of::<SocketAddr>() as u32;
        size += self.peer_addresses.len() as u32 * mem::size_of::<SocketAddr>() as u32;

        size
    }

}

#[derive(Debug, Clone, PartialEq, Eq)]
struct StunMessage {
    class: StunClass,
    method: u16,
    username: Option<String>,

    xor_mapped_address: Option<SocketAddr>,
    xor_relayed_address: Option<SocketAddr>,
    xor_peer_address: Option<SocketAddr>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StunTag {
    pub is_turn: bool,
    pub turn_usernames: Vec<String>,
    pub ice_ufrags: Vec<String>,
    pub mapped_addresses: Vec<SocketAddr>,
    pub relayed_addresses: Vec<SocketAddr>,
    pub peer_addresses: Vec<SocketAddr>,
}

fn is_turn_method(method: u16) -> bool {
    matches!(method,
        0x003 // Allocate
        | 0x004 // Refresh
        | 0x006 // Send
        | 0x007 // Data
        | 0x008 // CreatePermission
        | 0x009 // ChannelBind
        | 0x00A // Connect (TURN-TCP)
        | 0x00B // ConnectionBind (TURN-TCP)
        | 0x00C // ConnectionAttempt (TURN-TCP)
    )
}

pub fn tag_tcp(client_to_server: &[u8], server_to_client: &[u8], session: &TcpSession)
    -> Option<StunFlow> {
    let stun = tag(client_to_server, server_to_client)?;

    let (connection_status, terminated_at) = determine_tcp_session_state(session);

    Some(StunFlow {
        session_key: session.session_key.clone(),
        transport: StunTransport::Tcp,
        source_address: session.source_address,
        source_mac: session.source_mac.clone(),
        source_port: session.source_port,
        destination_address: session.destination_address,
        destination_port: session.destination_port,
        connection_status: Some(connection_status),
        established_at: session.start_time,
        terminated_at,
        most_recent_segment_time: session.most_recent_segment_time,
        is_turn: stun.is_turn,
        ufrags: stun.ice_ufrags,
        turn_usernames: stun.turn_usernames,
        mapped_addresses: stun.mapped_addresses,
        relayed_addresses: stun.relayed_addresses,
        peer_addresses: stun.peer_addresses,
    })
}

pub fn tag_udp(client_to_server: &[u8], server_to_client: &[u8], conversation: &UdpConversation)
    -> Option<StunFlow> {

    let stun = tag(client_to_server, server_to_client)?;

    let session_key = L4Key::new(
        conversation.source_address,
        conversation.source_port,
        conversation.destination_address,
        conversation.destination_port,
    );

    Some(StunFlow {
        session_key,
        transport: StunTransport::Udp,
        source_address: conversation.source_address,
        source_port: conversation.source_port,
        source_mac: conversation.source_mac.clone(),
        destination_address: conversation.destination_address,
        destination_port: conversation.destination_port,
        connection_status: None,
        established_at: conversation.start_time,
        terminated_at: conversation.end_time,
        most_recent_segment_time: conversation.most_recent_segment_time,
        is_turn: stun.is_turn,
        ufrags: stun.ice_ufrags,
        turn_usernames: stun.turn_usernames,
        mapped_addresses: stun.mapped_addresses,
        relayed_addresses: stun.relayed_addresses,
        peer_addresses: stun.peer_addresses,
    })
}

fn tag(client_to_server: &[u8], server_to_client: &[u8]) -> Option<StunTag> {
    let mut messages = scan_stun_messages(client_to_server);
    messages.extend(scan_stun_messages(server_to_client));

    if messages.is_empty() {
        return None;
    }

    let is_turn = messages.iter().any(|m| is_turn_method(m.method));

    let mut ice_ufrags: Vec<String> = Vec::new();
    let mut turn_usernames: Vec<String> = Vec::new();
    for m in &messages {
        if let Some(u) = &m.username {
            let bucket = if is_turn_method(m.method) {
                &mut turn_usernames
            } else {
                &mut ice_ufrags
            };
            if !bucket.contains(u) {
                bucket.push(u.clone());
            }
        }
    }

    // Collect the address attributes, deduplicated, preserving first-seen order.
    let mut mapped_addresses: Vec<SocketAddr> = Vec::new();
    let mut relayed_addresses: Vec<SocketAddr> = Vec::new();
    let mut peer_addresses: Vec<SocketAddr> = Vec::new();
    for message in &messages {
        push_unique(&mut mapped_addresses, message.xor_mapped_address);
        push_unique(&mut relayed_addresses, message.xor_relayed_address);
        push_unique(&mut peer_addresses, message.xor_peer_address);
    }

    Some(StunTag {
        is_turn,
        ice_ufrags,
        turn_usernames,
        mapped_addresses,
        relayed_addresses,
        peer_addresses
    })
}

fn push_unique(target: &mut Vec<SocketAddr>, value: Option<SocketAddr>) {
    if let Some(address) = value && !target.contains(&address) {
        target.push(address);
    }
}

fn scan_stun_messages(buf: &[u8]) -> Vec<StunMessage> {
    let mut out: Vec<StunMessage> = Vec::new();
    let mut offset: usize = 0;

    while let Some(remaining) = buf.get(offset..) {
        match parse_stun_message(remaining) {
            Some((message, consumed)) => {
                out.push(message);
                offset = match offset.checked_add(consumed) {
                    Some(next) => next,
                    None => break,
                };
            }
            None => break,
        }
    }

    out
}

fn parse_stun_message(buf: &[u8]) -> Option<(StunMessage, usize)> {
    if buf.len() < STUN_HEADER_LEN {
        return None;
    }

    // The two most significant bits of a STUN message are always zero.
    let type_field = u16::from_be_bytes([buf[0], buf[1]]);
    if type_field & 0xC000 != 0 {
        return None;
    }

    // Message length excludes the 20-byte header and is always a multiple of 4.
    let msg_len = u16::from_be_bytes([buf[2], buf[3]]) as usize;
    if !msg_len.is_multiple_of(4) {
        return None;
    }

    // Magic cookie.
    if buf[4..8] != STUN_MAGIC_COOKIE {
        return None;
    }

    let total = STUN_HEADER_LEN.checked_add(msg_len)?;
    if buf.len() < total {
        // Incomplete message. (maybe a TCP segment boundary mid-message)
        return None;
    }

    let class = decode_class(type_field);
    let method = decode_method(type_field);

    let transaction_id = buf.get(8..STUN_HEADER_LEN).unwrap_or(&[]);
    let attributes = buf.get(STUN_HEADER_LEN..total).unwrap_or(&[]);
    let parsed = parse_attributes(attributes, transaction_id);

    Some((
        StunMessage {
            class,
            method,
            username: parsed.username,
            xor_mapped_address: parsed.xor_mapped_address,
            xor_relayed_address: parsed.xor_relayed_address,
            xor_peer_address: parsed.xor_peer_address,
        }, total
    ))
}

fn decode_class(type_field: u16) -> StunClass {
    let class_bits = ((type_field & 0x0100) >> 7) | ((type_field & 0x0010) >> 4);
    match class_bits {
        0b00 => StunClass::Request,
        0b01 => StunClass::Indication,
        0b10 => StunClass::SuccessResponse,
        _ => StunClass::ErrorResponse, // 0b11
    }
}

fn decode_method(type_field: u16) -> u16 {
    ((type_field & 0x3E00) >> 2) | ((type_field & 0x00E0) >> 1) | (type_field & 0x000F)
}

#[derive(Default)]
struct ParsedAttributes {
    username: Option<String>,
    xor_mapped_address: Option<SocketAddr>,
    xor_relayed_address: Option<SocketAddr>,
    xor_peer_address: Option<SocketAddr>,
}

fn parse_attributes(attributes: &[u8], transaction_id: &[u8]) -> ParsedAttributes {
    let mut parsed = ParsedAttributes::default();

    let mut pos: usize = 0;
    while pos + 4 <= attributes.len() {
        let attr_type = u16::from_be_bytes([attributes[pos], attributes[pos + 1]]);
        let attr_len = u16::from_be_bytes([attributes[pos + 2], attributes[pos + 3]]) as usize;

        let value_start = pos + 4;
        let value_end = match value_start.checked_add(attr_len) {
            Some(end) => end,
            None => break,
        };
        if value_end > attributes.len() {
            // Truncated / malformed attribute.
            break;
        }

        let value = attributes.get(value_start..value_end).unwrap_or(&[]);

        match attr_type {
            ATTR_USERNAME => {
                if parsed.username.is_none() {
                    parsed.username = Some(String::from_utf8_lossy(value).into_owned());
                }
            }
            ATTR_XOR_MAPPED_ADDRESS => {
                if parsed.xor_mapped_address.is_none() {
                    parsed.xor_mapped_address = parse_xor_address(value, transaction_id);
                }
            }
            ATTR_XOR_RELAYED_ADDRESS => {
                if parsed.xor_relayed_address.is_none() {
                    parsed.xor_relayed_address = parse_xor_address(value, transaction_id);
                }
            }
            ATTR_XOR_PEER_ADDRESS => {
                if parsed.xor_peer_address.is_none() {
                    parsed.xor_peer_address = parse_xor_address(value, transaction_id);
                }
            }
            _ => {}
        }

        // Advance past the value and its padding to the next 4-byte boundary.
        let padding = (4 - (attr_len % 4)) % 4;
        pos = match value_end.checked_add(padding) {
            Some(next) => next,
            None => break,
        };
    }

    parsed
}

fn parse_xor_address(value: &[u8], transaction_id: &[u8]) -> Option<SocketAddr> {
    if value.len() < 4 {
        return None;
    }

    let family = value[1];
    let x_port = u16::from_be_bytes([value[2], value[3]]);
    let port = x_port ^ STUN_MAGIC_COOKIE_HIGH;

    match family {
        ADDRESS_FAMILY_IPV4 => {
            let x_addr = value.get(4..8)?;
            let mut octets = [0u8; 4];
            for i in 0..4 {
                octets[i] = x_addr[i] ^ STUN_MAGIC_COOKIE[i];
            }
            Some(SocketAddr::new(IpAddr::V4(Ipv4Addr::from(octets)), port))
        }
        ADDRESS_FAMILY_IPV6 => {
            let x_addr = value.get(4..20)?;
            if transaction_id.len() < 12 {
                return None;
            }

            let mut key = [0u8; 16];
            key[0..4].copy_from_slice(&STUN_MAGIC_COOKIE);
            key[4..16].copy_from_slice(transaction_id.get(0..12)?);

            let mut octets = [0u8; 16];
            for i in 0..16 {
                octets[i] = x_addr[i] ^ key[i];
            }
            Some(SocketAddr::new(IpAddr::V6(Ipv6Addr::from(octets)), port))
        }
        _ => None,
    }
}
