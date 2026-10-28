use std::collections::VecDeque;
use byteorder::{BigEndian, ByteOrder};
use chrono::Utc;
use crate::protocols::detection::l7_tagger::L7Tag;
use crate::protocols::detection::taggers::network_protocols::vpn_handshake_observer::{OpenVpnControlChannel, VpnHandshakeMessage};
use crate::protocols::detection::taggers::network_protocols::vpn_tagger_tools::{build_tcp_tunnel, build_udp_tunnel, observed_handshake};
use crate::protocols::detection::taggers::tagger_command::TaggerCommand;
use crate::state::tables::tcp_table::TcpSession;
use crate::state::tables::udp_table::UdpConversation;
use crate::tracemark;
use crate::wired::packets::{VpnHandshakeStatus, VpnTunnel, VpnType};

const OP_CONTROL_HARD_RESET_CLIENT_V1: u8 = 1;
const OP_CONTROL_HARD_RESET_SERVER_V1: u8 = 2;
const OP_CONTROL_SOFT_RESET_V1: u8 = 3;
const OP_CONTROL_V1: u8 = 4;
const OP_ACK_V1: u8 = 5;
const OP_DATA_V1: u8 = 6;
const OP_CONTROL_HARD_RESET_CLIENT_V2: u8 = 7;
const OP_CONTROL_HARD_RESET_SERVER_V2: u8 = 8;
const OP_DATA_V2: u8 = 9;
const OP_CONTROL_HARD_RESET_CLIENT_V3: u8 = 10;
const OP_CONTROL_WKC_V1: u8 = 11;

// Opcode, session id, ack array length and packet id.
const LEN_CONTROL_MIN: usize = 1 + 8 + 1 + 4;
// Opcode and session id.
const LEN_CONTROL_HEADER: usize = 1 + 8;
// Replay protection: packet id and network time, present with tls-auth and tls-crypt.
const LEN_REPLAY: usize = 4 + 4;
// tls-crypt authentication tag.
const LEN_TLS_CRYPT_TAG: usize = 32;
// Smallest tls-crypt control packet: header, replay, tag and an encrypted ack length plus packet id.
const LEN_TLS_CRYPT_MIN: usize = LEN_CONTROL_HEADER + LEN_REPLAY + LEN_TLS_CRYPT_TAG + 1 + 4;
// Smallest wrapped client key (WKc) appended to a tls-crypt-v2 client reset: tag, key, length.
const LEN_WKC_MIN: usize = 32 + 64 + 2;
// Digest sizes OpenVPN can use for tls-auth HMACs: MD5, SHA1, SHA224, SHA256, SHA384, SHA512.
const TLS_AUTH_HMAC_SIZES: [usize; 6] = [16, 20, 28, 32, 48, 64];
// Replay packet ids of hard resets are small because they are the first packets of a session.
const MAX_RESET_REPLAY_PACKET_ID: u32 = 0xFFFF;
// Network time in replay protection must be close to our own clock.
const MAX_CLOCK_SKEW_SECONDS: i64 = 7 * 24 * 60 * 60;
// Opcode and peer id.
const LEN_DATA_V2_MIN: usize = 4;
const LEN_DATA_V1_MIN: usize = 2;

// Largest packet accepted when walking a TCP stream.
const MAX_TCP_FRAME: usize = 65_535;

// Mid-tunnel conversations (no handshake in the buffers) need this many consistent data packets.
const MIN_MIDSTREAM_DATA_PACKETS: usize = 4;
// Peer ids are assigned by the server starting at zero, or unset.
const PEER_ID_UNSET: u32 = 0xFF_FF_FF;
const PEER_ID_ASSIGNED_MAX: u32 = 0x00_FF_FF;

