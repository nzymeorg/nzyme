use std::net::IpAddr;
use crate::protocols::parsers::tcp::tcp_tools::determine_tcp_session_state;
use crate::state::tables::tcp_table::{TcpSession};
use crate::wired::packets::{
    RtspAuthPosture, RtspMediaDescription, RtspMediaLocator, RtspFlag, RtspSession, RtspState
};

pub fn tag(cts: &[u8], stc: &[u8], session: &TcpSession) -> Option<RtspSession> {
    if !is_rtsp(cts) && !is_rtsp(stc) {
        return None;
    }

    let (req, resp) = orient(cts, stc);

    let mut flags: Vec<RtspFlag> = Vec::new();

    let (state, saw_publish) = derive_state(req);

    if saw_publish {
        flags.push(RtspFlag::PublishAttempt);
    }

    /*
     * Transport/media locator from the SETUP exchange. Prefer the authoritative server reply,
     * but fall back to the request.
     */
    let media = find_transport(resp)
        .or_else(|| find_transport(req))
        .and_then(|t| parse_media_locator(t, session, &mut flags));

    // Request URI.
    let request_uri = first_request_uri(req);

    // Client software and server/camera identity.
    let client_agent = header_value(req, b"user-agent").map(lossy);
    let server_info = header_value(resp, b"server").map(lossy);

    // Auth posture from the challenge/response exchange.
    let auth = derive_auth(req, resp, &mut flags);

    // Session Description Protocol (SDP) parse.
    let media_desc = parse_sdp(resp);

    let (connection_status, terminated_at) = determine_tcp_session_state(session);

    Some(RtspSession {
        setup_tcp_session_key: session.session_key.clone(),
        setup_source_address: session.source_address,
        setup_source_port: session.source_port,
        setup_destination_address: session.destination_address,
        setup_destination_port: session.destination_port,
        setup_connection_status: connection_status,
        setup_established_at: session.start_time,
        setup_terminated_at: terminated_at,
        setup_most_recent_segment_time: session.most_recent_segment_time,
        state,
        media,
        request_uri,
        client_agent,
        server_info,
        auth,
        media_desc,
        flags,
    })
}

fn is_rtsp(buf: &[u8]) -> bool {
    contains_ci(buf, b"RTSP/1.") && contains_ci(buf, b"\r\ncseq:")
}

/*
 * We've seen issues wher client-to-server and server-to-client was swapped, breaking parsing.
 * This re-orients the streams into the correct direction. We may be able to get rid of this once
 * we implement https://github.com/nzymeorg/nzyme/issues/1354
 */
fn orient<'a>(cts: &'a [u8], stc: &'a [u8]) -> (&'a [u8], &'a [u8]) {
    if looks_like_requests(cts) {
        (cts, stc)
    } else if looks_like_requests(stc) {
        (stc, cts) // Caller handed them to us swapped.
    } else {
        (cts, stc) // Neither is clearly requests. Keep caller's order.
    }
}

fn looks_like_requests(buf: &[u8]) -> bool {
    iter_lines(buf).any(|l| request_method(l).is_some())
}

fn derive_state(cts: &[u8]) -> (RtspState, bool) {
    let mut state = RtspState::Probing;
    let mut saw_publish = false;

    /*
     * Rank for the "only upgrade, never downgrade" negotiation phases, so a late
     * OPTIONS/keepalive can't drag Establishing back to Probing.
     */
    fn rank(s: RtspState) -> u8 {
        match s {
            RtspState::Probing => 0,
            RtspState::Describing => 1,
            RtspState::Establishing => 2,
            RtspState::Started => 3
        }
    }

    for line in iter_lines(cts) {
        let method = match request_method(line) {
            Some(m) => m,
            None => continue,
        };

        match method {
            b"PLAY" => {
                /*
                 * PLAY implies a prior successful SETUP, so this subsumes Establishing
                 * even if SETUP fell outside the capture window.
                 */
                if rank(state) < rank(RtspState::Started) {
                    state = RtspState::Started;
                }
            }
            b"SETUP" => {
                if rank(state) < rank(RtspState::Establishing) {
                    state = RtspState::Establishing;
                }
            }
            b"DESCRIBE" => {
                if rank(state) < rank(RtspState::Describing) {
                    state = RtspState::Describing;
                }
            }
            b"ANNOUNCE" | b"RECORD" => {
                saw_publish = true;
            }
            _ => {}
        }
    }

    (state, saw_publish)
}

