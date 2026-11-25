use std::sync::Arc;

use anyhow::{bail, Error};

use crate::helpers::network::to_mac_address_string;
use crate::protocols::parsers::dot11::management::channel_switch_element_parser;
use crate::wireless::dot11::frames::{ChannelSwitchAnnouncement, ChannelSwitchAnnouncementType, Dot11ChannelSwitchActionFrame, Dot11Frame};

const CATEGORY_SPECTRUM_MANAGEMENT: u8 = 0;
const CATEGORY_PUBLIC: u8 = 4;
const CATEGORY_PROTECTED_DUAL_OF_PUBLIC: u8 = 9;
const ACTION_CHANNEL_SWITCH_ANNOUNCEMENT: u8 = 4;

pub fn parse_channel_switch(frame: &Arc<Dot11Frame>) -> Result<Option<Dot11ChannelSwitchActionFrame>, Error> {
    if frame.payload.len() < 26 {
        bail!("Action frame payload too short to hold category and action. Discarding.");
    }

    // Set on encrypted bodies only. Group addressed frames under PMF stay readable and carry a MIC element.
    if frame.payload[1] & 0b0100_0000 != 0 {
        return Ok(None);
    }

    /*
     * An HT Control field follows the header of management frames that have the order bit set, but not every
     * receiver expects it there. An announcement at either place counts, or a frame could be built that the tap
     * reads differently than a client.
     */
    let has_order_bit = frame.payload[1] & 0b1000_0000 != 0;
    let announcement = match parse_announcement(&frame.payload[24..]) {
        Ok(Some(announcement)) => Some(announcement),
        Ok(None) | Err(_) if has_order_bit && frame.payload.len() >= 30 => parse_announcement(&frame.payload[28..])?,
        other => other?
    };

    Ok(announcement.map(|announcement| Dot11ChannelSwitchActionFrame {
        header: frame.header.clone(),
        destination: to_mac_address_string(&frame.payload[4..10]),
        transmitter: to_mac_address_string(&frame.payload[10..16]),
        bssid: to_mac_address_string(&frame.payload[16..22]),
        announcement
    }))
}

