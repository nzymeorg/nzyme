use anyhow::{bail, Error};
use byteorder::{ByteOrder, LittleEndian};

use crate::helpers::network::to_mac_address_string;
use crate::wireless::dot11::frames::DisconnectionFrameDetails;

const HEADER_LENGTH: usize = 24;
const HT_CONTROL_LENGTH: usize = 4;

const FLAG_MORE_FRAGMENTS: u8 = 0b0000_0100;
const FLAG_RETRY: u8 = 0b0000_1000;
const FLAG_PROTECTED: u8 = 0b0100_0000;
const FLAG_ORDER: u8 = 0b1000_0000;

const ELEMENT_MANAGEMENT_MIC: u8 = 76;

// A CCMP or GCMP header of 8 bytes, the reason code and a MIC of at least 8 bytes.
const MINIMUM_ENCRYPTED_BODY_LENGTH: usize = 18;
const KEY_ID_EXT_IV: u8 = 0b0010_0000;

pub struct ManagementHeader<'a> {
    payload: &'a [u8]
}

impl<'a> ManagementHeader<'a> {

    pub fn new(payload: &'a [u8]) -> Result<Self, Error> {
        if payload.len() < HEADER_LENGTH {
            bail!("Management frame payload too short to hold its header. Discarding.");
        }

        Ok(Self { payload })
    }

    pub fn receiver(&self) -> String {
        to_mac_address_string(&self.payload[4..10])
    }

    pub fn transmitter(&self) -> String {
        to_mac_address_string(&self.payload[10..16])
    }

    pub fn bssid(&self) -> String {
        to_mac_address_string(&self.payload[16..22])
    }

    pub fn is_retry(&self) -> bool {
        self.payload[1] & FLAG_RETRY != 0
    }

    // Set on encrypted bodies only. Group addressed frames under PMF stay readable and carry a MIC element.
    pub fn is_protected(&self) -> bool {
        self.payload[1] & FLAG_PROTECTED != 0
    }

    /*
     * The bit alone proves nothing: anyone can set it on a readable frame, and a station that ignores it would act
     * on the reason code. A body counts as encrypted if it can hold what encryption adds and starts like a CCMP
     * or GCMP header, at either place a body can start.
     */
    pub fn has_encrypted_body(&self) -> bool {
        if !self.is_protected() {
            return false;
        }

        let looks_encrypted = |offset: usize| {
            self.payload.len() >= offset + MINIMUM_ENCRYPTED_BODY_LENGTH
                && self.payload[offset + 3] & KEY_ID_EXT_IV != 0
        };

        looks_encrypted(HEADER_LENGTH) || (self.has_order_bit() && looks_encrypted(HEADER_LENGTH + HT_CONTROL_LENGTH))
    }

    pub fn is_group_addressed(&self) -> bool {
        self.payload[4] & 0b0000_0001 != 0
    }

    pub fn has_more_fragments(&self) -> bool {
        self.payload[1] & FLAG_MORE_FRAGMENTS != 0
    }

    pub fn fragment_number(&self) -> u8 {
        self.payload[22] & 0b0000_1111
    }

    pub fn has_order_bit(&self) -> bool {
        self.payload[1] & FLAG_ORDER != 0
    }

    pub fn sequence_number(&self) -> u16 {
        LittleEndian::read_u16(&self.payload[22..24]) >> 4
    }

    // An HT Control field follows the header when the order bit is set. A frame too short to hold it has none.
    pub fn body(&self, minimum_length: usize) -> Option<&'a [u8]> {
        let after_ht_control = HEADER_LENGTH + HT_CONTROL_LENGTH;

        if self.has_order_bit() && self.payload.len() >= after_ht_control + minimum_length {
            return Some(&self.payload[after_ht_control..]);
        }

        if self.payload.len() >= HEADER_LENGTH + minimum_length {
            return Some(&self.payload[HEADER_LENGTH..]);
        }

        None
    }

}

/*
 * Nobody has a reason to fragment a frame this small, but receivers put fragments back together. The first
 * fragment is recorded as a frame without a readable reason code, later ones are not counted again.
 */
