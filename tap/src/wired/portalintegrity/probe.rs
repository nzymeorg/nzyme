use std::io::{Read, Write};
use std::time::Duration as StdDuration;

use anyhow::{anyhow, bail, Context, Result};
use chrono::{DateTime, Utc};
use log::{info, warn};
use url::Url;

use smoltcp::wire::IpAddress;
use strum_macros::Display;
use crate::wired::portalintegrity::stack::Stack;
use crate::wired::portalintegrity::tls;
use crate::wired::portalintegrity::tls::{CapturedTls, TlsSlot};

const CONNECT_TIMEOUT: StdDuration = StdDuration::from_secs(8);
const IO_TIMEOUT: StdDuration = StdDuration::from_secs(8);
const DNS_TIMEOUT: StdDuration = StdDuration::from_secs(5);

const MAX_RESPONSE: usize = 8 * 1024 * 1024;

#[derive(Debug, Display, Clone, Copy, PartialEq, Eq)]
pub enum Completeness {
    // Full Content-Length read, chunked terminator seen, or a clean EOF.
    Complete,

    // Hit the sensor byte cap and truncated by us.
    TruncatedByCap,

    // Read timed out mid-response.
    TruncatedByTimeout,

    // Peer closed uncleanly before framing was satisfied.
    DirtyClose,
}

#[derive(Debug, Clone)]
pub struct ProbeContext {
    pub interface: String,
    pub mac: String,
    pub assigned_cidr: String,
    pub gateway: Option<String>,
    pub dhcp_server: Option<String>,
    pub dns_servers: Vec<String>
}

#[derive(Debug, Clone)]
pub struct Hop {
    pub url: String,
    pub resolved_ip: String,
    pub status: u16,
    pub followed_to: Option<String>,
    pub raw: Vec<u8>,
    pub completeness: Completeness,
    pub tls: Option<CapturedTls>
}

#[derive(Debug, Clone)]
pub struct ProbeResult {
    pub control_url: String,
    pub context: ProbeContext,
    pub hops: Vec<Hop>,
    pub error: Option<String>,
    pub probed_at: DateTime<Utc>
}

impl ProbeResult {
    pub fn log(&self) {
        info!(
            "portal_integrity: {} — {} hop(s)",
            self.control_url,
            self.hops.len()
        );
        info!(
            "  context: iface={} mac={} ip={} gw={} dhcp={} dns=[{}]",
            self.context.interface,
            self.context.mac,
            self.context.assigned_cidr,
            self.context.gateway.as_deref().unwrap_or("?"),
            self.context.dhcp_server.as_deref().unwrap_or("?"),
            self.context.dns_servers.join(", "),
        );
        for (i, hop) in self.hops.iter().enumerate() {
            info!(
                "  hop[{}] {} -> {} status={} raw={}B {}{}",
                i,
                hop.url,
                hop.resolved_ip,
                hop.status,
                hop.raw.len(),
                hop.completeness,
                hop.followed_to
                    .as_ref()
                    .map(|t| format!(" => {}", t))
                    .unwrap_or_default(),
            );
            if let Some(t) = &hop.tls {
                info!(
                    "         tls: leaf_sha256={} chain_len={} version={} cipher={:?}",
                    t.leaf_sha256,
                    t.chain_der.len(),
                    t.protocol_version.as_deref().unwrap_or("?"),
                    t.cipher_suite,
                );
            }
        }
        if let Some(err) = &self.error {
            warn!("  error: {}", err);
        }
    }
}

pub fn probe_url(stack: &mut Stack, control_url: &str, max_redirects: u32, context: &ProbeContext)
    -> ProbeResult {

    let mut result = ProbeResult {
        control_url: control_url.to_string(),
        context: context.clone(),
        hops: Vec::new(),
        error: None,
        probed_at: Utc::now()
    };

    match fetch_chain(stack, control_url, max_redirects, &mut result.hops) {
        Ok(()) => {}
        Err(e) => result.error = Some(format!("{:#}", e)),
    }

    result
}

