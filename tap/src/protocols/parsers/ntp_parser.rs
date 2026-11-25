use std::fmt;
use std::net::Ipv4Addr;
use std::sync::Arc;
use chrono::{Duration, Utc};
use serde::Serialize;
use crate::tracemark;
use crate::wired::packets::{Datagram, NtpPacket, NtpPacketType, NtpTimestamp};

pub fn parse(udp: &Arc<Datagram>) -> Option<NtpPacket> {
    let payload = &udp.payload;

    // NTP header is at least 48 bytes long.
    let len = payload.len();
    if len != 48 {
        // Extensions should be 32-bit aligned, and not huge.
        if len < 48 || (len % 4 != 0) || len > 256 {
            tracemark!("Implausible NTP length {}. Source Address: [{}]", len, udp.source_address);
            return None;
        }
    }

    let b0 = payload[0];

    // Parse flags.
    let leap_indicator = (b0 >> 6) & 0b11;
    let version = (b0 >> 3) & 0b111;
    let mode = b0 & 0b111;

    // Check version. It's version 3 or 4 in real networks.
    if version < 3 || version > 4 {
        tracemark!("Invalid version: <{}>. Source Address: [{}]", version, udp.source_address);
        return None;
    }

    // Check mode. We only allow the common modes.
    let ntp_type = match mode {
        3 => NtpPacketType::Client,
        4 => NtpPacketType::Server,
        _ => {
            tracemark!("Invalid mode: [{}]. Source Address: [{}]", mode, udp.source_address);
            return None;
        }
    };

    // Check leap indicator.
    if leap_indicator > 3 {
        tracemark!(
            "Invalid leap indicator: [{}]. Source Address: [{}]",
            leap_indicator,
            udp.source_address
        );
        return None;
    }

    // Check stratum.
    let stratum = payload[1];
    if stratum > 16 {
        tracemark!("Invalid stratum {}. Source Address: [{}]", stratum, udp.source_address);
        return None;
    }

    // Parse poll and precision.
    let poll = payload[2] as i8;
    let precision = payload[3] as i8;

    // Poll is log2(seconds).
    if poll < -2 || poll > 20 {
        tracemark!("Implausible poll: <{}>. Source Address: [{}]", poll, udp.source_address);
        return None;
    }

    // Root delay and dispersion sanity.
    let root_delay_raw = i32::from_be_bytes(payload[4..8].try_into().unwrap());
    let root_dispersion_raw = u32::from_be_bytes(payload[8..12].try_into().unwrap());

    // Reject values that are way too large.
    if root_delay_raw.abs() > (10_000 << 16) {
        tracemark!("Root delay out of range. Source Address: [{}]", udp.source_address);
        return None;
    }
    if root_dispersion_raw > (10_000 << 16) {
        tracemark!("Root dispersion out of range. Source Address: [{}]", udp.source_address);
        return None;
    }

    // Transmit timestamp seconds (quick non-zero check).
    let tx_secs = u32::from_be_bytes(payload[40..44].try_into().unwrap());

    // The timestamp MUST be set in server replies.
    if mode == 4 && tx_secs == 0 {
        tracemark!(
            "Server packet with zero transmit timestamp. Source Address: [{}]",
            udp.source_address
        );
        return None;
    }

    let root_delay_seconds = fixed_16_16_to_f64(root_delay_raw);
    let root_dispersion_seconds = fixed_16_16_to_f64(root_dispersion_raw as i32);

    // Reference ID.
    let reference_id_raw: [u8; 4] = payload[12..16].try_into().unwrap();
    let reference_id = decode_reference_id(stratum, version, reference_id_raw, udp.source_address.is_ipv6());

    let reference_ts = read_ntp_timestamp(&payload[16..24]);
    let origin_ts = read_ntp_timestamp(&payload[24..32]);
    let receive_ts = read_ntp_timestamp(&payload[32..40]);
    let transmit_ts = read_ntp_timestamp(&payload[40..48]);

    let reference_timestamp = Some(reference_ts);
    let origin_timestamp = Some(origin_ts);
    let receive_timestamp = Some(receive_ts);
    let transmit_timestamp = Some(transmit_ts);

    // Server timestamp validity checks.
    if mode == 4 {
        // Require non-zero receive timestamp in server replies (t2).
        if receive_ts.seconds == 0 {
            tracemark!("Server packet with zero receive timestamp. Source Address: [{}]",
                udp.source_address);
            return None;
        }

        // Convert to UTC; if conversion fails, reject.
        let t2_utc = match receive_ts.to_datetime_utc() {
            Some(v) => v,
            None => {
                tracemark!("Server packet with invalid receive timestamp. Source Address: [{}]",
                    udp.source_address);
                return None;
            }
        };

        let t3_utc = match transmit_ts.to_datetime_utc() {
            Some(v) => v,
            None => {
                tracemark!("Server packet with invalid transmit timestamp. Source Address: [{}]",
                    udp.source_address);
                return None;
            }
        };

        // Ordering: server receive must be <= server transmit.
        if t3_utc < t2_utc {
            tracemark!("Server packet with transmit < receive (t3 < t2). Source Address: [{}]",
                udp.source_address);
            return None;
        }

        // Processing time should be small. allow a little slack to avoid false rejects.
        let proc = t3_utc - t2_utc;
        if proc > Duration::seconds(2) {
            tracemark!("Server processing time too large ({}ms). Source Address: [{}]",
                proc.num_milliseconds(), udp.source_address);
            return None;
        }
    }

    let transmit_time_utc = transmit_timestamp.and_then(|ts| {
        if ts.seconds == 0 { return None; }
        ts.to_datetime_utc()
    });

    Some(NtpPacket {
        source_mac: udp.source_mac.clone(),
        destination_mac: udp.destination_mac.clone(),
        source_address: udp.source_address,
        destination_address: udp.destination_address,
        source_port: udp.source_port,
        destination_port: udp.destination_port,
        size: payload.len() as u32,
        timestamp: udp.timestamp,
        ntp_type,
        leap_indicator,
        version,
        mode,
        stratum,
        poll,
        precision,
        root_delay_seconds,
        root_dispersion_seconds,
        reference_id,
        reference_timestamp,
        origin_timestamp,
        receive_timestamp,
        transmit_timestamp,
        transmit_time_utc,
    })
}

