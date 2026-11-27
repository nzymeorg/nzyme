use std::sync::Arc;

use anyhow::{bail, Error};

use crate::helpers::network::to_mac_address_string;
use crate::protocols::parsers::dot11::management::advertising_frame_parser_tools::read_first_ssid;
use crate::wireless::dot11::frames::{Dot11Frame, Dot11ProbeRequestFrame};

pub fn parse(frame: &Arc<Dot11Frame>) -> Result<Dot11ProbeRequestFrame, Error> {
    if frame.payload.len() < 16 {
        bail!("Probe request frame payload too short to hold fixed parameters. Discarding.");
    }

    let transmitter = to_mac_address_string(&frame.payload[10..16]);
    let ssid = frame.payload.get(24..).and_then(read_first_ssid);

    Ok(Dot11ProbeRequestFrame {
        length: frame.length,
        header: frame.header.clone(),
        transmitter,
        ssid
    })
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;
    use std::time::Instant;

    use super::*;
    use crate::wireless::dot11::frames::{FrameSubType, RadiotapHeader};

    fn probe_request(elements: &[u8]) -> Arc<Dot11Frame> {
        let mut payload = vec![0x40, 0x00, 0x00, 0x00];
        payload.extend([0xff; 6]);
        payload.extend([0x02, 0x11, 0x22, 0x33, 0x44, 0x55]);
        payload.extend([0xff; 6]);
        payload.extend([0x10, 0x00]);
        payload.extend(elements);

        Arc::new(Dot11Frame {
            header: RadiotapHeader {
                is_wep: None,
                data_rate: None,
                frequency: None,
                channel: None,
                antenna_signal: None,
                antenna: None,
                captured_at: Instant::now()
            },
            frame_type: FrameSubType::ProbeRequest,
            length: payload.len(),
            payload
        })
    }

    #[test]
    fn reads_ssid() {
        let frame = parse(&probe_request(&[0, 4, b't', b'e', b's', b't', 1, 2, 0x82, 0x84])).unwrap();

        assert_eq!(frame.transmitter, "02:11:22:33:44:55");
        assert_eq!(frame.ssid, Some("test".to_string()));
    }

    #[test]
    fn first_ssid_element_decides() {
        assert_eq!(parse(&probe_request(&[0, 0, 0, 4, b't', b'e', b's', b't'])).unwrap().ssid, None);
        assert_eq!(parse(&probe_request(&[0, 4, b't', b'e', b's', b't', 0, 4, b'e', b'v', b'i', b'l'])).unwrap().ssid,
                   Some("test".to_string()));
        assert_eq!(parse(&probe_request(&[0, 3, 0, 0, 0])).unwrap().ssid, None);
    }

    #[test]
    fn survives_single_trailing_byte() {
        let frame = parse(&probe_request(&[0, 4, b't', b'e', b's', b't', 1])).unwrap();

        assert_eq!(frame.ssid, Some("test".to_string()));
    }

    #[test]
    fn survives_truncated_element() {
        let frame = parse(&probe_request(&[0, 4, b't', b'e', b's', b't', 1, 8, 0x82])).unwrap();

        assert_eq!(frame.ssid, Some("test".to_string()));
    }

    #[test]
    fn survives_frame_without_elements() {
        assert_eq!(parse(&probe_request(&[])).unwrap().ssid, None);
        assert_eq!(parse(&probe_request(&[0])).unwrap().ssid, None);
    }
}
