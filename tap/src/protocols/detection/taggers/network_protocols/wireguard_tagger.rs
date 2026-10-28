use std::collections::{BTreeMap, VecDeque};
use base64::Engine;
use base64::engine::general_purpose::STANDARD as BASE64;
use crate::protocols::detection::l7_tagger::L7Tag;
use crate::protocols::detection::taggers::network_protocols::vpn_handshake_observer::{read_u32_le, read_u64_le, VpnHandshakeMessage};
use crate::protocols::detection::taggers::network_protocols::vpn_tagger_tools::{build_udp_tunnel, observed_handshake};
use crate::protocols::detection::taggers::tagger_command::TaggerCommand;
use crate::state::tables::udp_table::UdpConversation;
use crate::tracemark;
use crate::wired::packets::{VpnHandshakeStatus, VpnTunnel, VpnType};

const TYPE_HANDSHAKE_INITIATION: u8 = 1;
const TYPE_HANDSHAKE_RESPONSE: u8 = 2;
const TYPE_COOKIE_REPLY: u8 = 3;
const TYPE_TRANSPORT_DATA: u8 = 4;

const LEN_HANDSHAKE_INITIATION: usize = 148;
const LEN_HANDSHAKE_RESPONSE: usize = 92;
const LEN_COOKIE_REPLY: usize = 64;
const LEN_TRANSPORT_HEADER: usize = 16;
const LEN_TRANSPORT_MIN: usize = 32;
const TRANSPORT_PAYLOAD_ALIGNMENT: usize = 16;

// Transport-only conversations need this many consistent messages before they are tagged.
const MIN_TRANSPORT_MESSAGES: usize = 2;

const TAILSCALE_DISCO_MAGIC: [u8; 6] = [0x54, 0x53, 0xF0, 0x9F, 0x92, 0xAC];
const TAILSCALE_DISCO_KEY_LEN: usize = 32;
pub const PROVIDER_TAILSCALE: &str = "Tailscale";

const OFFSET_SENDER_INDEX: usize = 4;
const OFFSET_RESPONSE_RECEIVER_INDEX: usize = 8;
const OFFSET_INITIATION_MAC1: usize = 116;
const OFFSET_RESPONSE_MAC1: usize = 60;
const LEN_MAC: usize = 16;
const OFFSET_TRANSPORT_RECEIVER_INDEX: usize = 4;
const OFFSET_TRANSPORT_COUNTER: usize = 8;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Message {
    HandshakeInitiation,
    HandshakeResponse,
    CookieReply,
    TransportData,
    TailscaleDisco
}

impl Message {
    fn name(&self) -> &'static str {
        match self {
            Message::HandshakeInitiation => "HandshakeInitiation",
            Message::HandshakeResponse => "HandshakeResponse",
            Message::CookieReply => "CookieReply",
            Message::TransportData => "TransportData",
            Message::TailscaleDisco => "TailscaleDisco"
        }
    }
}

pub fn observe_handshake(payload: &[u8]) -> Option<VpnHandshakeMessage> {
    match classify(payload)? {
        Message::HandshakeInitiation => Some(VpnHandshakeMessage::WireGuardHandshakeInitiation {
            sender_index: read_u32_le(&payload[OFFSET_SENDER_INDEX..OFFSET_SENDER_INDEX + 4]),
            cookie_used: payload[OFFSET_INITIATION_MAC1 + LEN_MAC..].iter().any(|b| *b != 0)
        }),
        Message::HandshakeResponse => Some(VpnHandshakeMessage::WireGuardHandshakeResponse {
            sender_index: read_u32_le(&payload[OFFSET_SENDER_INDEX..OFFSET_SENDER_INDEX + 4]),
            receiver_index: read_u32_le(&payload[OFFSET_RESPONSE_RECEIVER_INDEX..OFFSET_RESPONSE_RECEIVER_INDEX + 4]),
            cookie_used: payload[OFFSET_RESPONSE_MAC1 + LEN_MAC..].iter().any(|b| *b != 0)
        }),
        _ => None
    }
}

pub fn observe_transport(payload: &[u8]) -> Option<(u32, u64)> {
    match classify(payload)? {
        Message::TransportData => Some((
            read_u32_le(&payload[OFFSET_TRANSPORT_RECEIVER_INDEX..OFFSET_TRANSPORT_RECEIVER_INDEX + 4]),
            read_u64_le(&payload[OFFSET_TRANSPORT_COUNTER..OFFSET_TRANSPORT_COUNTER + 8])
        )),
        _ => None
    }
}

pub fn observe_tailscale_disco(payload: &[u8]) -> Option<String> {
    if !payload.starts_with(&TAILSCALE_DISCO_MAGIC)
        || payload.len() < TAILSCALE_DISCO_MAGIC.len() + TAILSCALE_DISCO_KEY_LEN {
        return None;
    }

    let key = &payload[TAILSCALE_DISCO_MAGIC.len()..TAILSCALE_DISCO_MAGIC.len() + TAILSCALE_DISCO_KEY_LEN];
    Some(BASE64.encode(key))
}