#[derive(Clone, Debug, Serialize)]
pub enum NtpReferenceId {
    KissCode { code: String, raw: [u8; 4] },
    RefClock { id: String, raw: [u8; 4] },
    Ipv4Upstream { addr: Ipv4Addr, raw: [u8; 4] },
    RefclockAddr { clock_type: u8, unit: u8, raw: [u8; 4] },

    Opaque { raw: [u8; 4] },
}

impl fmt::Display for NtpReferenceId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            NtpReferenceId::KissCode { code, .. } => {
                write!(f, "KoD: {}", code)
            }

            NtpReferenceId::RefClock { id, .. } => {
                write!(f, "Clock: {}", id)
            }

            NtpReferenceId::Ipv4Upstream { addr, .. } => {
                write!(f, "Upstream: {}", addr)
            }

            NtpReferenceId::RefclockAddr { clock_type, unit, .. } => {
                write!(f, "127.127.{}.{}", clock_type, unit)
            }

            NtpReferenceId::Opaque { raw } => {
                write!(
                    f,
                    "{:02X}.{:02X}.{:02X}.{:02X}",
                    raw[0], raw[1], raw[2], raw[3]
                )
            }
        }
    }
}

fn is_printable_refid_byte(b: u8) -> bool {
    (0x20..=0x7E).contains(&b)
}

fn bytes_to_ascii_if_printable(raw: [u8; 4]) -> Option<String> {
    if raw.iter().all(|&b| is_printable_refid_byte(b)) {
        Some(String::from_utf8_lossy(&raw).to_string())
    } else {
        None
    }
}

fn bytes_to_ascii_nul_padded(raw: [u8; 4]) -> Option<String> {
    let end = raw.iter().position(|&b| b == 0).unwrap_or(4);
    if end == 0 {
        return None;
    }

    if end < 4 && raw[end..].iter().any(|&b| b != 0) {
        return None;
    }

    if raw[..end].iter().all(|&b| is_printable_refid_byte(b)) {
        Some(String::from_utf8_lossy(&raw[..end]).to_string())
    } else {
        None
    }
}

fn is_refclock_pseudo_addr(raw: [u8; 4]) -> bool {
    raw[0] == 127 && raw[1] == 127
}

fn decode_reference_id(stratum: u8, _version: u8, raw: [u8; 4], over_ipv6: bool) -> NtpReferenceId {
    // Stratum 0: Kiss-of-Death code (typically 4 ASCII chars).
    if stratum == 0 {
        if let Some(code) = bytes_to_ascii_if_printable(raw).or_else(|| bytes_to_ascii_nul_padded(raw)) {
            return NtpReferenceId::KissCode { code, raw };
        }
        return NtpReferenceId::Opaque { raw };
    }

    // Stratum 1: Reference clock identifier (4 ASCII chars).
    if stratum == 1 {
        if let Some(id) = bytes_to_ascii_if_printable(raw).or_else(|| bytes_to_ascii_nul_padded(raw)) {
            return NtpReferenceId::RefClock { id, raw };
        }
        return NtpReferenceId::Opaque { raw };
    }

    // Stratum 2–15: usually IPv4 address of upstream server.
    if (2..=15).contains(&stratum) {
        if is_refclock_pseudo_addr(raw) {
            return NtpReferenceId::RefclockAddr {
                clock_type: raw[2],
                unit: raw[3],
                raw,
            };
        }

        // A server synchronized over IPv6 sends four bytes of a hash of the upstream address instead. A reply does
        // not say which of the two it is, so the bytes are only read as an address when it came over IPv4.
        if over_ipv6 {
            return NtpReferenceId::Opaque { raw };
        }

        let addr = Ipv4Addr::new(raw[0], raw[1], raw[2], raw[3]);
        return NtpReferenceId::Ipv4Upstream { addr, raw };
    }

    // Stratum 16 (unsynchronized) and anything else is treated as opaque.
    NtpReferenceId::Opaque { raw }
}

fn read_ntp_timestamp(b: &[u8]) -> NtpTimestamp {
    let seconds = u32::from_be_bytes(b[0..4].try_into().unwrap());
    let fraction = u32::from_be_bytes(b[4..8].try_into().unwrap());
    NtpTimestamp { seconds, fraction }
}

fn fixed_16_16_to_f64(v: i32) -> f64 {
    (v as f64) / 65536.0
}
