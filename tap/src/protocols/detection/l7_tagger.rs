use std::collections::{HashMap, HashSet};
use std::panic;
use std::sync::{Arc, Mutex, MutexGuard};
use log::{debug, error};
use strum_macros::Display;
use crate::protocols::detection::l7_tagger::L7Tag::{HTTP, Unencrypted, SOCKS, SSH, RTSP, STUN, TURN, RTP, DTLS, TLS, OpenVPN, WireGuard, DERP};
use chrono::{DateTime, Utc};
use crate::state::tables::tcp_table::{TcpSession, TcpSessionState};
use crate::protocols::parsers::l4_key::L4Key;
use crate::helpers::timer::{record_timer, Timer};
use crate::messagebus::bus::Bus;
use crate::messagebus::channel_names::WiredChannelName;
use crate::metrics::Metrics;
use crate::protocols::detection::taggers::network_protocols::{derp_tagger, http_tagger, openvpn_tagger, rtsp_tagger, socks_tagger, ssh_tagger, tls_tagger, vpn_tagger_tools, wireguard_tagger};
use crate::protocols::detection::taggers::tunnel_payload::TunnelPayload;
use crate::protocols::detection::taggers::tagger_command::TaggerCommand;
use crate::protocols::parsers::{dtls_tagger, rtp_tagger, stun_tagger};
use crate::protocols::parsers::rtp_tagger::RtpStream;
use crate::state::tables::udp_table::UdpConversation;
use crate::to_pipeline;
use crate::wired::ethernet::types::tls_session::TlsSession;
use crate::wired::ethernet::types::webrtc_conversation::WebRtcConversation;
use crate::wired::packets::VpnType;

#[allow(clippy::upper_case_acronyms)]
#[derive(Debug, Display, PartialEq, Clone, Hash, Eq)]
pub enum L7Tag {
    Unencrypted,
    HTTP,
    SMTP,
    IMAP,
    POP3,
    FTP,
    Telnet,
    TLS,
    DNS,
    SSH,
    RTSP,
    SOCKS,
    DHCP4,
    NTP,
    STUN,
    TURN,
    RTP,
    DTLS,
    WebRTC,
    WireGuard,
    OpenVPN,
    DERP
}

/*
 * Everything a TCP session exposes to the taggers that can change over the lifetime of the
 * session. A session is only re-tagged when this fingerprint differs from the one recorded at
 * its previous tagging run. Idle sessions (no new segments, no state change) are skipped
 * entirely, including the segment reassembly, which is the common case for long-lived
 * connections. If you add a session field that a tagger reads, add it here.
 */
#[derive(Debug, Clone, PartialEq)]
pub struct TcpTaggingFingerprint {
    state: TcpSessionState,
    segments_count: u64,
    bytes_count: u64,
    most_recent_segment_time: DateTime<Utc>,
    end_time: Option<DateTime<Utc>>
}

impl TcpTaggingFingerprint {

    pub fn of(session: &TcpSession) -> Self {
        Self {
            state: session.state.clone(),
            segments_count: session.segments_count,
            bytes_count: session.bytes_count(),
            most_recent_segment_time: session.most_recent_segment_time,
            end_time: session.end_time
        }
    }

}