/*
 * Parsed fields of a hard reset packet. The remote session id is only visible when the control
 * channel is not encrypted. (No tls-crypt)
 */
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct Reset {
    session_id: u64,
    remote_session_id: Option<u64>,
    control_channel: OpenVpnControlChannel,
    // Sender's unix time from the replay protection field, if the layout carries one.
    net_time: Option<u32>
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct Packet {
    opcode: u8,
    key_id: u8,
    first_byte: u8,
    peer_id: Option<u32>,
    reset: Option<Reset>
}

fn opcode_name(opcode: u8) -> Option<&'static str> {
    match opcode {
        OP_CONTROL_HARD_RESET_CLIENT_V1 => Some("HardResetClientV1"),
        OP_CONTROL_HARD_RESET_SERVER_V1 => Some("HardResetServerV1"),
        OP_CONTROL_SOFT_RESET_V1 => Some("SoftResetV1"),
        OP_CONTROL_V1 => Some("ControlV1"),
        OP_ACK_V1 => Some("AckV1"),
        OP_DATA_V1 => Some("DataV1"),
        OP_CONTROL_HARD_RESET_CLIENT_V2 => Some("HardResetClientV2"),
        OP_CONTROL_HARD_RESET_SERVER_V2 => Some("HardResetServerV2"),
        OP_DATA_V2 => Some("DataV2"),
        OP_CONTROL_HARD_RESET_CLIENT_V3 => Some("HardResetClientV3"),
        OP_CONTROL_WKC_V1 => Some("ControlWkcV1"),
        _ => None
    }
}

fn is_client_reset(p: &Packet) -> bool {
    matches!(p.opcode, OP_CONTROL_HARD_RESET_CLIENT_V1
        | OP_CONTROL_HARD_RESET_CLIENT_V2
        | OP_CONTROL_HARD_RESET_CLIENT_V3)
}

fn is_server_reset(p: &Packet) -> bool {
    matches!(p.opcode, OP_CONTROL_HARD_RESET_SERVER_V1 | OP_CONTROL_HARD_RESET_SERVER_V2)
}

fn is_hard_reset_opcode(opcode: u8) -> bool {
    matches!(opcode, OP_CONTROL_HARD_RESET_CLIENT_V1
        | OP_CONTROL_HARD_RESET_SERVER_V1
        | OP_CONTROL_HARD_RESET_CLIENT_V2
        | OP_CONTROL_HARD_RESET_SERVER_V2
        | OP_CONTROL_HARD_RESET_CLIENT_V3)
}

fn classify(packet: &[u8]) -> Option<Packet> {
    if packet.is_empty() {
        return None;
    }

    let first_byte = packet[0];
    let opcode = first_byte >> 3;
    let key_id = first_byte & 0x07;

    opcode_name(opcode)?;

    let mut peer_id = None;
    let mut reset = None;

    match opcode {
        OP_DATA_V2 => {
            if packet.len() < LEN_DATA_V2_MIN {
                return None;
            }
            peer_id = Some(BigEndian::read_u24(&packet[1..4]));
        },
        OP_DATA_V1 => {
            if packet.len() < LEN_DATA_V1_MIN {
                return None;
            }
        },
        op if is_hard_reset_opcode(op) => {
            // Hard resets always start a new session with key id zero.
            if key_id != 0 {
                return None;
            }
            reset = Some(parse_reset(op, packet)?);
        },
        _ => {
            // All other control channel packets.
            if packet.len() < LEN_CONTROL_MIN {
                return None;
            }
        }
    };

    Some(Packet { opcode, key_id, first_byte, peer_id, reset })
}

