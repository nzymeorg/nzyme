use std::sync::Arc;

use anyhow::{bail, Error};
use log::trace;

use crate::helpers::network::to_mac_address_string;
use crate::wireless::dot11::frames::{Dot11Frame, Dot11ProbeRequestFrame};

pub fn parse(frame: &Arc<Dot11Frame>) -> Result<Dot11ProbeRequestFrame, Error> {
    if frame.payload.len() < 16 {
        bail!("Probe request frame payload too short to hold fixed parameters. Discarding.");
    }

    let transmitter = to_mac_address_string(&frame.payload[10..16]);
    let mut ssid: Option<String> = None;

    let mut cursor: usize = 24;
    while cursor + 2 <= frame.payload.len() {
        // Bounds-checked IE walk: a truncated frame must bail out, never index OOB.
        let number = frame.payload[cursor];
        let length = frame.payload[cursor + 1] as usize;
        cursor += 2;

        if length == 0 {
            // Wildcard SSID.
            break;
        }

        if cursor + length > frame.payload.len() {
            trace!("Invalid tag length reported. Not calculating any more tagged parameters for this frame.");
            break;
        }

        let data = &frame.payload[cursor..cursor+length];
        cursor += length;

        if number == 0 {
            let ssid_s = String::from_utf8_lossy(data).to_string();
            if !ssid_s.trim().is_empty() {
                ssid = Some(ssid_s);
            }
        }
    }

    Ok(Dot11ProbeRequestFrame {
        length: frame.length,
        header: frame.header.clone(),
        transmitter,
        ssid
    })
}