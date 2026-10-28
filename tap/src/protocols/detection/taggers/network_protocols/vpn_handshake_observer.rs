use std::net::IpAddr;
use byteorder::{ByteOrder, LittleEndian};
use crate::protocols::detection::taggers::network_protocols::{openvpn_tagger, wireguard_tagger};

/*
 * The UDP table only buffers the first few hundred bytes of each conversation for the taggers.
 * VPN handshakes of long-lived tunnels (WireGuard rekeys every two minutes) happen long after
 * those buffers are full and would never be seen. The UDP table runs these cheap
 * checks on every datagram and records what it finds on the conversation, independent of the
 * buffers. The taggers combine both.
 */

// Handshake observations kept per conversation. Oldest are dropped first.
pub const MAX_HANDSHAKE_OBSERVATIONS: usize = 16;
// WireGuard transport sessions tracked per conversation. Oldest are dropped first.
pub const MAX_TRANSPORT_SESSIONS: usize = 8;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OpenVpnControlChannel {
    Plain,
    TlsAuth { hmac_size: usize },
    TlsCrypt,
    TlsCryptV2
}

impl OpenVpnControlChannel {
    pub fn name(&self) -> String {
        match self {
            OpenVpnControlChannel::Plain => "Plain (unauthenticated)".to_string(),
            OpenVpnControlChannel::TlsAuth { hmac_size } => format!("tls-auth ({} byte HMAC)", hmac_size),
            OpenVpnControlChannel::TlsCrypt => "tls-crypt".to_string(),
            OpenVpnControlChannel::TlsCryptV2 => "tls-crypt-v2".to_string()
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum VpnHandshakeMessage {
    WireGuardHandshakeInitiation {
        sender_index: u32,
        cookie_used: bool
    },
    WireGuardHandshakeResponse {
        sender_index: u32,
        receiver_index: u32,
        cookie_used: bool
    },
    OpenVpnHardReset {
        opcode: u8,
        control_channel: OpenVpnControlChannel,
        session_id: u64,
        remote_session_id: Option<u64>,
        clock_skew_seconds: Option<i64>
    }
}

#[derive(Debug, Clone)]
pub struct VpnHandshakeObservation {
    pub message: VpnHandshakeMessage,
    pub sender_address: IpAddr,
    pub sender_port: u16
}

#[derive(Debug, Clone)]
pub struct WireGuardTransportSession {
    pub receiver_index: u32,
    pub sender_address: IpAddr,
    pub sender_port: u16,
    pub first_counter: u64,
    pub last_counter: u64,
    pub datagrams: u64
}

#[derive(Debug, Clone)]
pub struct TailscaleDiscoKey {
    pub sender_address: IpAddr,
    pub sender_port: u16,
    pub key: String
}

#[derive(Debug, Clone, Default)]
pub struct VpnObservations {
    pub handshakes: Vec<VpnHandshakeObservation>,
    pub wireguard_sessions: Vec<WireGuardTransportSession>,
    pub tailscale_disco_keys: Vec<TailscaleDiscoKey>,
    pub openvpn_peer_id: Option<u32>,
    pub openvpn_max_key_id: Option<u8>,
    pub openvpn_data_datagrams: u64
}

impl VpnObservations {

    pub fn observe(&mut self, payload: &[u8], sender_address: IpAddr, sender_port: u16) {
        let first = match payload.first() {
            Some(b) => *b,
            None => return
        };

        match first {
            // WireGuard handshake messages.
            1 | 2 => {
                if let Some(message) = wireguard_tagger::observe_handshake(payload) {
                    self.record_handshake(message, sender_address, sender_port);
                }
            },
            // WireGuard transport data.
            4 => {
                if let Some((receiver_index, counter)) = wireguard_tagger::observe_transport(payload) {
                    self.record_transport(receiver_index, counter, sender_address, sender_port);
                }
            },
            _ => {}
        }

        if let Some(key) = wireguard_tagger::observe_tailscale_disco(payload) {
            if !self.tailscale_disco_keys.iter().any(|k| k.sender_address == sender_address && k.sender_port == sender_port) {
                self.tailscale_disco_keys.push(TailscaleDiscoKey { sender_address, sender_port, key });
                self.tailscale_disco_keys.truncate(2);
            }
        }

        if let Some(message) = openvpn_tagger::observe_hard_reset(payload) {
            self.record_handshake(message, sender_address, sender_port);
        }

        if let Some((peer_id, key_id)) = openvpn_tagger::observe_data(payload) {
            self.openvpn_data_datagrams += 1;
            if self.openvpn_peer_id.is_none() {
                self.openvpn_peer_id = Some(peer_id);
            }
            if self.openvpn_max_key_id.map_or(true, |k| key_id > k) {
                self.openvpn_max_key_id = Some(key_id);
            }
        }
    }

    fn record_handshake(&mut self, message: VpnHandshakeMessage, sender_address: IpAddr, sender_port: u16) {
        self.handshakes.push(VpnHandshakeObservation { message, sender_address, sender_port });

        if self.handshakes.len() > MAX_HANDSHAKE_OBSERVATIONS {
            self.handshakes.remove(0);
        }
    }

    fn record_transport(&mut self, receiver_index: u32, counter: u64, sender_address: IpAddr, sender_port: u16) {
        if let Some(s) = self.wireguard_sessions.iter_mut().find(|s| s.receiver_index == receiver_index) {
            s.first_counter = s.first_counter.min(counter);
            s.last_counter = s.last_counter.max(counter);
            s.datagrams += 1;
            return;
        }

        self.wireguard_sessions.push(WireGuardTransportSession {
            receiver_index,
            sender_address,
            sender_port,
            first_counter: counter,
            last_counter: counter,
            datagrams: 1
        });

        if self.wireguard_sessions.len() > MAX_TRANSPORT_SESSIONS {
            self.wireguard_sessions.remove(0);
        }
    }

}

pub fn read_u32_le(data: &[u8]) -> u32 {
    LittleEndian::read_u32(data)
}

pub fn read_u64_le(data: &[u8]) -> u64 {
    LittleEndian::read_u64(data)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn address() -> IpAddr {
        "10.0.0.1".parse().unwrap()
    }

    #[test]
    fn observes_wireguard_handshakes() {
        let mut o = VpnObservations::default();

        let mut init = vec![0u8; 148];
        init[0] = 1;
        init[4..8].copy_from_slice(&[0x11, 0x22, 0x33, 0x44]);
        o.observe(&init, address(), 1);

        let mut response = vec![0u8; 92];
        response[0] = 2;
        response[8..12].copy_from_slice(&[0x11, 0x22, 0x33, 0x44]);
        o.observe(&response, address(), 2);

        assert_eq!(o.handshakes.len(), 2);
        assert!(matches!(o.handshakes[0].message,
            VpnHandshakeMessage::WireGuardHandshakeInitiation { sender_index: 0x44332211, .. }));
        assert!(matches!(o.handshakes[1].message,
            VpnHandshakeMessage::WireGuardHandshakeResponse { receiver_index: 0x44332211, .. }));

        // Wrong length.
        let mut short = vec![0u8; 100];
        short[0] = 1;
        o.observe(&short, address(), 1);
        assert_eq!(o.handshakes.len(), 2);
    }

    #[test]
    fn tracks_wireguard_transport_counters() {
        let mut o = VpnObservations::default();

        for counter in [5u64, 6, 9] {
            let mut t = vec![0u8; 64];
            t[0] = 4;
            t[4..8].copy_from_slice(&[1, 0, 0, 0]);
            t[8..16].copy_from_slice(&counter.to_le_bytes());
            o.observe(&t, address(), 1);
        }

        assert_eq!(o.wireguard_sessions.len(), 1);
        let s = &o.wireguard_sessions[0];
        assert_eq!(s.receiver_index, 1);
        assert_eq!(s.first_counter, 5);
        assert_eq!(s.last_counter, 9);
        assert_eq!(s.datagrams, 3);
    }

    #[test]
    fn records_tailscale_disco_key_once_per_side() {
        let mut o = VpnObservations::default();
        let mut disco = vec![0x54, 0x53, 0xF0, 0x9F, 0x92, 0xAC];
        disco.extend_from_slice(&[0x5A; 32 + 24 + 40]);

        o.observe(&disco, address(), 1);
        o.observe(&disco, address(), 1);
        assert_eq!(o.tailscale_disco_keys.len(), 1);
        assert_eq!(o.tailscale_disco_keys[0].key.len(), 44);
    }

    #[test]
    fn ignores_other_traffic() {
        let mut o = VpnObservations::default();
        o.observe(&[], address(), 1);
        o.observe(&[0x16, 0x03, 0x01, 0x00, 0x05, 0x01, 0x00, 0x00, 0x01, 0x00], address(), 1);
        assert!(o.handshakes.is_empty());
        assert!(o.wireguard_sessions.is_empty());
    }

    #[test]
    fn keeps_a_bounded_number_of_observations() {
        let mut o = VpnObservations::default();
        let mut init = vec![0u8; 148];
        init[0] = 1;

        for _ in 0..(MAX_HANDSHAKE_OBSERVATIONS + 5) {
            o.observe(&init, address(), 1);
        }

        assert_eq!(o.handshakes.len(), MAX_HANDSHAKE_OBSERVATIONS);
    }
}