fn parse_reset(opcode: u8, packet: &[u8]) -> Option<Reset> {
    if packet.len() < LEN_CONTROL_MIN {
        return None;
    }

    let session_id = BigEndian::read_u64(&packet[1..9]);
    if session_id == 0 {
        return None;
    }

    let is_client = matches!(opcode, OP_CONTROL_HARD_RESET_CLIENT_V1
        | OP_CONTROL_HARD_RESET_CLIENT_V2
        | OP_CONTROL_HARD_RESET_CLIENT_V3);

    // V3 client resets only exist with tls-crypt-v2 and carry a wrapped client key.
    if opcode == OP_CONTROL_HARD_RESET_CLIENT_V3 {
        return parse_tls_crypt_reset(packet, true)
            .map(|net_time| Reset {
                session_id, remote_session_id: None,
                control_channel: OpenVpnControlChannel::TlsCryptV2, net_time: Some(net_time)
            });
    }

    // Plain control channel: opcode, session id, ack array, packet id.
    if let Some(remote_session_id) = parse_control_tail(packet, LEN_CONTROL_HEADER, is_client) {
        return Some(Reset {
            session_id, remote_session_id, control_channel: OpenVpnControlChannel::Plain, net_time: None
        });
    }

    // tls-auth: opcode, session id, HMAC, replay protection, ack array, packet id.
    for hmac_size in TLS_AUTH_HMAC_SIZES {
        let replay_offset = LEN_CONTROL_HEADER + hmac_size;
        let net_time = match replay_protection_net_time(packet, replay_offset) {
            Some(t) => t,
            None => continue
        };

        if let Some(remote_session_id) = parse_control_tail(packet, replay_offset + LEN_REPLAY, is_client) {
            return Some(Reset {
                session_id, remote_session_id,
                control_channel: OpenVpnControlChannel::TlsAuth { hmac_size }, net_time: Some(net_time)
            });
        }
    }

    // tls-crypt: opcode, session id, replay protection, tag, encrypted remainder.
    parse_tls_crypt_reset(packet, false)
        .map(|net_time| Reset {
            session_id, remote_session_id: None,
            control_channel: OpenVpnControlChannel::TlsCrypt, net_time: Some(net_time)
        })
}

fn parse_control_tail(packet: &[u8], offset: usize, is_client: bool) -> Option<Option<u64>> {
    if packet.len() < offset + 1 + 4 {
        return None;
    }

    let ack_count = packet[offset] as usize;
    let (packet_id_offset, remote_session_id) = match ack_count {
        0 => (offset + 1, None),
        1 if !is_client => {
            let acks_offset = offset + 1;
            if packet.len() < acks_offset + 4 + 8 + 4 {
                return None;
            }

            if BigEndian::read_u32(&packet[acks_offset..acks_offset + 4]) != 0 {
                return None;
            }

            let remote = BigEndian::read_u64(&packet[acks_offset + 4..acks_offset + 12]);
            if remote == 0 {
                return None;
            }

            (acks_offset + 12, Some(remote))
        },
        _ => return None
    };

    if packet.len() != packet_id_offset + 4 {
        return None;
    }

    if BigEndian::read_u32(&packet[packet_id_offset..packet_id_offset + 4]) != 0 {
        return None;
    }

    Some(remote_session_id)
}

fn parse_tls_crypt_reset(packet: &[u8], with_wrapped_client_key: bool) -> Option<u32> {
    if packet.len() < LEN_TLS_CRYPT_MIN {
        return None;
    }
    let net_time = replay_protection_net_time(packet, LEN_CONTROL_HEADER)?;

    if with_wrapped_client_key {
        // The wrapped client key is appended and ends with its own big-endian length.
        let wkc_length = BigEndian::read_u16(&packet[packet.len() - 2..]) as usize;
        if wkc_length < LEN_WKC_MIN || wkc_length > packet.len() - LEN_TLS_CRYPT_MIN {
            return None;
        }
    }

    Some(net_time)
}

fn replay_protection_net_time(packet: &[u8], offset: usize) -> Option<u32> {
    if packet.len() < offset + LEN_REPLAY {
        return None;
    }

    let packet_id = BigEndian::read_u32(&packet[offset..offset + 4]);
    if packet_id == 0 || packet_id > MAX_RESET_REPLAY_PACKET_ID {
        return None;
    }

    let net_time = BigEndian::read_u32(&packet[offset + 4..offset + 8]);
    if (Utc::now().timestamp() - net_time as i64).abs() > MAX_CLOCK_SKEW_SECONDS {
        return None;
    }

    Some(net_time)
}

pub fn observe_hard_reset(payload: &[u8]) -> Option<VpnHandshakeMessage> {
    let first = *payload.first()?;
    if !is_hard_reset_opcode(first >> 3) || first & 0x07 != 0 {
        return None;
    }

    let p = classify(payload)?;
    let reset = p.reset?;

    Some(VpnHandshakeMessage::OpenVpnHardReset {
        opcode: p.opcode,
        control_channel: reset.control_channel,
        session_id: reset.session_id,
        remote_session_id: reset.remote_session_id,
        clock_skew_seconds: reset.net_time.map(|t| t as i64 - Utc::now().timestamp())
    })
}

