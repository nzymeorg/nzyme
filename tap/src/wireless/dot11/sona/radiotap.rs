pub fn create_radiotap_header(flags: u8, rate: u8, frequency: u16, rssi: f32, tsft_us: u64, ) -> Vec<u8> {
    let mut radiotap = Vec::new();

    radiotap.push(0); // Version.
    radiotap.push(0); // Pad.
    radiotap.extend_from_slice(&0u16.to_le_bytes()); // Length placeholder.

    let present_word0: u32 =
        (1u32 << 0)  | // TSFT
            (1u32 << 1)  | // Flags
            (1u32 << 2)  | // Rate
            (1u32 << 3)  | // Channel
            (1u32 << 5)  | // dBm Antenna Signal
            (1u32 << 11) | // Antenna
            (1u32 << 14);  // RX Flags
    radiotap.extend_from_slice(&present_word0.to_le_bytes());

    // TSFT
    radiotap.extend_from_slice(&tsft_us.to_le_bytes());

    // Flags.
    radiotap.push(flags);

    // Rate.
    radiotap.push(rate);

    // Channel frequency + channel flags.
    radiotap.extend_from_slice(&frequency.to_le_bytes());
    let channel_flags: u16 = 0;
    radiotap.extend_from_slice(&channel_flags.to_le_bytes());

    // dBm Antenna Signal.
    let sig: i8 = rssi.round().clamp(i8::MIN as f32, i8::MAX as f32) as i8;
    radiotap.push(sig as u8);

    // Antenna.
    radiotap.push(0);

    // RX Flags (2-byte aligned).
    let rx_flags: u16 = 0;
    radiotap.extend_from_slice(&rx_flags.to_le_bytes());

    // Set final length.
    let rt_len = radiotap.len() as u16;
    radiotap[2..4].copy_from_slice(&rt_len.to_le_bytes());

    radiotap
}

#[cfg(test)]
mod tests {
    use std::sync::Arc;

    use super::*;
    use crate::protocols::parsers::dot11::dot11_header_parser;
    use crate::protocols::parsers::dot11::management::{action_frame_parser, channel_switch_element_parser};
    use crate::wireless::dot11::frames::{Dot11CaptureSource, Dot11RawFrame, FrameSubType};

    const AP: [u8; 6] = [0x02, 0x11, 0x22, 0x33, 0x44, 0x55];

    fn sona_frame(frame_control: u8, body: &[u8]) -> Arc<Dot11RawFrame> {
        let mut data = create_radiotap_header(0, 2, 2437, -48.0, 1_000_000);
        data.extend([frame_control, 0x00, 0x00, 0x00]);
        data.extend([0xff; 6]);
        data.extend(AP);
        data.extend(AP);
        data.extend([0x10, 0x00]);
        data.extend(body);

        Arc::new(Dot11RawFrame {
            interface_name: "sona-test".to_string(),
            capture_source: Dot11CaptureSource::Acquisition,
            data,
            captured_at: std::time::Instant::now()
        })
    }

    #[test]
    fn beacon_with_announcement_from_sona_reaches_the_parser() {
        let mut body = vec![0u8; 12];
        body.extend([0, 4, b't', b'e', b's', b't', 1, 4, 0x82, 0x84, 0x8b, 0x96, 48, 2, 1, 0, 37, 3, 1, 14, 0]);

        let (frame, _) = dot11_header_parser::parse(&sona_frame(0x80, &body)).unwrap();
        assert_eq!(frame.frame_type, FrameSubType::Beacon);
        assert_eq!(frame.header.frequency, Some(2437));
        assert_eq!(frame.header.antenna_signal, Some(-48));

        let announcement = channel_switch_element_parser::parse(&frame.payload[36..]).unwrap();
        assert_eq!(announcement.new_channel, 14);
        assert_eq!(announcement.switch_count, 0);
        assert!(announcement.is_trailing_element);
    }

    #[test]
    fn action_frame_with_announcement_from_sona_reaches_the_parser() {
        let (frame, _) = dot11_header_parser::parse(&sona_frame(0xd0, &[0, 4, 37, 3, 1, 11, 5])).unwrap();
        assert_eq!(frame.frame_type, FrameSubType::Action);

        let parsed = action_frame_parser::parse_channel_switch(&Arc::new(frame)).unwrap().unwrap();
        assert_eq!(parsed.bssid, "02:11:22:33:44:55");
        assert_eq!(parsed.header.frequency, Some(2437));
        assert_eq!(parsed.announcement.new_channel, 11);
    }
}
