use std::sync::{Arc, Mutex};
use std::time::Instant;
use log::{debug, error, trace, warn};
use crate::messagebus::bus::Bus;
use crate::metrics::Metrics;
use crate::peripherals::cobs::cobs_encode;
use crate::peripherals::crc::crc16_ccitt;
use crate::wireless::dot11::frames::Dot11CaptureSource::Acquisition;
use crate::wireless::dot11::frames::Dot11RawFrame;
use crate::wireless::dot11::sona::radiotap::create_radiotap_header;
use crate::wireless::dot11::sona::sona_frame_header::SonaFrameHeader;
use crate::wireless::dot11::sona::sona_metrics::{parse_metrics_payload, METRICS_PAYLOAD_BYTES};
use crate::wireless::dot11::sona::uptime_offset::UptimeOffset;

const WIRE_PROTOCOL_VERSION: u8 = 2;

pub const MSG_TYPE_DOT11: u8 = 1;
const MSG_TYPE_CMD: u8 = 2;
const MSG_TYPE_METRICS: u8 = 3;

const CMD_SET_FREQUENCY: u8 = 1;
const CMD_ENTER_BOOTLOADER: u8 = 2; // just for symmetry. we are not sending that from tap.
const CMD_SET_FILTER: u8 = 3;

const HEADER_BYTES: usize = 6;

pub const MGMT_SUBTYPE_PROBE_RESP: u16 = 5;
pub const MGMT_SUBTYPE_BEACON: u16 = 8;

#[repr(u8)]
#[derive(Debug, Clone, Copy)]
enum SonaFilterMode {
    AdmitAll = 0,
    DropAll = 1,
    MacMatch = 2,
}

#[derive(Debug, Clone)]
pub enum SonaFilter {
    AdmitAll,
    DropAll,
    MacMatch { mac: [u8; 6] },
}

pub fn parse_mac(s: &str) -> anyhow::Result<[u8; 6]> {
    let parts: Vec<&str> = s.split(':').collect();
    if parts.len() != 6 {
        anyhow::bail!("MAC must have 6 octets, got {}: {}", parts.len(), s);
    }
    let mut mac = [0u8; 6];
    for (i, p) in parts.iter().enumerate() {
        mac[i] = u8::from_str_radix(p, 16)
            .map_err(|e| anyhow::anyhow!("invalid hex octet '{}': {}", p, e))?;
    }
    Ok(mac)
}

pub fn process_decoded(interface_name: &str,
                       decoded: &[u8],
                       uptime_offset: &mut Option<UptimeOffset>,
                       metrics: Arc<Mutex<Metrics>>,
                       bus: Arc<Bus>) {
    if decoded.len() < HEADER_BYTES {
        trace!("Dropping frame too short for header: <{}> bytes.", decoded.len());
        return;
    }

    let msg_type = decoded[0];
    let version = decoded[1];
    let payload_len = u16::from_le_bytes([decoded[2], decoded[3]]) as usize;
    let crc_in_hdr = u16::from_le_bytes([decoded[4], decoded[5]]);

    if version != WIRE_PROTOCOL_VERSION {
        trace!("Unknown wire protocol version: {} (expected {})", version, WIRE_PROTOCOL_VERSION);
        return;
    }

    if decoded.len() != HEADER_BYTES + payload_len {
        trace!("Frame length mismatch: declared {}, got {}.", HEADER_BYTES + payload_len, decoded.len());
        return;
    }

    let payload = &decoded[HEADER_BYTES..HEADER_BYTES + payload_len];

    let computed_crc = crc16_ccitt(payload);
    if computed_crc != crc_in_hdr {
        trace!("CRC mismatch: declared 0x{:04x}, computed 0x{:04x}.", crc_in_hdr, computed_crc);
        return;
    }

    handle_payload(interface_name, msg_type, payload, uptime_offset, metrics, bus);
}