pub fn observe_data(payload: &[u8]) -> Option<(u32, u8)> {
    let first = *payload.first()?;
    if first >> 3 != OP_DATA_V2 {
        return None;
    }

    classify(payload).and_then(|p| p.peer_id.map(|id| (id, p.key_id)))
}

fn is_client_reset_opcode(opcode: u8) -> bool {
    matches!(opcode, OP_CONTROL_HARD_RESET_CLIENT_V1
        | OP_CONTROL_HARD_RESET_CLIENT_V2
        | OP_CONTROL_HARD_RESET_CLIENT_V3)
}

pub fn tag_udp(conversation: &UdpConversation) -> Option<(VpnTunnel, Vec<TaggerCommand>)> {
    let inherited = conversation.tags.contains(&L7Tag::OpenVPN);

    let (c2s, s2c) = match (scan_datagrams(&conversation.datagrams_client_to_server),
                            scan_datagrams(&conversation.datagrams_server_to_client)) {
        (Some(c2s), Some(s2c)) => (c2s, s2c),
        _ if inherited => (Vec::new(), Vec::new()),
        _ => return None
    };

    if c2s.is_empty() && s2c.is_empty() && !inherited {
        return None;
    }

    // Hard resets recorded by the UDP table on every datagram, regardless of buffer state.
    let observed = observed_handshake(
        conversation,
        |m| matches!(m, VpnHandshakeMessage::OpenVpnHardReset { opcode, .. } if is_client_reset_opcode(*opcode)),
        |m| matches!(m, VpnHandshakeMessage::OpenVpnHardReset { opcode, .. } if !is_client_reset_opcode(*opcode))
    );

    let client_reset_c2s = c2s.iter().any(is_client_reset) || observed.initiation_c2s;
    let client_reset_s2c = s2c.iter().any(is_client_reset) || observed.initiation_s2c;
    let server_reset_seen = c2s.iter().any(is_server_reset) || s2c.iter().any(is_server_reset)
        || observed.response_seen();
    let client_reset_seen = client_reset_c2s || client_reset_s2c;

    if !resets_belong_together(c2s.iter().chain(s2c.iter())) {
        tracemark!("OpenVPN: Server reset does not acknowledge client session.");
        return None;
    }

    let handshake_status = if client_reset_seen && server_reset_seen {
        VpnHandshakeStatus::Complete
    } else if client_reset_seen || server_reset_seen {
        VpnHandshakeStatus::Partial
    } else if is_consistent_data_stream(&c2s, &s2c) {
        VpnHandshakeStatus::NotObserved
    } else {
        tracemark!("OpenVPN: No handshake and no consistent data stream.");
        return None;
    };

    // The peer sending the client hard reset is the tunnel client.
    let reversed = client_reset_s2c && !client_reset_c2s;

    let mut types = message_types(c2s.iter().chain(s2c.iter()));
    for o in &conversation.vpn.handshakes {
        if let VpnHandshakeMessage::OpenVpnHardReset { opcode, .. } = o.message {
            if let Some(name) = opcode_name(opcode) {
                if !types.iter().any(|t| t == name) {
                    types.push(name.to_string());
                }
            }
        }
    }

    let mut tunnel = build_udp_tunnel(
        conversation,
        VpnType::OpenVPN,
        handshake_status,
        types,
        reversed
    );

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

pub fn tag_tcp(cts: &[u8], stc: &[u8], session: &TcpSession) -> Option<VpnTunnel> {
    let c2s = scan_stream(cts)?;
    let s2c = scan_stream(stc)?;

    // TCP sessions are tracked from their start, so the first client packet is the hard reset.
    let first = match c2s.first() {
        Some(p) => p,
        None => {
            tracemark!("OpenVPN: No client packets.");
            return None;
        }
    };

    if !is_client_reset(first) || first.key_id != 0 {
        tracemark!("OpenVPN: First client packet is not a hard reset.");
        return None;
    }

    let handshake_status = match s2c.first() {
        Some(p) if is_server_reset(p) => {
            if !resets_belong_together([first, p].into_iter()) {
                tracemark!("OpenVPN: Server reset does not acknowledge client session.");
                return None;
            }
            VpnHandshakeStatus::Complete
        },
        Some(_) => {
            tracemark!("OpenVPN: First server packet is not a hard reset.");
            return None;
        },
        None => VpnHandshakeStatus::Partial
    };

    Some(build_tcp_tunnel(
        session,
        VpnType::OpenVPN,
        handshake_status,
        message_types(c2s.iter().chain(s2c.iter()))
    ))
}

fn add_handshake_details(conversation: &UdpConversation, tunnel: &mut VpnTunnel, reversed: bool) {
    let obs = &conversation.vpn;

    let is_client = |address: &std::net::IpAddr, port: u16| -> bool {
        let from_source = *address == conversation.source_address && port == conversation.source_port;
        from_source != reversed
    };

    for h in &obs.handshakes {
        if let VpnHandshakeMessage::OpenVpnHardReset {
            opcode, control_channel, session_id, remote_session_id, clock_skew_seconds
        } = &h.message {
            let client = is_client_reset_opcode(*opcode);

            tunnel.details.insert("Control channel".to_string(), control_channel.name());

            let role = if client { "Client" } else { "Server" };
            tunnel.details.insert(format!("{} session ID", role), format!("{:016x}", session_id));

            if client {
                tunnel.initiation_ids.push(*session_id);
            } else if let Some(remote) = remote_session_id {
                tunnel.response_to_ids.push(*remote);
            }

            if let Some(skew) = clock_skew_seconds {
                let who = if is_client(&h.sender_address, h.sender_port) { "Client" } else { "Server" };
                tunnel.details.insert(format!("{} clock skew (seconds)", who), skew.to_string());
            }
        }
    }

    if let Some(peer_id) = obs.openvpn_peer_id {
        tunnel.details.insert("Peer ID".to_string(),
                              if peer_id == PEER_ID_UNSET { "Unset".to_string() } else { peer_id.to_string() });
    }
    if let Some(key_id) = obs.openvpn_max_key_id {
        tunnel.details.insert("Key renegotiations".to_string(), key_id.to_string());
    }
    tunnel.details.insert("Data packets observed".to_string(), obs.openvpn_data_datagrams.to_string());
}

fn resets_belong_together<'a>(packets: impl Iterator<Item = &'a Packet>) -> bool {
    let mut client_session_ids = Vec::new();
    let mut acked_session_ids = Vec::new();

    for p in packets {
        if let Some(reset) = p.reset {
            if is_client_reset(p) {
                client_session_ids.push(reset.session_id);
            } else if let Some(remote) = reset.remote_session_id {
                acked_session_ids.push(remote);
            }
        }
    }

    if client_session_ids.is_empty() || acked_session_ids.is_empty() {
        return true;
    }

    acked_session_ids.iter().all(|acked| client_session_ids.contains(acked))
}