pub fn tag_tcp_sessions(sessions: &mut MutexGuard<HashMap<L4Key, TcpSession>>,
                        bus: Arc<Bus>,
                        metrics: Arc<Mutex<Metrics>>) {
    let mut tagged_count: i128 = 0;
    let mut skipped_count: i128 = 0;

    for session in sessions.values_mut() {
        let fingerprint = TcpTaggingFingerprint::of(session);
        if session.tagging_fingerprint.as_ref() == Some(&fingerprint) {
            // Nothing the taggers look at has changed since the last run.
            skipped_count += 1;
            continue;
        }

        let mut client_to_server_data: Vec<u8> = vec![];
        let mut server_to_client_data: Vec<u8> = vec![];

        for segment_data in session.segments_client_to_server.values() {
            client_to_server_data.extend(segment_data);
        }

        for segment_data in session.segments_server_to_client.values() {
            server_to_client_data.extend(segment_data);
        }

        /*
         * The taggers should always be written extremely defensively and never throw any panics.
         * However, to increase reliability, we catch and log any panics that may still occur
         * because nobody is perfect.
         */
        let result = panic::catch_unwind(|| tag_all_tcp(
            &client_to_server_data,
            &server_to_client_data,
            session,
            &bus,
            &metrics
        ));

        match result {
            Ok(tags) => {
                // We overwrite the tags because they may have changed with more segments coming in.
                session.tags = tags
            },
            Err(e) => {
                match e.downcast_ref::<&str>() { Some(s) => {
                    error!("Could not tag TCP session {:?}: {}", session.session_key, s);
                } _ => { match e.downcast_ref::<String>() { Some(s) => {
                    error!("Could not tag TCP session {:?}: {}", session.session_key, s);
                } _ => {
                    error!("Could not tag TCP session {:?}. Panicked with an unknown type.",
                        session.session_key);
                }}}}
            }
        }

        /*
         * Recorded after a panic as well: re-running the same input every cycle would only
         * repeat the error. The session is tagged again once new data or a state change arrives.
         */
        session.tagging_fingerprint = Some(fingerprint);
        tagged_count += 1;
    }

    match metrics.lock() {
        Ok(mut metrics) => {
            metrics.set_gauge("tables.tcp.sessions.tagging.tagged", tagged_count);
            metrics.set_gauge("tables.tcp.sessions.tagging.skipped", skipped_count);
        },
        Err(e) => error!("Could not acquire metrics mutex: {}", e)
    }
}


pub fn tag_udp_sessions(conversations: &mut MutexGuard<HashMap<L4Key, UdpConversation>>,
                        bus: Arc<Bus>,
                        metrics: Arc<Mutex<Metrics>>) {
    for conversation in conversations.values_mut() {
        let mut client_to_server_data: Vec<u8> = vec![];
        let mut server_to_client_data: Vec<u8> = vec![];

        for segment_data in &conversation.datagrams_client_to_server {
            client_to_server_data.extend(segment_data);
        }
        for segment_data in &conversation.datagrams_server_to_client {
            server_to_client_data.extend(segment_data);
        }


        /*
         * The taggers should always be written extremely defensively and never throw any panics.
         * However, to increase reliability, we catch and log any panics that may still occur
         * because nobody is perfect.
         */
        let result = panic::catch_unwind(|| tag_all_udp(
            &client_to_server_data,
            &server_to_client_data,
            conversation,
            &bus,
            &metrics
        ));

        match result {
            Ok((tags, commands)) => {
                // UDP tags are extended because there is tagging already fed by initial datagrams.
                conversation.tags.extend(tags);

                // Apply any commands issued by the taggers.
                for command in commands {
                    command.apply(conversation);
                }

                if conversation.tags.contains(&STUN)
                    && (conversation.tags.contains(&RTP) || conversation.tags.contains(&DTLS)) {
                    // We can safely assume the flow is WebRTC when STUN combined with RTP or DTLS.
                    conversation.tags.insert(L7Tag::WebRTC);
                }
            },
            Err(e) => {
                match e.downcast_ref::<&str>() { Some(s) => {
                    error!("Could not tag UDP session: {}", s);
                } _ => { match e.downcast_ref::<String>() { Some(s) => {
                    error!("Could not tag UDP session: {}", s);
                } _ => {
                    error!("Could not tag UDP session. Panicked with an unknown type.");
                }}}}
            }
        }
    }
}

struct TcpTaggerContext<'a> {
    session: &'a TcpSession,
    bus: &'a Arc<Bus>
}

struct TunnelMatch {
    tags: Vec<L7Tag>,
    payload: TunnelPayload
}

type TunnelTagger = fn(&[u8], &[u8], &TcpTaggerContext) -> Option<TunnelMatch>;
type ApplicationTagger = fn(&[u8], &[u8], &TcpTaggerContext) -> Option<Vec<L7Tag>>;

struct TaggerEntry<T> {
    // Used in metric names: tables.tcp.timer.sessions.tagging.<name>.<tagged|untagged>
    name: &'static str,
    tag: T
}