pub fn parse_disconnection_details(header: &ManagementHeader) -> Result<DisconnectionFrameDetails, Error> {
    if header.fragment_number() != 0 {
        bail!("Later fragment of a disconnection frame. Discarding.");
    }

    if header.has_more_fragments() {
        return Ok(DisconnectionFrameDetails {
            reason_code: None,
            protected: false,
            retry: header.is_retry(),
            has_order_bit: header.has_order_bit(),
            sequence_number: header.sequence_number(),
            has_integrity_element: false
        });
    }

    let Some(body) = header.body(2) else {
        bail!("Disconnection frame payload too short to hold fixed parameters. Discarding.");
    };

    if header.has_encrypted_body() {
        return Ok(DisconnectionFrameDetails {
            reason_code: None,
            protected: true,
            retry: header.is_retry(),
            has_order_bit: header.has_order_bit(),
            sequence_number: header.sequence_number(),
            has_integrity_element: false
        });
    }

    Ok(DisconnectionFrameDetails {
        reason_code: Some(LittleEndian::read_u16(&body[0..2])),
        protected: false,
        retry: header.is_retry(),
        has_order_bit: header.has_order_bit(),
        sequence_number: header.sequence_number(),
        has_integrity_element: header.is_group_addressed() && ends_with_management_mic_element(&body[2..])
    })
}

/*
 * Receivers only look for the MIC element at the very end of a group addressed frame. Its MIC is 8 or 16 bytes
 * long, after a key ID of 2 and a packet number of 6 bytes.
 */
fn ends_with_management_mic_element(elements: &[u8]) -> bool {
    [16usize, 24].iter().any(|length| {
        elements.len() >= 2 + length
            && elements[elements.len() - 2 - length] == ELEMENT_MANAGEMENT_MIC
            && elements[elements.len() - 1 - length] as usize == *length
    })
}

#[cfg(test)]
pub mod test_frames {
    use std::sync::Arc;

    use crate::wireless::dot11::frames::{Dot11Frame, FrameSubType, RadiotapHeader};

    pub const AP: [u8; 6] = [0x02, 0x11, 0x22, 0x33, 0x44, 0x55];
    pub const CLIENT: [u8; 6] = [0x02, 0xaa, 0xbb, 0xcc, 0xdd, 0xee];

    pub fn frame(frame_type: FrameSubType, payload: Vec<u8>) -> Arc<Dot11Frame> {
        Arc::new(Dot11Frame {
            header: RadiotapHeader {
                is_wep: None,
                data_rate: None,
                frequency: Some(2437),
                channel: Some(6),
                antenna_signal: Some(-50),
                antenna: None,
                captured_at: std::time::Instant::now()
            },
            frame_type,
            length: payload.len(),
            payload
        })
    }