fn is_consistent_data_stream(c2s: &[Packet], s2c: &[Packet]) -> bool {
    if c2s.is_empty() || s2c.is_empty() || c2s.len() + s2c.len() < MIN_MIDSTREAM_DATA_PACKETS {
        return false;
    }

    for direction in [c2s, s2c] {
        let first = direction[0];
        if first.opcode != OP_DATA_V2 {
            return false;
        }
        if direction.iter().any(|p| p.first_byte != first.first_byte || p.peer_id != first.peer_id) {
            return false;
        }
    }

    let peer_id = match c2s[0].peer_id {
        Some(id) => id,
        None => return false
    };

    if s2c[0].peer_id != Some(peer_id) {
        return false;
    }

    peer_id <= PEER_ID_ASSIGNED_MAX || peer_id == PEER_ID_UNSET
}

fn message_types<'a>(packets: impl Iterator<Item = &'a Packet>) -> Vec<String> {
    let mut types: Vec<String> = Vec::new();
    for p in packets {
        if let Some(name) = opcode_name(p.opcode) {
            if !types.iter().any(|t| t == name) {
                types.push(name.to_string());
            }
        }
    }
    types
}

// Returns None if any datagram in the buffer is not an OpenVPN packet.
fn scan_datagrams(datagrams: &VecDeque<Vec<u8>>) -> Option<Vec<Packet>> {
    let mut packets = Vec::with_capacity(datagrams.len());

    for datagram in datagrams {
        match classify(datagram) {
            Some(p) => packets.push(p),
            None => {
                tracemark!("OpenVPN: Datagram is not an OpenVPN packet.");
                return None;
            }
        }
    }

    Some(packets)
}

