use std::sync::Arc;

use anyhow::{bail, Error};
use byteorder::{BigEndian, ByteOrder};

use crate::helpers::network::to_mac_address_string;
use crate::wireless::dot11::frames::{ConnectionFrameType, Dot11ConnectionFrame, Dot11Frame};

const HEADER_LENGTH: usize = 24;
const QOS_CONTROL_LENGTH: usize = 2;
const HT_CONTROL_LENGTH: usize = 4;
const AMSDU_SUBFRAME_HEADER_LENGTH: usize = 14;

const FRAME_CONTROL_TYPE_MASK: u8 = 0b0000_1100;
const FRAME_CONTROL_TYPE_DATA: u8 = 0b0000_1000;
const FRAME_CONTROL_SUBTYPE_NO_BODY: u8 = 0b0100_0000;
const FRAME_CONTROL_SUBTYPE_QOS: u8 = 0b1000_0000;
const QOS_CONTROL_AMSDU_PRESENT: u8 = 0b1000_0000;

const FLAG_TO_DS: u8 = 0b0000_0001;
const FLAG_FROM_DS: u8 = 0b0000_0010;
const FLAG_RETRY: u8 = 0b0000_1000;
const FLAG_PROTECTED: u8 = 0b0100_0000;
const FLAG_ORDER: u8 = 0b1000_0000;

// RFC 1042 and bridge tunnel encapsulation. Receivers take either.
const LLC_SNAP_EAPOL: [[u8; 8]; 2] = [
    [0xAA, 0xAA, 0x03, 0x00, 0x00, 0x00, 0x88, 0x8E],
    [0xAA, 0xAA, 0x03, 0x00, 0x00, 0xF8, 0x88, 0x8E]
];

const EAPOL_TYPE_EAP_PACKET: u8 = 0;
const EAPOL_TYPE_START: u8 = 1;
const EAPOL_TYPE_LOGOFF: u8 = 2;
const EAPOL_TYPE_KEY: u8 = 3;

const KEY_DESCRIPTOR_RSN: u8 = 2;
const KEY_DESCRIPTOR_WPA: u8 = 254;

const KEY_INFO_PAIRWISE: u16 = 0x0008;
const KEY_INFO_ACK: u16 = 0x0080;
const KEY_INFO_MIC: u16 = 0x0100;
const KEY_INFO_SECURE: u16 = 0x0200;
const KEY_INFO_REQUEST: u16 = 0x0800;

// After the 802.1X header: descriptor, key information, key length, replay counter, nonce, IV, RSC, ID and a MIC of 16.
const KEY_DATA_LENGTH_OFFSET: usize = 4 + 93;

pub fn parse(frame: &Arc<Dot11Frame>) -> Result<Option<Dot11ConnectionFrame>, Error> {
    let payload = &frame.payload;

    if !is_data_with_body(payload) || payload.len() < HEADER_LENGTH + LLC_SNAP_EAPOL[0].len()
        || payload[1] & FLAG_PROTECTED != 0 {
        return Ok(None);
    }

    let is_qos = payload[0] & FRAME_CONTROL_SUBTYPE_QOS != 0;

    let to_ds = payload[1] & FLAG_TO_DS != 0;
    let from_ds = payload[1] & FLAG_FROM_DS != 0;

    if to_ds && from_ds {
        return Ok(None);
    }

    /*
     * An HT Control field follows the QoS Control field when the order bit is set, but not every receiver expects
     * it there, and an aggregate puts a subframe header in front. EAPOL at any of these places counts, or a frame
     * could be built that the tap reads differently than a client.
     */
    let body_offset = HEADER_LENGTH + if is_qos { QOS_CONTROL_LENGTH } else { 0 };
    let mut offsets = vec![body_offset];
    if is_qos && payload[1] & FLAG_ORDER != 0 {
        offsets.push(body_offset + HT_CONTROL_LENGTH);
    }
    if is_qos && payload[HEADER_LENGTH] & QOS_CONTROL_AMSDU_PRESENT != 0 {
        for offset in offsets.clone() {
            offsets.push(offset + AMSDU_SUBFRAME_HEADER_LENGTH);
        }
    }

    let Some(eapol) = offsets.into_iter().find_map(|offset| eapol_at(payload, offset)) else {
        return Ok(None);
    };

    let Some(frame_type) = classify(eapol)? else {
        return Ok(None);
    };

    let addr1 = to_mac_address_string(&payload[4..10]);
    let addr2 = to_mac_address_string(&payload[10..16]);

    let bssid = match (to_ds, from_ds) {
        (true, _) => addr1.clone(),
        (false, true) => addr2.clone(),
        (false, false) => to_mac_address_string(&payload[16..22])
    };

    Ok(Some(Dot11ConnectionFrame {
        header: frame.header.clone(),
        frame_type,
        receiver: addr1,
        transmitter: addr2,
        bssid,
        algorithm: None,
        transaction: None,
        status_code: None,
        retry: payload[1] & FLAG_RETRY != 0
    }))
}

