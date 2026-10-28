use std::collections::BTreeMap;
use crate::protocols::detection::taggers::network_protocols::vpn_handshake_observer::VpnHandshakeMessage;
use crate::protocols::parsers::l4_key::L4Key;
use crate::protocols::parsers::tcp::tcp_tools::determine_tcp_session_state;
use crate::state::tables::tcp_table::TcpSession;
use crate::state::tables::udp_table::UdpConversation;
use crate::wired::packets::{GenericConnectionStatus, VpnHandshakeStatus, VpnTransport, VpnTunnel, VpnType};

/*
 * Builds a tunnel record from a UDP conversation. If `reversed` is set, the tagger determined
 * from the handshake direction that the conversation's destination is the tunnel client.
 */
pub fn build_udp_tunnel(conversation: &UdpConversation,
                        vpn_type: VpnType,
                        handshake_status: VpnHandshakeStatus,
                        message_types: Vec<String>,
                        reversed: bool) -> VpnTunnel {
    let (source_address, source_port, source_mac, destination_address, destination_port, destination_mac) =
        if reversed {
            (conversation.destination_address, conversation.destination_port,
             conversation.destination_mac.clone(),
             conversation.source_address, conversation.source_port,
             conversation.source_mac.clone())
        } else {
            (conversation.source_address, conversation.source_port,
             conversation.source_mac.clone(),
             conversation.destination_address, conversation.destination_port,
             conversation.destination_mac.clone())
        };

    let (connection_status, terminated_at) = match conversation.end_time {
        Some(end_time) => (GenericConnectionStatus::Inactive, Some(end_time)),
        None => (GenericConnectionStatus::Active, None)
    };

    VpnTunnel {
        vpn_type,
        transport: VpnTransport::UDP,
        handshake_status,
        message_types,
        provider: None,
        server_name: None,
        details: BTreeMap::new(),
        initiation_ids: Vec::new(),
        response_to_ids: Vec::new(),
        connection_status,
        session_key: L4Key::new(source_address, source_port, destination_address, destination_port),
        conversation_start_time: conversation.start_time,
        conversation_bytes: conversation.bytes_count_rx + conversation.bytes_count_tx,
        source_mac,
        destination_mac,
        source_address,
        destination_address,
        source_port,
        destination_port,
        terminated_at,
        most_recent_segment_time: conversation.most_recent_segment_time
    }
}

pub fn build_tcp_tunnel(session: &TcpSession,
                        vpn_type: VpnType,
                        handshake_status: VpnHandshakeStatus,
                        message_types: Vec<String>) -> VpnTunnel {
    let (connection_status, terminated_at) = determine_tcp_session_state(session);

    VpnTunnel {
        vpn_type,
        transport: VpnTransport::TCP,
        handshake_status,
        message_types,
        provider: None,
        server_name: None,
        details: BTreeMap::new(),
        initiation_ids: Vec::new(),
        response_to_ids: Vec::new(),
        connection_status,
        session_key: session.session_key.clone(),
        conversation_start_time: session.start_time,
        conversation_bytes: session.bytes_count(),
        source_mac: session.source_mac.clone(),
        destination_mac: session.destination_mac.clone(),
        source_address: session.source_address,
        destination_address: session.destination_address,
        source_port: session.source_port,
        destination_port: session.destination_port,
        terminated_at,
        most_recent_segment_time: session.most_recent_segment_time
    }
}

/*
 * Handshake messages the UDP table observed on a conversation, independent of the datagram
 * buffers, mapped onto the conversation's current client/server orientation.
 */
pub struct ObservedHandshake {
    pub initiation_c2s: bool,
    pub initiation_s2c: bool,
    pub response_c2s: bool,
    pub response_s2c: bool
}

impl ObservedHandshake {
    pub fn response_seen(&self) -> bool {
        self.response_c2s || self.response_s2c
    }
}

pub fn observed_handshake(conversation: &UdpConversation,
                          is_initiation: impl Fn(&VpnHandshakeMessage) -> bool,
                          is_response: impl Fn(&VpnHandshakeMessage) -> bool) -> ObservedHandshake {
    let mut observed = ObservedHandshake {
        initiation_c2s: false, initiation_s2c: false, response_c2s: false, response_s2c: false
    };

    for o in &conversation.vpn.handshakes {
        let from_source = o.sender_address == conversation.source_address
            && o.sender_port == conversation.source_port;

        if is_initiation(&o.message) {
            if from_source {
                observed.initiation_c2s = true;
            } else {
                observed.initiation_s2c = true;
            }
        } else if is_response(&o.message) {
            if from_source {
                observed.response_c2s = true;
            } else {
                observed.response_s2c = true;
            }
        }
    }

    observed
}

/*
 * A UDP conversation that inherited a VPN tag from its predecessor on the same 5-tuple (see
 * the UDP table) but whose own buffered datagrams did not allow a fresh identification. The
 * tunnel keeps being tracked with the conversation's current statistics.
 */
pub fn continuation(conversation: &UdpConversation) -> Option<VpnTunnel> {
    use crate::protocols::detection::l7_tagger::L7Tag;

    let vpn_type = if conversation.tags.contains(&L7Tag::WireGuard) {
        VpnType::WireGuard
    } else if conversation.tags.contains(&L7Tag::OpenVPN) {
        VpnType::OpenVPN
    } else {
        return None;
    };

    Some(build_udp_tunnel(conversation, vpn_type, VpnHandshakeStatus::NotObserved, vec![], false))
}