fn scan_stream(data: &[u8]) -> Option<Vec<Packet>> {
    let mut packets = Vec::new();
    let mut cursor = 0;

    while cursor + 2 <= data.len() {
        let len = BigEndian::read_u16(&data[cursor..cursor + 2]) as usize;
        cursor += 2;

        if len == 0 || len > MAX_TCP_FRAME {
            tracemark!("OpenVPN: Invalid TCP frame length.");
            return None;
        }

        if cursor + len > data.len() {
            // Packet not fully in buffer.
            break;
        }

        match classify(&data[cursor..cursor + len]) {
            Some(p) => packets.push(p),
            None => {
                tracemark!("OpenVPN: TCP frame is not an OpenVPN packet.");
                return None;
            }
        }

        cursor += len;
    }

    Some(packets)
}

#[cfg(test)]
mod tests {
    use super::*;

    const CLIENT_SID: u64 = 0x1122334455667788;
    const SERVER_SID: u64 = 0x8877665544332211;

    fn opcode_byte(opcode: u8, key_id: u8) -> u8 {
        (opcode << 3) | key_id
    }

    // Plain control channel client hard reset.
    fn client_reset() -> Vec<u8> {
        let mut p = vec![0u8; LEN_CONTROL_MIN];
        p[0] = opcode_byte(OP_CONTROL_HARD_RESET_CLIENT_V2, 0);
        BigEndian::write_u64(&mut p[1..9], CLIENT_SID);
        p
    }

    // Plain control channel server hard reset acknowledging the client's packet zero.
    fn server_reset(acked_session_id: u64) -> Vec<u8> {
        let mut p = vec![0u8; 1 + 8 + 1 + 4 + 8 + 4];
        p[0] = opcode_byte(OP_CONTROL_HARD_RESET_SERVER_V2, 0);
        BigEndian::write_u64(&mut p[1..9], SERVER_SID);
        p[9] = 1;
        BigEndian::write_u64(&mut p[14..22], acked_session_id);
        p
    }

    fn write_replay(p: &mut [u8], offset: usize, packet_id: u32, time: i64) {
        BigEndian::write_u32(&mut p[offset..offset + 4], packet_id);
        BigEndian::write_u32(&mut p[offset + 4..offset + 8], time as u32);
    }

    // tls-auth client hard reset with a SHA256 HMAC.
    fn tls_auth_client_reset(time: i64) -> Vec<u8> {
        let hmac = 32;
        let mut p = vec![0xABu8; LEN_CONTROL_HEADER + hmac + LEN_REPLAY + 1 + 4];
        p[0] = opcode_byte(OP_CONTROL_HARD_RESET_CLIENT_V2, 0);
        BigEndian::write_u64(&mut p[1..9], CLIENT_SID);
        write_replay(&mut p, LEN_CONTROL_HEADER + hmac, 1, time);
        let tail = LEN_CONTROL_HEADER + hmac + LEN_REPLAY;
        p[tail] = 0;
        p[tail + 1..].fill(0);
        p
    }

    // tls-crypt client hard reset: everything after the tag is encrypted.
    fn tls_crypt_client_reset(time: i64) -> Vec<u8> {
        let mut p = vec![0xCDu8; LEN_TLS_CRYPT_MIN + 10];
        p[0] = opcode_byte(OP_CONTROL_HARD_RESET_CLIENT_V2, 0);
        BigEndian::write_u64(&mut p[1..9], CLIENT_SID);
        write_replay(&mut p, LEN_CONTROL_HEADER, 1, time);
        p
    }