/*
 * Receivers decide by the bits and not by the subtypes in use: every data subtype without the "no body" bit carries
 * a body, also the contention free ones nobody sends anymore.
 */
pub fn is_data_with_body(payload: &[u8]) -> bool {
    payload.len() >= HEADER_LENGTH
        && payload[0] & FRAME_CONTROL_TYPE_MASK == FRAME_CONTROL_TYPE_DATA
        && payload[0] & FRAME_CONTROL_SUBTYPE_NO_BODY == 0
}

fn eapol_at(payload: &[u8], offset: usize) -> Option<&[u8]> {
    let body = payload.get(offset..)?;

    LLC_SNAP_EAPOL.iter().find_map(|prefix| body.strip_prefix(prefix))
}

// Version, type, length. The type is enough for a start or a logoff, which is all a first fragment may hold.
fn classify(eapol: &[u8]) -> Result<Option<ConnectionFrameType>, Error> {
    if eapol.len() < 2 {
        bail!("EAPOL frame too short to hold its type. Discarding.");
    }

    match eapol[1] {
        EAPOL_TYPE_START => Ok(Some(ConnectionFrameType::EapolStart)),
        EAPOL_TYPE_LOGOFF => Ok(Some(ConnectionFrameType::EapolLogoff)),
        EAPOL_TYPE_EAP_PACKET => match eapol.get(4) {
            Some(1) => Ok(Some(ConnectionFrameType::EapRequest)),
            Some(2) => Ok(Some(ConnectionFrameType::EapResponse)),
            Some(3) => Ok(Some(ConnectionFrameType::EapSuccess)),
            Some(4) => Ok(Some(ConnectionFrameType::EapFailure)),
            Some(_) => Ok(None),
            None => bail!("EAP packet too short to hold its code. Discarding.")
        },
        EAPOL_TYPE_KEY => {
            if eapol.len() < 7 {
                bail!("EAPOL key frame too short to hold its key information. Discarding.");
            }

            if eapol[4] != KEY_DESCRIPTOR_RSN && eapol[4] != KEY_DESCRIPTOR_WPA {
                return Ok(None);
            }

            let key_information = BigEndian::read_u16(&eapol[5..7]);

            if key_information & KEY_INFO_PAIRWISE == 0 || key_information & KEY_INFO_REQUEST != 0 {
                return Ok(None);
            }

            let has_ack = key_information & KEY_INFO_ACK != 0;
            let has_mic = key_information & KEY_INFO_MIC != 0;

            Ok(match (has_ack, has_mic) {
                (true, false) => Some(ConnectionFrameType::Handshake1),
                (true, true) => Some(ConnectionFrameType::Handshake3),
                (false, true) => {
                    // WPA does not set the secure bit in the fourth message. It carries no key data, the second does.
                    let has_no_key_data = eapol.get(KEY_DATA_LENGTH_OFFSET..KEY_DATA_LENGTH_OFFSET + 2)
                        .is_some_and(|length| BigEndian::read_u16(length) == 0);

                    if key_information & KEY_INFO_SECURE != 0 || has_no_key_data {
                        Some(ConnectionFrameType::Handshake4)
                    } else {
                        Some(ConnectionFrameType::Handshake2)
                    }
                },
                (false, false) => None
            })
        },
        _ => Ok(None)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::protocols::parsers::dot11::management::management_frame_tools::test_frames::{frame, header, AP, CLIENT};
    use crate::wireless::dot11::frames::FrameSubType;

    const SERVER: [u8; 6] = [0x02, 0x99, 0x99, 0x99, 0x99, 0x99];

    fn key_frame(descriptor: u8, key_information: u16, key_data_length: u16) -> Vec<u8> {
        let mut eapol = vec![2, EAPOL_TYPE_KEY, 0, 95, descriptor];
        eapol.extend(key_information.to_be_bytes());
        eapol.extend([0u8; 90]);
        eapol.extend(key_data_length.to_be_bytes());
        eapol.extend(vec![0u8; key_data_length as usize]);

        eapol
    }

    fn from_ap(eapol: &[u8]) -> Arc<Dot11Frame> {
        let mut payload = header(0x08, FLAG_FROM_DS, CLIENT, AP, SERVER, 7);
        payload.extend(LLC_SNAP_EAPOL[0]);
        payload.extend(eapol);

        frame(FrameSubType::Data, payload)
    }

    fn qos_from_client(flags: u8, before_llc: &[u8], eapol: &[u8]) -> Arc<Dot11Frame> {
        let mut payload = header(0x88, FLAG_TO_DS | flags, AP, CLIENT, SERVER, 7);
        payload.extend([0x00, 0x00]);
        payload.extend(before_llc);
        payload.extend(LLC_SNAP_EAPOL[0]);
        payload.extend(eapol);

        frame(FrameSubType::QosData, payload)
    }

    fn type_of(frame: &Arc<Dot11Frame>) -> Option<ConnectionFrameType> {
        parse(frame).unwrap().map(|parsed| parsed.frame_type)
    }

    #[test]
    fn reads_addresses_by_direction() {
        let entering = parse(&from_ap(&[2, EAPOL_TYPE_START, 0, 0])).unwrap().unwrap();
        assert_eq!(entering.receiver, "02:AA:BB:CC:DD:EE");
        assert_eq!(entering.transmitter, "02:11:22:33:44:55");
        assert_eq!(entering.bssid, "02:11:22:33:44:55");
        assert_eq!((entering.algorithm, entering.transaction, entering.status_code), (None, None, None));

        let leaving = parse(&qos_from_client(FLAG_RETRY, &[], &[2, EAPOL_TYPE_START, 0, 0])).unwrap().unwrap();
        assert_eq!(leaving.receiver, "02:11:22:33:44:55");
        assert_eq!(leaving.transmitter, "02:AA:BB:CC:DD:EE");
        assert_eq!(leaving.bssid, "02:11:22:33:44:55");
        assert!(leaving.retry);

        let mut ad_hoc = header(0x08, 0x00, CLIENT, SERVER, AP, 7);
        ad_hoc.extend(LLC_SNAP_EAPOL[0]);
        ad_hoc.extend([2, EAPOL_TYPE_START, 0, 0]);
        let ad_hoc = parse(&frame(FrameSubType::Data, ad_hoc)).unwrap().unwrap();
        assert_eq!(ad_hoc.bssid, "02:11:22:33:44:55");
        assert_eq!(ad_hoc.transmitter, "02:99:99:99:99:99");
    }

    #[test]
    fn classifies_start_logoff_and_eap_packets() {
        assert_eq!(type_of(&from_ap(&[2, EAPOL_TYPE_START, 0, 0])), Some(ConnectionFrameType::EapolStart));
        assert_eq!(type_of(&from_ap(&[1, EAPOL_TYPE_LOGOFF, 0, 0])), Some(ConnectionFrameType::EapolLogoff));
        assert_eq!(type_of(&from_ap(&[2, 0, 0, 5, 1, 1, 0, 5, 1])), Some(ConnectionFrameType::EapRequest));
        assert_eq!(type_of(&from_ap(&[2, 0, 0, 5, 2, 1, 0, 5, 1])), Some(ConnectionFrameType::EapResponse));
        assert_eq!(type_of(&from_ap(&[2, 0, 0, 4, 3, 1, 0, 4])), Some(ConnectionFrameType::EapSuccess));
        assert_eq!(type_of(&from_ap(&[2, 0, 0, 4, 4, 1, 0, 4])), Some(ConnectionFrameType::EapFailure));
        assert_eq!(type_of(&from_ap(&[2, 0, 0, 4, 5, 1, 0, 4])), None);
        assert_eq!(type_of(&from_ap(&[2, 4, 0, 0])), None);
    }

    #[test]
    fn classifies_the_handshake_messages() {
        assert_eq!(type_of(&from_ap(&key_frame(2, 0x008a, 22))), Some(ConnectionFrameType::Handshake1));
        assert_eq!(type_of(&from_ap(&key_frame(2, 0x010a, 22))), Some(ConnectionFrameType::Handshake2));
        assert_eq!(type_of(&from_ap(&key_frame(2, 0x13ca, 56))), Some(ConnectionFrameType::Handshake3));
        assert_eq!(type_of(&from_ap(&key_frame(2, 0x030a, 0))), Some(ConnectionFrameType::Handshake4));

        assert_eq!(type_of(&from_ap(&key_frame(254, 0x0109, 24))), Some(ConnectionFrameType::Handshake2));
        assert_eq!(type_of(&from_ap(&key_frame(254, 0x0109, 0))), Some(ConnectionFrameType::Handshake4));
    }

    #[test]
    fn ignores_group_key_request_and_foreign_key_frames() {
        assert_eq!(type_of(&from_ap(&key_frame(2, 0x1382, 32))), None);
        assert_eq!(type_of(&from_ap(&key_frame(2, 0x0b0a, 0))), None);
        assert_eq!(type_of(&from_ap(&key_frame(1, 0x008a, 0))), None);
        assert_eq!(type_of(&from_ap(&key_frame(2, 0x000a, 0))), None);
    }

    #[test]
    fn key_frame_without_a_readable_key_data_length_is_judged_by_the_secure_bit() {
        let mut second = key_frame(2, 0x010a, 0);
        second.truncate(40);
        assert_eq!(type_of(&from_ap(&second)), Some(ConnectionFrameType::Handshake2));

        let mut fourth = key_frame(2, 0x030a, 0);
        fourth.truncate(40);
        assert_eq!(type_of(&from_ap(&fourth)), Some(ConnectionFrameType::Handshake4));
    }

    #[test]
    fn finds_eapol_in_qos_frames_with_and_without_ht_control() {
        let logoff = [2, EAPOL_TYPE_LOGOFF, 0, 0];

        assert_eq!(type_of(&qos_from_client(0, &[], &logoff)), Some(ConnectionFrameType::EapolLogoff));
        assert_eq!(type_of(&qos_from_client(FLAG_ORDER, &[], &logoff)), Some(ConnectionFrameType::EapolLogoff));
        assert_eq!(
            type_of(&qos_from_client(FLAG_ORDER, &[0xaa, 0xbb, 0xcc, 0xdd], &logoff)),
            Some(ConnectionFrameType::EapolLogoff)
        );
        assert_eq!(type_of(&qos_from_client(0, &[0xaa, 0xbb, 0xcc, 0xdd], &logoff)), None);
    }

    #[test]
    fn protected_and_other_data_frames_are_no_connection_frames() {
        let mut protected = header(0x08, FLAG_FROM_DS | FLAG_PROTECTED, CLIENT, AP, SERVER, 7);
        protected.extend(LLC_SNAP_EAPOL[0]);
        protected.extend([2, EAPOL_TYPE_START, 0, 0]);
        assert_eq!(type_of(&frame(FrameSubType::Data, protected)), None);

        let mut ip = header(0x08, FLAG_FROM_DS, CLIENT, AP, SERVER, 7);
        ip.extend([0xAA, 0xAA, 0x03, 0x00, 0x00, 0x00, 0x08, 0x00, 0x45, 0x00, 0x00, 0x1c]);
        assert_eq!(type_of(&frame(FrameSubType::Data, ip)), None);

        let mut wds = header(0x08, FLAG_FROM_DS | FLAG_TO_DS, CLIENT, AP, SERVER, 7);
        wds.extend(LLC_SNAP_EAPOL[0]);
        wds.extend([2, EAPOL_TYPE_START, 0, 0]);
        assert_eq!(type_of(&frame(FrameSubType::Data, wds)), None);

        let mut null = header(0x48, FLAG_TO_DS, AP, CLIENT, SERVER, 7);
        null.extend(LLC_SNAP_EAPOL[0]);
        null.extend([2, EAPOL_TYPE_START, 0, 0]);
        assert_eq!(type_of(&frame(FrameSubType::Null, null)), None);

        assert_eq!(type_of(&frame(FrameSubType::Data, header(0x08, FLAG_FROM_DS, CLIENT, AP, SERVER, 7))), None);
        assert_eq!(type_of(&frame(FrameSubType::Data, vec![0x08, 0x02])), None);
    }

    #[test]
    fn truncated_eapol_is_an_error() {
        assert!(parse(&from_ap(&[])).is_err());
        assert!(parse(&from_ap(&[2])).is_err());
        assert!(parse(&from_ap(&[2, EAPOL_TYPE_EAP_PACKET, 0, 4])).is_err());
        assert!(parse(&from_ap(&[2, EAPOL_TYPE_KEY, 0, 95, 2, 0x00])).is_err());
    }

    #[test]
    fn a_first_fragment_that_only_holds_the_type_is_still_a_start_or_logoff() {
        assert_eq!(type_of(&from_ap(&[2, EAPOL_TYPE_START])), Some(ConnectionFrameType::EapolStart));
        assert_eq!(type_of(&from_ap(&[2, EAPOL_TYPE_LOGOFF, 0])), Some(ConnectionFrameType::EapolLogoff));
    }

    #[test]
    fn data_subtypes_are_told_by_their_bits() {
        for frame_control in [0x18u8, 0x28, 0x38] {
            let mut payload = header(frame_control, FLAG_FROM_DS, CLIENT, AP, SERVER, 7);
            payload.extend(LLC_SNAP_EAPOL[0]);
            payload.extend([2, EAPOL_TYPE_LOGOFF, 0, 0]);

            assert!(is_data_with_body(&payload));
            assert_eq!(type_of(&frame(FrameSubType::Reserved, payload)), Some(ConnectionFrameType::EapolLogoff));
        }

        for (frame_control, subtype) in [(0x98u8, FrameSubType::QosDataCfAck), (0xA8, FrameSubType::QosDataCfPoll),
                (0xB8, FrameSubType::QosDataCfAckCfPoll)] {
            let mut payload = header(frame_control, FLAG_TO_DS, AP, CLIENT, SERVER, 7);
            payload.extend([0x00, 0x00]);
            payload.extend(LLC_SNAP_EAPOL[0]);
            payload.extend([2, EAPOL_TYPE_LOGOFF, 0, 0]);

            assert_eq!(type_of(&frame(subtype, payload)), Some(ConnectionFrameType::EapolLogoff));
        }

        for frame_control in [0x48u8, 0x58, 0xC8, 0xE8, 0x80, 0xB4] {
            assert!(!is_data_with_body(&header(frame_control, 0x00, AP, CLIENT, SERVER, 7)));
        }

        assert!(!is_data_with_body(&[0x08, 0x02]));
    }

    #[test]
    fn bridge_tunnel_encapsulation_counts() {
        let mut payload = header(0x08, FLAG_FROM_DS, CLIENT, AP, SERVER, 7);
        payload.extend(LLC_SNAP_EAPOL[1]);
        payload.extend([2, EAPOL_TYPE_EAP_PACKET, 0, 4, 4, 1, 0, 4]);

        assert_eq!(type_of(&frame(FrameSubType::Data, payload)), Some(ConnectionFrameType::EapFailure));
    }

    #[test]
    fn eapol_in_an_aggregate_is_found() {
        let subframe_header = [0u8; 14];

        let mut payload = header(0x88, FLAG_TO_DS, AP, CLIENT, SERVER, 7);
        payload.extend([0x80, 0x00]);
        payload.extend(subframe_header);
        payload.extend(LLC_SNAP_EAPOL[0]);
        payload.extend([2, EAPOL_TYPE_LOGOFF, 0, 0]);
        assert_eq!(type_of(&frame(FrameSubType::QosData, payload)), Some(ConnectionFrameType::EapolLogoff));

        let mut with_ht_control = header(0x88, FLAG_TO_DS | FLAG_ORDER, AP, CLIENT, SERVER, 7);
        with_ht_control.extend([0x80, 0x00]);
        with_ht_control.extend([0u8; 4]);
        with_ht_control.extend(subframe_header);
        with_ht_control.extend(LLC_SNAP_EAPOL[0]);
        with_ht_control.extend([2, EAPOL_TYPE_START, 0, 0]);
        assert_eq!(type_of(&frame(FrameSubType::QosData, with_ht_control)), Some(ConnectionFrameType::EapolStart));

        let mut no_aggregate = header(0x88, FLAG_TO_DS, AP, CLIENT, SERVER, 7);
        no_aggregate.extend([0x00, 0x00]);
        no_aggregate.extend(subframe_header);
        no_aggregate.extend(LLC_SNAP_EAPOL[0]);
        no_aggregate.extend([2, EAPOL_TYPE_LOGOFF, 0, 0]);
        assert_eq!(type_of(&frame(FrameSubType::QosData, no_aggregate)), None);
    }
}