pub fn tag_udp(conversation: &UdpConversation) -> Option<(VpnTunnel, Vec<TaggerCommand>)> {
    let inherited = conversation.tags.contains(&L7Tag::WireGuard);

    let (mut c2s, mut s2c) = match (scan(&conversation.datagrams_client_to_server),
                                    scan(&conversation.datagrams_server_to_client)) {
        (Some(c2s), Some(s2c)) => (c2s, s2c),
        _ if inherited => (Vec::new(), Vec::new()),
        _ => return None
    };

    if c2s.is_empty() && s2c.is_empty() && !inherited {
        return None;
    }

    let observed = observed_handshake(
        conversation,
        |m| matches!(m, VpnHandshakeMessage::WireGuardHandshakeInitiation { .. }),
        |m| matches!(m, VpnHandshakeMessage::WireGuardHandshakeResponse { .. })
    );
    if observed.initiation_c2s {
        c2s.push(Message::HandshakeInitiation);
    }
    if observed.initiation_s2c {
        s2c.push(Message::HandshakeInitiation);
    }
    if observed.response_c2s {
        c2s.push(Message::HandshakeResponse);
    }
    if observed.response_s2c {
        s2c.push(Message::HandshakeResponse);
    }

    let initiation_c2s = c2s.contains(&Message::HandshakeInitiation);
    let initiation_s2c = s2c.contains(&Message::HandshakeInitiation);
    let response_c2s = c2s.contains(&Message::HandshakeResponse);
    let response_s2c = s2c.contains(&Message::HandshakeResponse);
    let response_seen = response_c2s || response_s2c;
    let initiation_seen = initiation_c2s || initiation_s2c;

    let handshake_status = if initiation_seen && response_seen {
        VpnHandshakeStatus::Complete
    } else if initiation_seen || response_seen {
        VpnHandshakeStatus::Partial
    } else {
        let transport_count = c2s.iter().chain(s2c.iter())
            .filter(|m| **m == Message::TransportData)
            .count();

        if transport_count < MIN_TRANSPORT_MESSAGES {
            tracemark!("WireGuard: Not enough transport messages.");
            return None;
        }

        VpnHandshakeStatus::NotObserved
    };

    let reversed = if initiation_seen {
        initiation_s2c && !initiation_c2s
    } else {
        response_c2s && !response_s2c
    };

    let mut message_types: Vec<String> = Vec::new();
    for m in c2s.iter().chain(s2c.iter()) {
        let name = m.name().to_string();
        if !message_types.contains(&name) {
            message_types.push(name);
        }
    }

    let mut tunnel = build_udp_tunnel(
        conversation, VpnType::WireGuard, handshake_status, message_types, reversed
    );

    // Only Tailscale speaks disco on the WireGuard socket.
    if c2s.iter().chain(s2c.iter()).any(|m| *m == Message::TailscaleDisco)
        || !conversation.vpn.tailscale_disco_keys.is_empty() {
        tunnel.provider = Some(PROVIDER_TAILSCALE.to_string());
    }

    add_handshake_details(conversation, &mut tunnel, reversed);

    let mut commands = Vec::new();
    if reversed {
        commands.push(TaggerCommand::OrientClientTo {
            address: conversation.destination_address,
            port: conversation.destination_port
        });
    }

    Some((tunnel, commands))
}

fn add_handshake_details(conversation: &UdpConversation, tunnel: &mut VpnTunnel, reversed: bool) {
    let obs = &conversation.vpn;
    let mut details: BTreeMap<String, String> = BTreeMap::new();

    let is_client = |address: &std::net::IpAddr, port: u16| -> bool {
        let from_source = *address == conversation.source_address && port == conversation.source_port;
        from_source != reversed
    };

    let mut initiations: u64 = 0;
    let mut responses: u64 = 0;
    let mut cookies: u64 = 0;
    for h in &obs.handshakes {
        match &h.message {
            VpnHandshakeMessage::WireGuardHandshakeInitiation { sender_index, cookie_used } => {
                initiations += 1;
                cookies += *cookie_used as u64;
                tunnel.initiation_ids.push(*sender_index as u64);
            },
            VpnHandshakeMessage::WireGuardHandshakeResponse { receiver_index, cookie_used, .. } => {
                responses += 1;
                cookies += *cookie_used as u64;
                tunnel.response_to_ids.push(*receiver_index as u64);
            },
            _ => {}
        }
    }

    details.insert("Handshake initiations".to_string(), initiations.to_string());
    details.insert("Handshake responses".to_string(), responses.to_string());
    details.insert("Handshakes with cookie (responder under load)".to_string(), cookies.to_string());

    let mut sessions: u64 = 0;
    let (mut packets_c2s, mut packets_s2c, mut missed) = (0u64, 0u64, 0u64);
    let mut existed_before: Option<bool> = None;
    for s in &obs.wireguard_sessions {
        sessions += 1;
        let span = s.last_counter - s.first_counter + 1;
        missed += span.saturating_sub(s.datagrams);
        if is_client(&s.sender_address, s.sender_port) {
            packets_c2s += span;
        } else {
            packets_s2c += span;
        }
        if existed_before.is_none() {
            existed_before = Some(s.first_counter > 1);
        }
    }

    details.insert("Transport sessions".to_string(), sessions.to_string());
    details.insert("Packets client to server (by counter)".to_string(), packets_c2s.to_string());
    details.insert("Packets server to client (by counter)".to_string(), packets_s2c.to_string());
    details.insert("Packets not seen by tap".to_string(), missed.to_string());
    if let Some(existed) = existed_before {
        details.insert("Session existed before observation".to_string(),
                       if existed { "Yes" } else { "No" }.to_string());
    }

    for k in &obs.tailscale_disco_keys {
        let role = if is_client(&k.sender_address, k.sender_port) { "client" } else { "server" };
        details.insert(format!("Tailscale disco key ({})", role), k.key.clone());
    }

    tunnel.details.extend(details);
}