const TCP_TUNNEL_TAGGERS: &[TaggerEntry<TunnelTagger>] = &[
    TaggerEntry { name: "socks", tag: tag_socks },
];

/*
 * Ordered by how cheaply a tagger rejects a stream that is not its protocol, because the
 * common case is a session that matches nothing but the first entry (TLS) or nothing at all.
 */
const TCP_APPLICATION_TAGGERS: &[TaggerEntry<ApplicationTagger>] = &[
    TaggerEntry { name: "tls",  tag: tag_tls },
    TaggerEntry { name: "ssh",  tag: tag_ssh },
    TaggerEntry { name: "stun", tag: tag_stun },
    TaggerEntry { name: "openvpn", tag: tag_openvpn },
    TaggerEntry { name: "http", tag: tag_http },
    TaggerEntry { name: "rtsp", tag: tag_rtsp },
];

fn tag_all_tcp(client_to_server: &[u8],
               server_to_client: &[u8],
               session: &TcpSession,
               bus: &Arc<Bus>,
               metrics: &Arc<Mutex<Metrics>>) -> HashSet<L7Tag> {
    let context = TcpTaggerContext { session, bus };
    let mut tags = HashSet::new();

    // Tunnels. The application taggers below run on the tunneled payload.
    let (mut payload_cts, mut payload_stc) = (client_to_server, server_to_client);
    for entry in TCP_TUNNEL_TAGGERS {
        let result = timed_tcp_tagger(entry.name, metrics, || {
            (entry.tag)(client_to_server, server_to_client, &context)
        });

        if let Some(tunnel) = result {
            tags.extend(tunnel.tags);
            (payload_cts, payload_stc) = tunnel.payload.slice(client_to_server, server_to_client);
            break;
        }
    }

    // Application protocol. First match wins.
    for entry in TCP_APPLICATION_TAGGERS {
        let result = timed_tcp_tagger(entry.name, metrics, || {
            (entry.tag)(payload_cts, payload_stc, &context)
        });

        if let Some(protocol_tags) = result {
            tags.extend(protocol_tags);
            break;
        }
    }

    tags
}

fn timed_tcp_tagger<T>(name: &str,
                       metrics: &Arc<Mutex<Metrics>>,
                       tagger: impl FnOnce() -> Option<T>) -> Option<T> {
    let mut timer = Timer::new();
    let result = tagger();
    timer.stop();

    let outcome = if result.is_some() { "tagged" } else { "untagged" };
    record_timer(
        timer.elapsed_microseconds(),
        &format!("tables.tcp.timer.sessions.tagging.{}.{}", name, outcome),
        metrics
    );

    result
}

fn tag_socks(cts: &[u8], stc: &[u8], context: &TcpTaggerContext) -> Option<TunnelMatch> {
    let (socks, payload) = socks_tagger::tag(cts, stc, context.session)?;

    let len = socks.estimate_struct_size();
    to_pipeline!(
        WiredChannelName::SocksPipeline,
        context.bus.socks_pipeline.sender,
        Arc::new(socks),
        len
    );

    Some(TunnelMatch { tags: vec![SOCKS], payload })
}


fn tag_tls(cts: &[u8], stc: &[u8], context: &TcpTaggerContext) -> Option<Vec<L7Tag>> {
    let handshake = tls_tagger::tag(cts, stc)?;

    // Tailscale DERP relay connections are TLS sessions to known relay hostnames.
    let derp = derp_tagger::tag_tcp(&handshake, context.session);

    let tls = TlsSession::build(handshake, context.session);

    let len = tls.estimate_struct_size();
    to_pipeline!(
        WiredChannelName::TlsPipeline,
        context.bus.tls_pipeline.sender,
        Arc::new(tls),
        len
    );

    match derp {
        Some(tunnel) => {
            let len = tunnel.estimate_struct_size();
            to_pipeline!(
                WiredChannelName::VpnPipeline,
                context.bus.vpn_pipeline.sender,
                Arc::new(tunnel),
                len
            );

            Some(vec![TLS, DERP])
        },
        None => Some(vec![TLS])
    }
}