    pub fn header(frame_control: u8, flags: u8, addr1: [u8; 6], addr2: [u8; 6], addr3: [u8; 6],
                  sequence_number: u16) -> Vec<u8> {
        let mut payload = vec![frame_control, flags, 0x00, 0x00];
        payload.extend(addr1);
        payload.extend(addr2);
        payload.extend(addr3);
        payload.extend((sequence_number << 4).to_le_bytes());

        payload
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use super::test_frames::{header, AP, CLIENT};

    const MIC_ELEMENT: [u8; 18] = [76, 16, 4, 0, 1, 0, 0, 0, 0, 0, 9, 9, 9, 9, 9, 9, 9, 9];

    const BROADCAST: [u8; 6] = [0xff; 6];

    fn details(flags: u8, body: &[u8]) -> Result<DisconnectionFrameDetails, Error> {
        details_to(CLIENT, flags, body)
    }

    fn details_to(receiver: [u8; 6], flags: u8, body: &[u8]) -> Result<DisconnectionFrameDetails, Error> {
        let mut payload = header(0xc0, flags, receiver, AP, AP, 291);
        payload.extend(body);

        parse_disconnection_details(&ManagementHeader::new(&payload)?)
    }

    #[test]
    fn reads_reason_code_retry_and_sequence_number() {
        let parsed = details(0x08, &[7, 0]).unwrap();

        assert_eq!(parsed.reason_code, Some(7));
        assert_eq!(parsed.sequence_number, 291);
        assert!(parsed.retry);
        assert!(!parsed.protected);
        assert!(!parsed.has_integrity_element);
    }

    #[test]
    fn protected_frame_has_no_reason_code() {
        let encrypted = [0xfc, 0x13, 0x00, 0x20, 0x00, 0x00, 0x00, 0x00, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
        let parsed = details(0x40, &encrypted).unwrap();

        assert_eq!(parsed.reason_code, None);
        assert!(parsed.protected);
        assert!(!parsed.has_integrity_element);
        assert_eq!(parsed.sequence_number, 291);
    }

    #[test]
    fn finds_the_mic_element_of_both_lengths_at_the_end_of_a_group_addressed_frame() {
        let mut body = vec![3, 0];
        body.extend(MIC_ELEMENT);
        assert!(details_to(BROADCAST, 0x00, &body).unwrap().has_integrity_element);

        let mut long_mic = vec![3, 0, 221, 3, 1, 2, 3, 76, 24];
        long_mic.extend([0u8; 24]);
        assert!(details_to(BROADCAST, 0x00, &long_mic).unwrap().has_integrity_element);

        assert!(!details_to(BROADCAST, 0x00, &[3, 0, 76, 8, 0, 0, 0, 0, 0, 0, 0, 0]).unwrap().has_integrity_element);
        assert!(!details_to(BROADCAST, 0x00, &[3, 0, 76, 16, 0, 0]).unwrap().has_integrity_element);
        assert!(!details_to(BROADCAST, 0x00, &[3, 0, 76]).unwrap().has_integrity_element);
    }

    #[test]
    fn a_mic_element_anywhere_else_does_not_count() {
        let mut unicast = vec![3, 0];
        unicast.extend(MIC_ELEMENT);
        assert!(!details(0x00, &unicast).unwrap().has_integrity_element);

        let mut not_last = vec![3, 0];
        not_last.extend(MIC_ELEMENT);
        not_last.extend([221, 3, 1, 2, 3]);
        assert!(!details_to(BROADCAST, 0x00, &not_last).unwrap().has_integrity_element);
    }

    #[test]
    fn skips_the_ht_control_field() {
        let mut body = vec![0xaa, 0xbb, 0xcc, 0xdd, 6, 0];
        body.extend(MIC_ELEMENT);
        let parsed = details_to(BROADCAST, 0x80, &body).unwrap();

        assert_eq!(parsed.reason_code, Some(6));
        assert!(parsed.has_integrity_element);
    }

    #[test]
    fn the_protected_bit_on_a_readable_body_does_not_hide_the_reason_code() {
        let parsed = details(0x40, &[7, 0]).unwrap();
        assert_eq!(parsed.reason_code, Some(7));
        assert!(!parsed.protected);

        let parsed = details(0x40, &[7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]).unwrap();
        assert_eq!(parsed.reason_code, Some(7));
        assert!(!parsed.protected);

        let mut behind_ht_control = vec![0xaa, 0xbb, 0xcc, 0x00];
        behind_ht_control.extend([0xfc, 0x13, 0x00, 0x20, 0x00, 0x00, 0x00, 0x00, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
        let parsed = details(0xc0, &behind_ht_control).unwrap();
        assert_eq!(parsed.reason_code, None);
        assert!(parsed.protected);
    }

    #[test]
    fn a_first_fragment_is_a_frame_without_reason_code_and_later_ones_are_not_counted() {
        let first = details(0x04, &[7]).unwrap();
        assert_eq!(first.reason_code, None);
        assert!(!first.protected);
        assert_eq!(first.sequence_number, 291);

        let mut later = header(0xc0, 0x00, CLIENT, AP, AP, 291);
        later[22] |= 0x01;
        later.extend([0, 0, 0, 0]);
        assert_eq!(ManagementHeader::new(&later).unwrap().fragment_number(), 1);
        assert!(parse_disconnection_details(&ManagementHeader::new(&later).unwrap()).is_err());
    }

    #[test]
    fn order_bit_without_room_for_ht_control_reads_the_body_at_the_header_end() {
        assert_eq!(details(0x80, &[7, 0]).unwrap().reason_code, Some(7));
        assert_eq!(details(0x80, &[7, 0, 0, 0, 0]).unwrap().reason_code, Some(7));
    }

    #[test]
    fn reports_the_order_bit_at_either_body_offset_and_on_protected_frames() {
        assert!(details(0x80, &[7, 0]).unwrap().has_order_bit);
        assert!(details(0x80, &[0xaa, 0xbb, 0xcc, 0xdd, 6, 0]).unwrap().has_order_bit);
        assert!(details(0xc0, &[0xfc, 0x13, 0x00, 0x20, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]).unwrap().has_order_bit);
        assert!(!details(0x08, &[7, 0]).unwrap().has_order_bit);
        assert!(!details(0x40, &[0xfc, 0x13, 0x00, 0x20, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]).unwrap().has_order_bit);
    }

    #[test]
    fn truncated_frames_are_errors() {
        assert!(ManagementHeader::new(&header(0xc0, 0, CLIENT, AP, AP, 1)[..23]).is_err());
        assert!(details(0x00, &[]).is_err());
        assert!(details(0x00, &[7]).is_err());
        assert!(details(0x40, &[7]).is_err());
    }
}