    // tls-crypt-v2 client hard reset with a wrapped client key appended.
    fn tls_crypt_v2_client_reset(time: i64) -> Vec<u8> {
        let wkc_len = LEN_WKC_MIN + 20;
        let mut p = vec![0xEFu8; LEN_TLS_CRYPT_MIN + 10 + wkc_len];
        p[0] = opcode_byte(OP_CONTROL_HARD_RESET_CLIENT_V3, 0);
        BigEndian::write_u64(&mut p[1..9], CLIENT_SID);
        write_replay(&mut p, LEN_CONTROL_HEADER, 1, time);
        let len = p.len();
        BigEndian::write_u16(&mut p[len - 2..], wkc_len as u16);
        p
    }

    fn data_v2(key_id: u8, peer_id: u32, len: usize) -> Vec<u8> {
        let mut p = vec![0u8; len];
        p[0] = opcode_byte(OP_DATA_V2, key_id);
        BigEndian::write_u24(&mut p[1..4], peer_id);
        p
    }

    fn framed(packets: &[Vec<u8>]) -> Vec<u8> {
        let mut out = Vec::new();
        for p in packets {
            let mut len = [0u8; 2];
            BigEndian::write_u16(&mut len, p.len() as u16);
            out.extend_from_slice(&len);
            out.extend_from_slice(p);
        }
        out
    }

    #[test]
    fn classifies_plain_hard_resets() {
        let p = classify(&client_reset()).unwrap();
        assert!(is_client_reset(&p));
        assert_eq!(p.key_id, 0);
        let reset = p.reset.unwrap();
        assert_eq!((reset.session_id, reset.remote_session_id, reset.control_channel),
                   (CLIENT_SID, None, OpenVpnControlChannel::Plain));

        let p = classify(&server_reset(CLIENT_SID)).unwrap();
        assert!(is_server_reset(&p));
        let reset = p.reset.unwrap();
        assert_eq!((reset.session_id, reset.remote_session_id), (SERVER_SID, Some(CLIENT_SID)));
    }

    #[test]
    fn classifies_tls_auth_and_tls_crypt_hard_resets() {
        let now = Utc::now().timestamp();

        let reset = classify(&tls_auth_client_reset(now)).unwrap().reset.unwrap();
        assert_eq!(reset.session_id, CLIENT_SID);
        assert_eq!(reset.control_channel, OpenVpnControlChannel::TlsAuth { hmac_size: 32 });
        assert_eq!(reset.net_time, Some(now as u32));

        let reset = classify(&tls_crypt_client_reset(now)).unwrap().reset.unwrap();
        assert_eq!(reset.control_channel, OpenVpnControlChannel::TlsCrypt);

        let p = classify(&tls_crypt_v2_client_reset(now)).unwrap();
        assert_eq!(p.opcode, OP_CONTROL_HARD_RESET_CLIENT_V3);
        assert_eq!(p.reset.unwrap().control_channel, OpenVpnControlChannel::TlsCryptV2);
    }

    #[test]
    fn rejects_hard_resets_with_implausible_time_or_structure() {
        let old = Utc::now().timestamp() - 365 * 24 * 60 * 60;
        assert_eq!(classify(&tls_auth_client_reset(old)), None);
        assert_eq!(classify(&tls_crypt_client_reset(old)), None);
        assert_eq!(classify(&tls_crypt_v2_client_reset(old)), None);

        // Non-zero key id.
        let mut p = client_reset();
        p[0] = opcode_byte(OP_CONTROL_HARD_RESET_CLIENT_V2, 1);
        assert_eq!(classify(&p), None);

        // Zero session id.
        let mut p = client_reset();
        p[1..9].fill(0);
        assert_eq!(classify(&p), None);

        // Non-zero packet id.
        let mut p = client_reset();
        p[13] = 1;
        assert_eq!(classify(&p), None);

        // Client reset acking something.
        let mut p = server_reset(CLIENT_SID);
        p[0] = opcode_byte(OP_CONTROL_HARD_RESET_CLIENT_V2, 0);
        assert_eq!(classify(&p), None);

        // Trailing garbage.
        let mut p = client_reset();
        p.push(0);
        assert_eq!(classify(&p), None);
    }