fn tag_ssh(cts: &[u8], stc: &[u8], context: &TcpTaggerContext) -> Option<Vec<L7Tag>> {
    let ssh = ssh_tagger::tag(cts, stc, context.session)?;

    let len = ssh.estimate_struct_size();
    to_pipeline!(
        WiredChannelName::SshPipeline,
        context.bus.ssh_pipeline.sender,
        Arc::new(ssh),
        len
    );

    Some(vec![SSH])
}

fn tag_stun(cts: &[u8], stc: &[u8], context: &TcpTaggerContext) -> Option<Vec<L7Tag>> {
    let stun = stun_tagger::tag_tcp(cts, stc, context.session)?;

    let mut tags = vec![STUN];
    if stun.is_turn {
        tags.push(TURN);
    }

    let len = stun.estimate_struct_size();
    to_pipeline!(
        WiredChannelName::StunPipeline,
        context.bus.stun_pipeline.sender,
        Arc::new(stun),
        len
    );

    Some(tags)
}

fn tag_openvpn(cts: &[u8], stc: &[u8], context: &TcpTaggerContext) -> Option<Vec<L7Tag>> {
    let tunnel = openvpn_tagger::tag_tcp(cts, stc, context.session)?;

    let len = tunnel.estimate_struct_size();
    to_pipeline!(
        WiredChannelName::VpnPipeline,
        context.bus.vpn_pipeline.sender,
        Arc::new(tunnel),
        len
    );

    Some(vec![OpenVPN])
}

fn tag_http(cts: &[u8], stc: &[u8], _context: &TcpTaggerContext) -> Option<Vec<L7Tag>> {
    http_tagger::tag(cts, stc).map(|_| vec![HTTP, Unencrypted])
}

fn tag_rtsp(cts: &[u8], stc: &[u8], context: &TcpTaggerContext) -> Option<Vec<L7Tag>> {
    let rtsp = rtsp_tagger::tag(cts, stc, context.session)?;

    let len = rtsp.estimate_struct_size();
    to_pipeline!(
        WiredChannelName::RtspPipeline,
        context.bus.rtsp_pipeline.sender,
        Arc::new(rtsp),
        len
    );

    Some(vec![RTSP, Unencrypted])
}