fn fetch_chain(stack: &mut Stack, start_url: &str, max_redirects: u32, hops: &mut Vec<Hop>)
    -> Result<()> {

    let mut current = Url::parse(start_url)
        .with_context(|| format!("parsing control URL '{}'", start_url))?;

    for i in 0..=max_redirects {
        let mut hop = fetch_one(stack, &current)?;
        let status = hop.status;
        let is_redirect = (300..400).contains(&status);

        let next = if is_redirect {
            redirect_target(&hop.raw)
        } else {
            None
        };

        hop.followed_to = next.clone();
        hops.push(hop);

        if !is_redirect {
            break;
        }

        match next {
            Some(loc) => {
                if i == max_redirects {
                    info!(
                        "  max_redirects ({}) reached; not following '{}'",
                        max_redirects, loc
                    );
                    break;
                }
                current = current
                    .join(&loc)
                    .with_context(|| format!("joining redirect location '{}'", loc))?;
            }
            None => {
                info!(
                    "  redirect status {} with no followable target; chain stops here",
                    status
                );
                break;
            }
        }
    }

    Ok(())
}

fn fetch_one(stack: &mut Stack, url: &Url) -> Result<Hop> {
    let scheme = url.scheme();

    let host = url
        .host_str()
        .ok_or_else(|| anyhow!("URL '{}' has no host", url))?
        .to_string();

    let is_tls = match scheme {
        "http" => false,
        "https" => true,
        other => bail!("unsupported scheme '{}'", other),
    };

    let port = url.port().unwrap_or(if is_tls { 443 } else { 80 });

    let ip: IpAddress = match host.parse::<std::net::IpAddr>() {
        Ok(std::net::IpAddr::V4(v4)) => IpAddress::Ipv4(v4.into()),
        Ok(std::net::IpAddr::V6(_)) => bail!("IPv6 control targets not supported in this build"),
        Err(_) => stack.resolve(&host, DNS_TIMEOUT)?,
    };

    let path_and_query = {
        let mut pq = url.path().to_string();
        if let Some(q) = url.query() {
            pq.push('?');
            pq.push_str(q);
        }
        if pq.is_empty() {
            pq.push('/');
        }
        pq
    };

    let request = format!(
        "GET {} HTTP/1.1\r\nHost: {}\r\nUser-Agent: portal-integrity-probe/0.1\r\nAccept: */*\r\nConnection: close\r\n\r\n",
        path_and_query, host
    );

    let handle = stack.connect(ip, port, CONNECT_TIMEOUT)?;

    let (raw, completeness, tls_captured) = if is_tls {
        read_https(stack, handle, &host, request.as_bytes())?
    } else {
        let mut stream = stack.stream(handle, IO_TIMEOUT);
        stream.write_all(request.as_bytes())?;
        stream.flush()?;
        let (raw, completeness) = read_http_message(&mut stream)?;
        (raw, completeness, None)
    };

    let status = parse_status(&raw)?;

    Ok(Hop {
        url: url.to_string(),
        resolved_ip: ip.to_string(),
        status,
        followed_to: None,
        raw,
        completeness,
        tls: tls_captured,
    })
}

fn read_https(stack: &mut Stack, handle: smoltcp::iface::SocketHandle, host: &str, request: &[u8]
) -> Result<(Vec<u8>, Completeness, Option<CapturedTls>)> {

    use rustls::pki_types::ServerName;

    let slot: TlsSlot = std::sync::Arc::new(std::sync::Mutex::new(None));
    let config = tls::client_config(slot.clone());

    let server_name = ServerName::try_from(host.to_string())
        .map_err(|e| anyhow!("invalid server name '{}': {:?}", host, e))?;
    let mut conn = rustls::ClientConnection::new(config, server_name)
        .map_err(|e| anyhow!("rustls client init: {:?}", e))?;

    let (raw, completeness) = {
        let mut stream_adapter = stack.stream(handle, IO_TIMEOUT);
        let mut tls_stream = rustls::Stream::new(&mut conn, &mut stream_adapter);

        tls_stream
            .write_all(request)
            .context("TLS write (request)")?;
        tls_stream.flush().ok();

        read_http_message(&mut tls_stream)?
    };

    let captured = {
        let mut rec = slot.lock().ok().and_then(|g| g.clone());
        if let Some(rec) = rec.as_mut() {
            rec.protocol_version = conn.protocol_version().map(protocol_version_str);
            rec.cipher_suite = conn.negotiated_cipher_suite().map(|cs| u16::from(cs.suite()));
            rec.sni = Some(host.to_string());
        }
        rec
    };

    Ok((raw, completeness, captured))
}