fn parse_media_locator(transport: &[u8], session: &TcpSession, flags: &mut Vec<RtspFlag>)
    -> Option<RtspMediaLocator> {

    let t = lossy(transport);
    let tl = t.to_ascii_lowercase();

    // Multicast: destination group/port, no per-client negotiation.
    if tl.contains("multicast") {
        let group = param(&t, "destination").and_then(|s| s.parse::<IpAddr>().ok());

        let port = param(&t, "port")
            .and_then(|p| p.split('-').next().map(str::to_string))
            .and_then(|p| p.parse::<u16>().ok());

        if let (Some(group), Some(port)) = (group, port) {
            return Some(RtspMediaLocator::Multicast { group, port });
        }

        flags.push(RtspFlag::UnusualTransport);

        return None;
    }

    // Interleaved: media muxed into the control TCP connection.
    if let Some(chs) = param(&t, "interleaved") {
        return Some(RtspMediaLocator::Interleaved);
    }

    // UDP unicast: separate media flow on negotiated ports.
    if let Some(cp) = param(&t, "client_port") {
        let (crtp, crtcp) = parse_port_pair(&cp);
        let (srtp, srtcp) = param(&t, "server_port")
            .map(|s| parse_port_pair(&s))
            .unwrap_or((0, None));

        // destination= means media is redirected off the control client. Flag it.
        let redirect_destination = param(&t, "destination")
            .and_then(|s| s.parse::<IpAddr>().ok())
            .filter(|d| *d != session.source_address);

        if redirect_destination.is_some() {
            flags.push(RtspFlag::MediaRedirect);
        }

        return Some(RtspMediaLocator::Udp {
            client_rtp_port: crtp,
            client_rtcp_port: crtcp,
            server_rtp_port: if srtp == 0 { None } else { Some(srtp) },
            server_rtcp_port: srtcp,
            redirect_destination,
        });
    }

    flags.push(RtspFlag::UnusualTransport);
    None
}

fn derive_auth(cts: &[u8], stc: &[u8], flags: &mut Vec<RtspFlag>) -> RtspAuthPosture {
    let failures = count_status(stc, b"401");

    if failures > 0 {
        flags.push(RtspFlag::AuthFailures);
    }

    let challenge = header_value(stc, b"www-authenticate")
        .map(|v| lossy(v).to_ascii_lowercase());
    let client_authz = header_value(cts, b"authorization")
        .map(|v| lossy(v).to_ascii_lowercase());

    // A 200 anywhere in the response stream after the exchange means success.
    let saw_ok = count_status(stc, b"200") > 0;

    match challenge.as_deref() {
        Some(c) if c.contains("digest") => RtspAuthPosture::Digest,
        Some(c) if c.contains("basic") => {
            flags.push(RtspFlag::BasicAuthCleartext);
            RtspAuthPosture::Basic
        }
        _ => {
            // No challenge. If the client never sent credentials, and we saw a 200 to a
            // DESCRIBE/SETUP, the stream is effectively unauthenticated.
            if client_authz.is_none() && saw_ok {
                flags.push(RtspFlag::UnauthenticatedStream);
                RtspAuthPosture::None
            } else if client_authz.as_deref().map_or(false, |a| a.contains("basic")) {
                flags.push(RtspFlag::BasicAuthCleartext);
                RtspAuthPosture::Basic
            } else if client_authz.as_deref().map_or(false, |a| a.contains("digest")) {
                RtspAuthPosture::Digest
            } else {
                RtspAuthPosture::Unknown
            }
        }
    }
}

fn parse_sdp(stc: &[u8]) -> Option<RtspMediaDescription> {
    // SDP begins after the response headers. Look for the "v=0" anchor.
    let start = find_subsequence(stc, b"\r\nv=0")
        .map(|i| i + 2)
        .or_else(|| if stc.starts_with(b"v=0") { Some(0) } else { None })?;
    let sdp = &stc[start..];

    let mut d = RtspMediaDescription::default();
    let mut current_is_video = false;

    for line in iter_lines(sdp) {
        if line.starts_with(b"m=video") {
            d.has_video = true;
            current_is_video = true;
        } else if line.starts_with(b"m=audio") {
            d.has_audio = true;
            current_is_video = false;
        } else if let Some(rest) = strip_prefix_ci(line, b"a=rtpmap:") {
            // "a=rtpmap:96 H264/90000" = H264
            if let Some(codec) = rest
                .split(|&b| b == b' ')
                .nth(1)
                .and_then(|c| c.split(|&b| b == b'/').next())
                .map(lossy)
            {
                if current_is_video && d.video_codec.is_none() {
                    d.video_codec = Some(codec);
                } else if !current_is_video && d.audio_codec.is_none() {
                    d.audio_codec = Some(codec);
                }
            }
        } else if let Some(rest) = strip_prefix_ci(line, b"a=x-dimensions:") {
            // Some cameras advertise "a=x-dimensions:1920,1080"
            let dims = lossy(rest).replace(',', "x");
            d.resolution = Some(dims.trim().to_string());
        }
    }

    if d.has_video || d.has_audio {
        Some(d)
    } else {
        None
    }
}