fn handle_payload(interface_name: &str,
                  msg_type: u8,
                  payload: &[u8],
                  uptime_offset: &mut Option<UptimeOffset>,
                  metrics: Arc<Mutex<Metrics>>,
                  bus: Arc<Bus>) {
    match msg_type {
        MSG_TYPE_DOT11 => {
            if payload.len() < SonaFrameHeader::BYTES {
                error!("Dropping 802.11 frame that is too short to fit Sona header: <{}> bytes.",
                        payload.len());
                return;
            }

            let header_bytes = &payload[..SonaFrameHeader::BYTES];
            let hdr = match SonaFrameHeader::parse(header_bytes) {
                Some(h) => h,
                None => {
                    warn!("Could not parse frame header: [{:?}]", header_bytes);
                    return;
                }
            };

            let rssi = match hdr.rssi_dbm {
                Some(r) => r,
                None => {
                    trace!("Skipping Sona frame with no RSSI.");
                    return;
                }
            };

            let offset = uptime_offset
                .get_or_insert_with(|| UptimeOffset::new(hdr.capture_uptime_ms));
            let wall_micros = offset.wall_micros(hdr.capture_uptime_ms);

            let frame_bytes = &payload[SonaFrameHeader::BYTES..];

            let tsft_us = (hdr.capture_uptime_ms as u64) * 1000;

            let radiotap = create_radiotap_header(
                hdr.rate_flags,
                hdr.rate_code,
                hdr.freq_mhz,
                rssi,
                tsft_us,
            );

            let mut full_frame = Vec::with_capacity(frame_bytes.len() + radiotap.len());
            full_frame.extend_from_slice(&radiotap);
            full_frame.extend_from_slice(frame_bytes);
            let full_frame_length = full_frame.len();

            let data = Dot11RawFrame {
                capture_source: Acquisition,
                interface_name: interface_name.to_string(),
                data: full_frame,
                captured_at: Instant::now()
            };

            trace!("Sona frame on {} captured at uptime {}ms => host walltime {}us",
                    interface_name, hdr.capture_uptime_ms, wall_micros);

            match metrics.lock() {
                Ok(mut metrics) => {
                    metrics.increment_processed_bytes_total(full_frame_length as u32);
                    metrics.update_capture(interface_name, true, 0, 0, true);
                }
                Err(e) => error!("Could not acquire metrics mutex: {}", e),
            }

            match bus.dot11_broker.sender.lock() {
                Ok(mut sender) => sender.send_packet(Arc::new(data), full_frame_length as u32),
                Err(e) => {
                    error!("Could not acquire 802.11 handler broker mutex: {}", e)
                }
            }
        }
        MSG_TYPE_METRICS => {
            if let Some(sona_metrics) = parse_metrics_payload(payload) {
                debug!("Sona [{}] metrics frame: [{}]", interface_name, sona_metrics);
                match metrics.lock() {
                    Ok(mut metrics) => {
                        metrics.update_capture(
                            interface_name, true, sona_metrics.frame_queue_drops, 0, false
                        );
                        metrics.set_gauge_float(
                            &format!("{}.temperature_mc", interface_name),
                            sona_metrics.temperature_mc as f32 / 1000.0,
                        );
                        metrics.set_gauge(
                            &format!("{}.frame_queue_drops", interface_name),
                            sona_metrics.frame_queue_drops as i128,
                        );
                        metrics.set_gauge(
                            &format!("{}.frame_queue_stale_drops", interface_name),
                            sona_metrics.frame_queue_stale_drops as i128,
                        );
                        metrics.set_gauge(
                            &format!("{}.frame_queue_used", interface_name),
                            sona_metrics.frame_queue_used as i128,
                        );
                        metrics.set_gauge(
                            &format!("{}.firmware_version_bcd", interface_name),
                            sona_metrics.version_bcd as i128,
                        );
                    }
                    Err(e) => error!("Could not acquire metrics mutex: {}", e),
                }
            } else {
                warn!("Failed to parse metrics payload: <{}> bytes (expected {}).",
                        payload.len(), METRICS_PAYLOAD_BYTES);
            }
        }
        _ => {
            debug!("Unknown message type: {}", msg_type);
        }
    }
}

fn send_command(port: &mut dyn serialport::SerialPort, payload: &[u8]) -> std::io::Result<()> {
    let crc = crc16_ccitt(payload);

    // Header: type(1), version(1), len(2 LE), crc(2 LE).
    let mut msg = Vec::with_capacity(HEADER_BYTES + payload.len());
    msg.push(MSG_TYPE_CMD);
    msg.push(WIRE_PROTOCOL_VERSION);
    msg.extend_from_slice(&(payload.len() as u16).to_le_bytes());
    msg.extend_from_slice(&crc.to_le_bytes());
    msg.extend_from_slice(payload);

    let mut enc = cobs_encode(&msg);
    enc.push(0u8);

    port.write_all(&enc)
}

pub fn send_set_frequency(port: &mut dyn serialport::SerialPort, freq_mhz: u16) -> std::io::Result<()> {
    let payload = [
        CMD_SET_FREQUENCY,
        (freq_mhz & 0xFF) as u8,
        (freq_mhz >> 8) as u8,
    ];

    send_command(port, &payload)
}

pub fn send_set_filter(port: &mut dyn serialport::SerialPort, filter: &SonaFilter) -> std::io::Result<()> {
    let (mode, mac) = match filter {
        SonaFilter::AdmitAll => (SonaFilterMode::AdmitAll, [0u8; 6]),
        SonaFilter::DropAll => (SonaFilterMode::DropAll,  [0u8; 6]),
        SonaFilter::MacMatch { mac } => (SonaFilterMode::MacMatch, *mac),
    };

    let mut payload = Vec::with_capacity(1 + 7);
    payload.push(CMD_SET_FILTER);
    payload.push(mode as u8);
    payload.extend_from_slice(&mac);

    send_command(port, &payload)
}