fn protocol_version_str(v: rustls::ProtocolVersion) -> String {
    use rustls::ProtocolVersion::*;
    match v {
        TLSv1_3 => "TLSv1.3",
        TLSv1_2 => "TLSv1.2",
        other => return format!("{:?}", other), // future-proof fallback
    }
        .to_string()
}

fn read_http_message<R: Read>(stream: &mut R) -> Result<(Vec<u8>, Completeness)> {
    let mut buf: Vec<u8> = Vec::with_capacity(8 * 1024);
    let mut chunk = [0u8; 4096];

    let header_end = loop {
        if let Some(idx) = find_header_end(&buf) {
            break idx;
        }
        match read_tolerant(stream, &mut chunk)? {
            Some(0) => return Ok((buf, Completeness::DirtyClose)),
            None => return Ok((buf, Completeness::TruncatedByTimeout)),
            Some(n) => buf.extend_from_slice(&chunk[..n]),
        }
        if buf.len() > 256 * 1024 {
            bail!("HTTP headers exceeded 256 KiB without terminator");
        }
    };

    let head = String::from_utf8_lossy(&buf[..header_end]);
    let mut content_length: Option<usize> = None;
    let mut chunked = false;
    for line in split_header_lines(&head).skip(1) {
        if let Some((name, value)) = line.split_once(':') {
            let name = name.trim();
            let value = value.trim();
            if name.eq_ignore_ascii_case("content-length") {
                content_length = value.parse::<usize>().ok();
            } else if name.eq_ignore_ascii_case("transfer-encoding")
                && value.to_ascii_lowercase().contains("chunked")
            {
                chunked = true;
            }
        }
    }

    let completeness = if chunked {
        loop {
            if find_subslice(&buf[header_end..], b"\r\n0\r\n\r\n").is_some()
                || buf[header_end..].starts_with(b"0\r\n\r\n")
            {
                break Completeness::Complete;
            }
            if buf.len() > MAX_RESPONSE {
                break Completeness::TruncatedByCap;
            }
            match read_tolerant(stream, &mut chunk)? {
                Some(0) => break Completeness::DirtyClose,
                None => break Completeness::TruncatedByTimeout,
                Some(n) => buf.extend_from_slice(&chunk[..n]),
            }
        }
    } else if let Some(len) = content_length {
        let target = header_end + len;
        loop {
            if buf.len() >= target {
                break Completeness::Complete;
            }
            if buf.len() > MAX_RESPONSE {
                break Completeness::TruncatedByCap;
            }
            match read_tolerant(stream, &mut chunk)? {
                Some(0) => break Completeness::DirtyClose,
                None => break Completeness::TruncatedByTimeout,
                Some(n) => buf.extend_from_slice(&chunk[..n]),
            }
        }
    } else {
        // No framing info: legacy close-delimited. A clean EOF IS the frame.
        loop {
            if buf.len() > MAX_RESPONSE {
                break Completeness::TruncatedByCap;
            }
            match read_tolerant(stream, &mut chunk)? {
                Some(0) => break Completeness::Complete,
                None => break Completeness::TruncatedByTimeout,
                Some(n) => buf.extend_from_slice(&chunk[..n]),
            }
        }
    };

    Ok((buf, completeness))
}