fn iter_lines(buf: &[u8]) -> impl Iterator<Item = &[u8]> {
    buf.split(|&b| b == b'\n')
        .map(|l| l.strip_suffix(b"\r").unwrap_or(l))
}

fn request_method(line: &[u8]) -> Option<&'static [u8]> {
    const METHODS: [&[u8]; 11] = [
        b"OPTIONS", b"DESCRIBE", b"SETUP", b"PLAY", b"PAUSE", b"TEARDOWN",
        b"ANNOUNCE", b"RECORD", b"REDIRECT", b"GET_PARAMETER", b"SET_PARAMETER",
    ];
    // Must look like a request line ending in RTSP/1.x
    if !window_contains(line, b"RTSP/1.") {
        return None;
    }
    let first = line.split(|&b| b == b' ').next()?;
    METHODS.iter().copied().find(|&m| m == first)
}

fn first_request_uri(cts: &[u8]) -> Option<String> {
    for line in iter_lines(cts) {
        if request_method(line).is_some() {
            let mut parts = line.split(|&b| b == b' ');
            let _method = parts.next();
            if let Some(uri) = parts.next() {
                if uri != b"*" {
                    return Some(lossy(uri));
                }
            }
        }
    }
    None
}

fn header_value<'a>(buf: &'a [u8], name_lower: &[u8]) -> Option<&'a [u8]> {
    for line in iter_lines(buf) {
        if let Some(colon) = line.iter().position(|&b| b == b':') {
            let (n, v) = line.split_at(colon);
            if n.eq_ignore_ascii_case(name_lower) {
                return Some(trim(&v[1..]));
            }
        }
    }
    None
}

fn find_transport(buf: &[u8]) -> Option<&[u8]> {
    header_value(buf, b"transport")
}

fn count_status(buf: &[u8], code: &[u8]) -> u32 {
    let mut n = 0;
    for line in iter_lines(buf) {
        if line.starts_with(b"RTSP/1.") && window_contains(line, code) {
            // guard: the code should appear right after "RTSP/1.x "
            if let Some(sp) = line.iter().position(|&b| b == b' ') {
                if line.get(sp + 1..sp + 1 + code.len()) == Some(code) {
                    n += 1;
                }
            }
        }
    }
    n
}

fn param(s: &str, key: &str) -> Option<String> {
    for part in s.split(';') {
        let part = part.trim();
        if let Some(eq) = part.find('=') {
            if part[..eq].eq_ignore_ascii_case(key) {
                return Some(part[eq + 1..].trim().trim_matches('"').to_string());
            }
        }
    }
    None
}

fn parse_port_pair(s: &str) -> (u16, Option<u16>) {
    let mut it = s.split('-');
    let a = it.next().and_then(|v| v.trim().parse().ok()).unwrap_or(0);
    let b = it.next().and_then(|v| v.trim().parse().ok());
    (a, b)
}

fn contains_ci(hay: &[u8], needle_lower: &[u8]) -> bool {
    hay.windows(needle_lower.len())
        .any(|w| w.eq_ignore_ascii_case(needle_lower))
}

fn window_contains(hay: &[u8], needle: &[u8]) -> bool {
    if needle.is_empty() || hay.len() < needle.len() {
        return false;
    }
    hay.windows(needle.len()).any(|w| w == needle)
}

fn find_subsequence(hay: &[u8], needle: &[u8]) -> Option<usize> {
    if needle.is_empty() || hay.len() < needle.len() {
        return None;
    }
    hay.windows(needle.len()).position(|w| w == needle)
}

fn strip_prefix_ci<'a>(line: &'a [u8], prefix: &[u8]) -> Option<&'a [u8]> {
    if line.len() >= prefix.len() && line[..prefix.len()].eq_ignore_ascii_case(prefix) {
        Some(&line[prefix.len()..])
    } else {
        None
    }
}

fn trim(b: &[u8]) -> &[u8] {
    let start = b.iter().position(|&c| !c.is_ascii_whitespace()).unwrap_or(b.len());
    let end = b.iter().rposition(|&c| !c.is_ascii_whitespace()).map_or(start, |i| i + 1);
    &b[start..end]
}

fn lossy(b: &[u8]) -> String {
    String::from_utf8_lossy(b).into_owned()
}