fn parse_announcement(body: &[u8]) -> Result<Option<ChannelSwitchAnnouncement>, Error> {
    if body.len() < 2 || body[1] != ACTION_CHANNEL_SWITCH_ANNOUNCEMENT {
        return Ok(None);
    }

    match body[0] {
        CATEGORY_SPECTRUM_MANAGEMENT => match channel_switch_element_parser::parse(&body[2..]) {
            Some(announcement) => Ok(Some(ChannelSwitchAnnouncement { is_trailing_element: false, ..announcement })),
            None => bail!("Channel switch announcement action frame holds no announcement element. Discarding.")
        },
        CATEGORY_PUBLIC | CATEGORY_PROTECTED_DUAL_OF_PUBLIC => {
            // Fixed fields, not an element: mode, new operating class, new channel, count.
            if body.len() < 6 {
                bail!("Extended channel switch announcement action frame too short. Discarding.");
            }

            Ok(Some(ChannelSwitchAnnouncement {
                announcement_type: ChannelSwitchAnnouncementType::Extended,
                switch_mode: body[2],
                new_operating_class: Some(body[3]),
                new_channel: body[4],
                switch_count: body[5],
                is_trailing_element: false,
                dsss_only: None,
                country_code: None,
                current_frequency: None
            }))
        },
        _ => Ok(None)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::wireless::dot11::frames::{FrameSubType, RadiotapHeader};

    const AP: [u8; 6] = [0x02, 0x11, 0x22, 0x33, 0x44, 0x55];

    fn action_frame(flags: u8, destination: [u8; 6], body: &[u8]) -> Arc<Dot11Frame> {
        let mut payload = vec![0xd0, flags, 0x00, 0x00];
        payload.extend(destination);
        payload.extend(AP);
        payload.extend(AP);
        payload.extend([0x10, 0x00]);
        payload.extend(body);

        Arc::new(Dot11Frame {
            header: RadiotapHeader {
                is_wep: None,
                data_rate: None,
                frequency: Some(5180),
                channel: Some(36),
                antenna_signal: Some(-50),
                antenna: None,
                captured_at: std::time::Instant::now()
            },
            frame_type: FrameSubType::Action,
            length: payload.len(),
            payload
        })
    }

    #[test]
    fn parses_spectrum_management_announcement() {
        let frame = action_frame(0x00, [0xff; 6], &[0, 4, 37, 3, 1, 44, 5, 62, 1, 0]);
        let parsed = parse_channel_switch(&frame).unwrap().unwrap();

        assert_eq!(parsed.bssid, "02:11:22:33:44:55");
        assert_eq!(parsed.destination, "FF:FF:FF:FF:FF:FF");
        assert_eq!(parsed.announcement.announcement_type, ChannelSwitchAnnouncementType::Standard);
        assert_eq!(parsed.announcement.switch_mode, 1);
        assert_eq!(parsed.announcement.new_channel, 44);
        assert_eq!(parsed.announcement.switch_count, 5);
        assert_eq!(parsed.announcement.new_operating_class, None);
    }

    #[test]
    fn parses_extended_announcement_in_public_and_protected_dual_frames() {
        for category in [4, 9] {
            let frame = action_frame(0x00, [0xff; 6], &[category, 4, 0, 128, 100, 3]);
            let parsed = parse_channel_switch(&frame).unwrap().unwrap();

            assert_eq!(parsed.announcement.announcement_type, ChannelSwitchAnnouncementType::Extended);
            assert_eq!(parsed.announcement.switch_mode, 0);
            assert_eq!(parsed.announcement.new_operating_class, Some(128));
            assert_eq!(parsed.announcement.new_channel, 100);
            assert_eq!(parsed.announcement.switch_count, 3);
        }
    }

    #[test]
    fn reads_frames_with_a_mic_element_and_skips_encrypted_ones() {
        let body = [0, 4, 37, 3, 1, 44, 5, 76, 16, 4, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];

        assert!(parse_channel_switch(&action_frame(0x00, [0xff; 6], &body)).unwrap().is_some());
        assert!(parse_channel_switch(&action_frame(0x40, [0x02, 0, 0, 0, 0, 1], &body)).unwrap().is_none());
    }

    #[test]
    fn skips_the_ht_control_field() {
        let announcement = [0xaa, 0xbb, 0xcc, 0xdd, 0, 4, 37, 3, 1, 44, 5];
        let parsed = parse_channel_switch(&action_frame(0x80, [0xff; 6], &announcement)).unwrap().unwrap();
        assert_eq!(parsed.announcement.new_channel, 44);

        let block_ack_request = [0xaa, 0xbb, 0xcc, 0xdd, 3, 0, 1, 2, 3, 4];
        assert!(parse_channel_switch(&action_frame(0x80, [0xff; 6], &block_ack_request)).unwrap().is_none());

        // The order bit without the field, which a receiver that ignores the bit reads as an announcement.
        let without_field = parse_channel_switch(&action_frame(0x80, [0xff; 6], &[0, 4, 37, 3, 1, 11, 5])).unwrap().unwrap();
        assert_eq!(without_field.announcement.new_channel, 11);

        assert!(parse_channel_switch(&action_frame(0x80, [0xff; 6], &[0, 4])).is_err());
    }

    #[test]
    fn ignores_other_action_frames() {
        assert!(parse_channel_switch(&action_frame(0x00, [0xff; 6], &[3, 0, 1, 2, 3, 4])).unwrap().is_none());
        assert!(parse_channel_switch(&action_frame(0x00, [0xff; 6], &[0, 0, 37, 3, 1, 44, 5])).unwrap().is_none());
        assert!(parse_channel_switch(&action_frame(0x00, [0xff; 6], &[12, 4, 1, 2, 3, 4])).unwrap().is_none());
    }

    #[test]
    fn rejects_truncated_frames() {
        assert!(parse_channel_switch(&action_frame(0x00, [0xff; 6], &[0])).is_err());
        assert!(parse_channel_switch(&action_frame(0x00, [0xff; 6], &[0, 4])).is_err());
        assert!(parse_channel_switch(&action_frame(0x00, [0xff; 6], &[0, 4, 37, 3, 1])).is_err());
        assert!(parse_channel_switch(&action_frame(0x00, [0xff; 6], &[4, 4, 0, 128, 100])).is_err());
    }
}