fn read_tolerant<R: Read>(stream: &mut R, buf: &mut [u8]) -> Result<Option<usize>> {
    match stream.read(buf) {
        Ok(0) => Ok(Some(0)),
        Ok(n) => Ok(Some(n)),
        Err(e) if e.kind() == std::io::ErrorKind::TimedOut => Ok(None),
        Err(e) if e.kind() == std::io::ErrorKind::UnexpectedEof => Ok(Some(0)),
        Err(e) => Err(anyhow!("HTTP read: {}", e)),
    }
}

fn parse_status(raw: &[u8]) -> Result<u16> {
    let end = find_header_end(raw).unwrap_or(raw.len());
    let head = String::from_utf8_lossy(&raw[..end]);
    let status_line = split_header_lines(&head)
        .next()
        .ok_or_else(|| anyhow!("empty HTTP response"))?;
    status_line
        .split_whitespace()
        .nth(1)
        .and_then(|s| s.parse().ok())
        .ok_or_else(|| anyhow!("could not parse status line: '{}'", status_line))
}

fn redirect_target(raw: &[u8]) -> Option<String> {
    let end = find_header_end(raw)?;
    let head = String::from_utf8_lossy(&raw[..end]);

    let mut location = None;
    let mut refresh = None;
    for line in split_header_lines(&head).skip(1) {
        if let Some((name, value)) = line.split_once(':') {
            let name = name.trim();
            let value = value.trim();
            if name.eq_ignore_ascii_case("location") && location.is_none() {
                location = Some(value.to_string());
            } else if name.eq_ignore_ascii_case("refresh") && refresh.is_none() {
                refresh = refresh_header_url(value);
            }
        }
    }

    location
        .or(refresh)
        .or_else(|| soft_redirect_target(&raw[end..]))
}

fn refresh_header_url(value: &str) -> Option<String> {
    let lower = value.to_ascii_lowercase();
    let pos = lower.find("url=")?;
    let target = value[pos + 4..].trim().trim_matches(['"', '\'']).to_string();
    if target.is_empty() {
        None
    } else {
        Some(target)
    }
}

fn soft_redirect_target(body: &[u8]) -> Option<String> {
    let html = String::from_utf8_lossy(body);
    let lower = html.to_ascii_lowercase();

    let meta = lower
        .find("http-equiv=\"refresh\"")
        .or_else(|| lower.find("http-equiv='refresh'"))
        .or_else(|| lower.find("http-equiv=refresh"));
    if let Some(idx) = meta {
        let window = &html[idx..(idx + 300).min(html.len())];
        if let Some(u) = window.to_ascii_lowercase().find("url=") {
            let target: String = window[u + 4..]
                .chars()
                .take_while(|c| !matches!(c, '"' | '\'' | '>' | ' '))
                .collect();
            let target = target.trim().to_string();
            if !target.is_empty() {
                return Some(target);
            }
        }
    }

    for marker in ["location.href", "location.replace", "window.location"] {
        if let Some(idx) = lower.find(marker) {
            let window = &html[idx..(idx + 200).min(html.len())];
            if let Some(q) = window.find(['"', '\'']) {
                let target: String = window[q + 1..]
                    .chars()
                    .take_while(|c| !matches!(c, '"' | '\''))
                    .collect();
                if target.starts_with("http") || target.starts_with('/') {
                    return Some(target);
                }
            }
        }
    }

    None
}

fn find_header_end(buf: &[u8]) -> Option<usize> {
    let crlf = find_subslice(buf, b"\r\n\r\n").map(|i| i + 4);
    let lf = find_subslice(buf, b"\n\n").map(|i| i + 2);
    match (crlf, lf) {
        (Some(a), Some(b)) => Some(a.min(b)),
        (Some(a), None) => Some(a),
        (None, Some(b)) => Some(b),
        (None, None) => None,
    }
}

fn split_header_lines(head: &str) -> impl Iterator<Item = &str> {
    head.split('\n').map(|l| l.strip_suffix('\r').unwrap_or(l))
}

fn find_subslice(haystack: &[u8], needle: &[u8]) -> Option<usize> {
    haystack.windows(needle.len()).position(|w| w == needle)
}