    #[test]
    fn rejects_lookalike_traffic() {
        // Tailscale discovery packets start with "TS" and a speech balloon emoji, followed by
        // a 32 byte key and a 24 byte nonce. The first byte reads as a V3 client hard reset.
        let mut disco = vec![0x54, 0x53, 0xF0, 0x9F, 0x92, 0xAC];
        disco.extend_from_slice(&[0x5A; 32 + 24 + 40]);
        assert_eq!(disco[0] >> 3, OP_CONTROL_HARD_RESET_CLIENT_V3);
        assert_eq!(classify(&disco), None);

        // QUIC short header packet with a first byte in the server hard reset range.
        let mut quic = vec![0x41];
        quic.extend_from_slice(&[0x77; 60]);
        assert_eq!(quic[0] >> 3, OP_CONTROL_HARD_RESET_SERVER_V2);
        assert_eq!(classify(&quic), None);
    }

    #[test]
    fn rejects_short_control_and_unknown_opcodes() {
        let mut short = vec![0u8; 10];
        short[0] = opcode_byte(OP_CONTROL_V1, 0);
        assert_eq!(classify(&short), None);
        assert_eq!(classify(&[0x00; 20]), None);
        assert_eq!(classify(&[(12 << 3); 20]), None);
        assert_eq!(classify(&[]), None);
    }

    #[test]
    fn server_reset_must_acknowledge_client_session() {
        let client = classify(&client_reset()).unwrap();
        let matching = classify(&server_reset(CLIENT_SID)).unwrap();
        let foreign = classify(&server_reset(0x0F0F0F0F0F0F0F0F)).unwrap();

        assert!(resets_belong_together([&client, &matching].into_iter()));
        assert!(!resets_belong_together([&client, &foreign].into_iter()));

        // Without a visible client reset there is nothing to compare against.
        assert!(resets_belong_together([&foreign].into_iter()));
    }

    #[test]
    fn reads_data_v2_peer_id() {
        let p = classify(&data_v2(1, 7, 60)).unwrap();
        assert_eq!(p.opcode, OP_DATA_V2);
        assert_eq!(p.key_id, 1);
        assert_eq!(p.peer_id, Some(7));
    }

    #[test]
    fn consistent_data_stream_requires_matching_peer_ids() {
        let a = classify(&data_v2(1, 3, 60)).unwrap();
        let b = classify(&data_v2(1, 3, 90)).unwrap();
        assert!(is_consistent_data_stream(&[a, b], &[a, b]));

        let other = classify(&data_v2(1, 4, 60)).unwrap();
        assert!(!is_consistent_data_stream(&[a, b], &[other, other]));

        let random = classify(&data_v2(1, 0x123456, 60)).unwrap();
        assert!(!is_consistent_data_stream(&[random, random], &[random, random]));

        // Too few packets.
        assert!(!is_consistent_data_stream(&[a], &[a]));
    }

    #[test]
    fn walks_tcp_stream() {
        let mut control = vec![0u8; LEN_CONTROL_MIN];
        control[0] = opcode_byte(OP_CONTROL_V1, 0);

        let stream = framed(&[client_reset(), control]);
        let packets = scan_stream(&stream).unwrap();
        assert_eq!(packets.len(), 2);
        assert!(is_client_reset(&packets[0]));
        assert_eq!(packets[1].opcode, OP_CONTROL_V1);
    }

    #[test]
    fn tcp_stream_ignores_truncated_trailing_frame() {
        let mut stream = framed(&[client_reset()]);
        stream.extend_from_slice(&[0x00, 0x40, 0x20]); // Length 64 but only one byte present.
        let packets = scan_stream(&stream).unwrap();
        assert_eq!(packets.len(), 1);
    }

    #[test]
    fn tcp_stream_rejects_non_openvpn_frames() {
        let stream = framed(&[vec![0xFF; 20]]);
        assert!(scan_stream(&stream).is_none());

        // A TLS record reads as a length prefix larger than the buffer: no complete packets.
        assert!(scan_stream(&[0x16, 0x03, 0x01, 0x00, 0x05, 0x01, 0x00, 0x00, 0x01, 0x00]).unwrap().is_empty());
    }
}