fn scan(datagrams: &VecDeque<Vec<u8>>) -> Option<Vec<Message>> {
    let mut messages = Vec::with_capacity(datagrams.len());

    for datagram in datagrams {
        match classify(datagram) {
            Some(m) => messages.push(m),
            None => {
                tracemark!("WireGuard: Datagram is not a WireGuard message.");
                return None;
            }
        }
    }

    Some(messages)
}

fn classify(datagram: &[u8]) -> Option<Message> {
    if datagram.len() < LEN_TRANSPORT_HEADER {
        return None;
    }

    if datagram.starts_with(&TAILSCALE_DISCO_MAGIC) {
        return Some(Message::TailscaleDisco);
    }

    if datagram[1] != 0 || datagram[2] != 0 || datagram[3] != 0 {
        return None;
    }

    match datagram[0] {
        TYPE_HANDSHAKE_INITIATION if datagram.len() == LEN_HANDSHAKE_INITIATION =>
            Some(Message::HandshakeInitiation),
        TYPE_HANDSHAKE_RESPONSE if datagram.len() == LEN_HANDSHAKE_RESPONSE =>
            Some(Message::HandshakeResponse),
        TYPE_COOKIE_REPLY if datagram.len() == LEN_COOKIE_REPLY =>
            Some(Message::CookieReply),
        TYPE_TRANSPORT_DATA if datagram.len() >= LEN_TRANSPORT_MIN
            && (datagram.len() - LEN_TRANSPORT_HEADER) % TRANSPORT_PAYLOAD_ALIGNMENT == 0 =>
            Some(Message::TransportData),
        _ => None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn message(t: u8, len: usize) -> Vec<u8> {
        let mut m = vec![0u8; len];
        m[0] = t;
        m
    }

    #[test]
    fn classifies_handshake_messages() {
        assert_eq!(classify(&message(1, 148)), Some(Message::HandshakeInitiation));
        assert_eq!(classify(&message(2, 92)), Some(Message::HandshakeResponse));
        assert_eq!(classify(&message(3, 64)), Some(Message::CookieReply));
    }

    #[test]
    fn rejects_wrong_handshake_lengths() {
        assert_eq!(classify(&message(1, 147)), None);
        assert_eq!(classify(&message(2, 93)), None);
    }

    #[test]
    fn classifies_transport_messages() {
        assert_eq!(classify(&message(4, 32)), Some(Message::TransportData));
        assert_eq!(classify(&message(4, 1232)), Some(Message::TransportData));
        assert_eq!(classify(&message(4, 33)), None);
        assert_eq!(classify(&message(4, 16)), None);
    }

    #[test]
    fn rejects_nonzero_reserved_bytes() {
        let mut m = message(1, 148);
        m[2] = 1;
        assert_eq!(classify(&m), None);
    }

    #[test]
    fn reads_transport_header() {
        let mut t = vec![0u8; 48];
        t[0] = 4;
        t[4..8].copy_from_slice(&[0x78, 0x56, 0x34, 0x12]);
        t[8..16].copy_from_slice(&1000u64.to_le_bytes());
        assert_eq!(observe_transport(&t), Some((0x12345678, 1000)));
        assert_eq!(observe_transport(&message(1, 148)), None);
    }

    #[test]
    fn extracts_tailscale_disco_key() {
        let mut m = TAILSCALE_DISCO_MAGIC.to_vec();
        m.extend_from_slice(&[0x01; 32]);
        m.extend_from_slice(&[0x02; 24]);
        let key = observe_tailscale_disco(&m).unwrap();
        assert_eq!(key, BASE64.encode([0x01; 32]));
        assert_eq!(observe_tailscale_disco(&message(4, 48)), None);
    }

    #[test]
    fn classifies_tailscale_disco() {
        let mut m = TAILSCALE_DISCO_MAGIC.to_vec();
        m.extend_from_slice(&[0x5A; 70]);
        assert_eq!(classify(&m), Some(Message::TailscaleDisco));
    }

    #[test]
    fn rejects_unknown_types() {
        assert_eq!(classify(&message(0, 148)), None);
        assert_eq!(classify(&message(5, 148)), None);
    }
}