fn tag_all_udp(client_to_server: &[u8],
               server_to_client: &[u8],
               conversation: &UdpConversation,
               bus: &Arc<Bus>,
               metrics: &Arc<Mutex<Metrics>>) -> (HashSet<L7Tag>, Vec<TaggerCommand>) {
    let mut tags = HashSet::new();
    let mut commands = Vec::new();

    let mut stun_timer_untagged = Timer::new();
    let mut stun_timer_tagged = Timer::new();
    let mut stun_negotiation_key: Option<String> = None; // Needed in WebRTC later.
    if let Some((stun, stun_commands, stun_neg_key)) =
        stun_tagger::tag_udp(client_to_server, server_to_client, conversation) {

        stun_timer_tagged.stop();
        record_timer(stun_timer_tagged.elapsed_microseconds(),
                     "tables.udp.timer.sessions.tagging.stun.tagged", metrics);

        tags.extend([STUN]);
        if stun.is_turn { tags.extend([TURN]); }
        commands.extend(stun_commands);

        let len = stun.estimate_struct_size();
        to_pipeline!(WiredChannelName::StunPipeline, bus.stun_pipeline.sender, Arc::new(stun), len);

        stun_negotiation_key = stun_neg_key;
    } else {
        stun_timer_untagged.stop();
        record_timer(stun_timer_untagged.elapsed_microseconds(),
                     "tables.udp.timer.sessions.tagging.stun.untagged", metrics);

        tag_vpn_udp(conversation, bus, metrics, &mut tags, &mut commands);
    }

    // Taggers on conversations with recent buffer enabled.
    if conversation.capture_recent {
        let recent_c2s: Vec<Vec<u8>> =
            conversation.recent_client_to_server.iter().cloned().collect();
        let recent_s2c: Vec<Vec<u8>> =
            conversation.recent_server_to_client.iter().cloned().collect();

        // RTP. (Currently only used in WebRTC below, not in its own pipeline)
        let mut rtp_timer = Timer::new();
        let rtp_streams: Vec<RtpStream> = match rtp_tagger::tag(&recent_c2s, &recent_s2c) {
            Some(streams) => {
                rtp_timer.stop();
                record_timer(rtp_timer.elapsed_microseconds(),
                             "tables.udp.timer.sessions.tagging.rtp.tagged", metrics);
                tags.insert(RTP);

                if let Some(dir) = rtp_tagger::dominant_direction(&streams) {
                    let (address, port) = match dir {
                        rtp_tagger::RtpDirection::ClientToServer =>
                            (conversation.source_address, conversation.source_port),
                        rtp_tagger::RtpDirection::ServerToClient =>
                            (conversation.destination_address, conversation.destination_port),
                    };
                    commands.push(TaggerCommand::OrientClientTo { address, port });
                }

                streams
            }
            None => {
                rtp_timer.stop();
                record_timer(rtp_timer.elapsed_microseconds(),
                             "tables.udp.timer.sessions.tagging.rtp.untagged", metrics);
                Vec::new()
            }
        };

        // DTLS. (Currently only used in WebRTC below, not in its own pipeline)
        let mut dtls_timer = Timer::new();
        let dtls_result = dtls_tagger::tag(&recent_c2s, &recent_s2c);
        if dtls_result.is_some() {
            dtls_timer.stop();
            record_timer(dtls_timer.elapsed_microseconds(),
                         "tables.udp.timer.sessions.tagging.dtls.tagged", metrics);
            tags.insert(DTLS);
        } else {
            dtls_timer.stop();
            record_timer(dtls_timer.elapsed_microseconds(),
                         "tables.udp.timer.sessions.tagging.dtls.untagged", metrics);
        }

        // WebRTC.
        let has_rtp = !rtp_streams.is_empty();
        let has_dtls = dtls_result.is_some();
        if tags.contains(&STUN) && (has_rtp || has_dtls) {
            match &stun_negotiation_key {
                Some(key) => {
                    let webrtc = WebRtcConversation::build(
                        conversation,
                        key.clone(),
                        rtp_streams,
                        dtls_result.map(|d| d.app_data_records).unwrap_or(0),
                    );
                    let len = webrtc.estimate_struct_size();
                    to_pipeline!(WiredChannelName::WebRtcPipeline, bus.webrtc_pipeline.sender, Arc::new(webrtc), len);
                }
                None => {
                    debug!("WebRTC media/data on a STUN flow with no negotiation key; skipping.");
                }
            }
        }
    }

    (tags, commands)
}

fn tag_vpn_udp(conversation: &UdpConversation,
               bus: &Arc<Bus>,
               metrics: &Arc<Mutex<Metrics>>,
               tags: &mut HashSet<L7Tag>,
               commands: &mut Vec<TaggerCommand>) {
    // Conversations already identified as another protocol by the datagram-level taggers.
    if conversation.tags.iter().any(|t| !matches!(t, WireGuard | OpenVPN)) {
        return;
    }

    let mut timer = Timer::new();
    let result = wireguard_tagger::tag_udp(conversation)
        .or_else(|| openvpn_tagger::tag_udp(conversation))
        .or_else(|| vpn_tagger_tools::continuation(conversation).map(|t| (t, Vec::new())));
    timer.stop();

    match result {
        Some((tunnel, vpn_commands)) => {
            record_timer(timer.elapsed_microseconds(),
                         "tables.udp.timer.sessions.tagging.vpn.tagged", metrics);

            tags.insert(match tunnel.vpn_type {
                VpnType::WireGuard => WireGuard,
                VpnType::OpenVPN => OpenVPN,
                VpnType::DERP => DERP
            });
            commands.extend(vpn_commands);

            let len = tunnel.estimate_struct_size();
            to_pipeline!(WiredChannelName::VpnPipeline, bus.vpn_pipeline.sender, Arc::new(tunnel), len);
        },
        None => {
            record_timer(timer.elapsed_microseconds(),
                         "tables.udp.timer.sessions.tagging.vpn.untagged", metrics);
        }
    }
}
