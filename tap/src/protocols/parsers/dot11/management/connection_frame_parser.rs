use std::sync::Arc;

use anyhow::{bail, Error};
use byteorder::{ByteOrder, LittleEndian};

use crate::protocols::parsers::dot11::management::management_frame_tools::ManagementHeader;
use crate::wireless::dot11::frames::{ConnectionFrameType, Dot11ConnectionFrame, Dot11Frame, FrameSubType};

pub fn parse(frame: &Arc<Dot11Frame>) -> Result<Dot11ConnectionFrame, Error> {
    let header = ManagementHeader::new(&frame.payload)?;

    if header.fragment_number() != 0 {
        bail!("Later fragment of a connection frame. Discarding.");
    }

    // Stations disagree about where the fields are when the order bit is set, and a first fragment may not hold them.
    let readable = !header.is_protected() && !header.has_order_bit() && !header.has_more_fragments();

    let mut algorithm = None;
    let mut transaction = None;
    let mut status_code = None;

    let frame_type = match frame.frame_type {
        FrameSubType::Authentication => {
            // Algorithm, transaction sequence number, status code. Encrypted in the third frame of shared key.
            if readable {
                let Some(body) = header.body(6) else {
                    bail!("Authentication frame payload too short to hold fixed parameters. Discarding.");
                };

                algorithm = Some(LittleEndian::read_u16(&body[0..2]));
                transaction = Some(LittleEndian::read_u16(&body[2..4]));
                status_code = Some(LittleEndian::read_u16(&body[4..6]));
            }

            ConnectionFrameType::Authentication
        },
        FrameSubType::AssociationRequest => {
            // Capabilities, listen interval.
            if !header.has_more_fragments() && header.body(4).is_none() {
                bail!("Association request frame payload too short to hold fixed parameters. Discarding.");
            }

            ConnectionFrameType::AssociationRequest
        },
        FrameSubType::ReAssociationRequest => {
            // Capabilities, listen interval, current access point.
            if !header.has_more_fragments() && header.body(10).is_none() {
                bail!("Reassociation request frame payload too short to hold fixed parameters. Discarding.");
            }

            ConnectionFrameType::ReassociationRequest
        },
        FrameSubType::AssociationResponse | FrameSubType::ReAssociationResponse => {
            // Capabilities, status code, association ID.
            if readable {
                let Some(body) = header.body(6) else {
                    bail!("Association response frame payload too short to hold fixed parameters. Discarding.");
                };

                status_code = Some(LittleEndian::read_u16(&body[2..4]));
            }

            if frame.frame_type == FrameSubType::AssociationResponse {
                ConnectionFrameType::AssociationResponse
            } else {
                ConnectionFrameType::ReassociationResponse
            }
        },
        _ => bail!("Not a connection frame.")
    };

    Ok(Dot11ConnectionFrame {
        header: frame.header.clone(),
        frame_type,
        receiver: header.receiver(),
        transmitter: header.transmitter(),
        bssid: header.bssid(),
        algorithm,
        transaction,
        status_code,
        retry: header.is_retry()
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::protocols::parsers::dot11::management::management_frame_tools::test_frames::{frame, header, AP, CLIENT};

    fn parse_frame(subtype: FrameSubType, frame_control: u8, flags: u8, body: &[u8])
        -> Result<Dot11ConnectionFrame, Error> {
        let mut payload = header(frame_control, flags, AP, CLIENT, AP, 5);
        payload.extend(body);

        parse(&frame(subtype, payload))
    }

    #[test]
    fn parses_authentication() {
        let parsed = parse_frame(FrameSubType::Authentication, 0xb0, 0x08, &[3, 0, 1, 0, 76, 0, 9, 9]).unwrap();

        assert_eq!(parsed.frame_type, ConnectionFrameType::Authentication);
        assert_eq!(parsed.receiver, "02:11:22:33:44:55");
        assert_eq!(parsed.transmitter, "02:AA:BB:CC:DD:EE");
        assert_eq!(parsed.bssid, "02:11:22:33:44:55");
        assert_eq!(parsed.algorithm, Some(3));
        assert_eq!(parsed.transaction, Some(1));
        assert_eq!(parsed.status_code, Some(76));
        assert!(parsed.retry);
    }

    #[test]
    fn protected_authentication_has_no_fields() {
        let parsed = parse_frame(FrameSubType::Authentication, 0xb0, 0x40, &[1, 2, 3, 4, 5, 6, 7, 8]).unwrap();

        assert_eq!(parsed.frame_type, ConnectionFrameType::Authentication);
        assert_eq!((parsed.algorithm, parsed.transaction, parsed.status_code), (None, None, None));
        assert!(!parsed.retry);
    }

    #[test]
    fn authentication_with_the_order_bit_has_no_fields() {
        let parsed = parse_frame(
            FrameSubType::Authentication, 0xb0, 0x80, &[0xaa, 0xbb, 0xcc, 0xdd, 0, 0, 2, 0, 0, 0]
        ).unwrap();

        assert_eq!(parsed.frame_type, ConnectionFrameType::Authentication);
        assert_eq!((parsed.algorithm, parsed.transaction, parsed.status_code), (None, None, None));
    }

    #[test]
    fn first_fragments_are_counted_and_later_ones_are_not() {
        let first = parse_frame(FrameSubType::AssociationRequest, 0x00, 0x04, &[1]).unwrap();
        assert_eq!(first.frame_type, ConnectionFrameType::AssociationRequest);

        let mut later = header(0xb0, 0x00, AP, CLIENT, AP, 5);
        later[22] |= 0x02;
        later.extend([3, 0, 1, 0, 0, 0]);
        assert!(parse(&frame(FrameSubType::Authentication, later)).is_err());
    }

    #[test]
    fn parses_association_and_reassociation_requests() {
        let request = parse_frame(FrameSubType::AssociationRequest, 0x00, 0x00, &[0x31, 0x04, 10, 0]).unwrap();
        assert_eq!(request.frame_type, ConnectionFrameType::AssociationRequest);
        assert_eq!((request.algorithm, request.transaction, request.status_code), (None, None, None));

        let mut body = vec![0x31, 0x04, 10, 0];
        body.extend(AP);
        let reassociation = parse_frame(FrameSubType::ReAssociationRequest, 0x20, 0x00, &body).unwrap();
        assert_eq!(reassociation.frame_type, ConnectionFrameType::ReassociationRequest);
        assert_eq!(reassociation.transmitter, "02:AA:BB:CC:DD:EE");
    }

    #[test]
    fn parses_association_and_reassociation_responses() {
        let response = parse_frame(FrameSubType::AssociationResponse, 0x10, 0x00, &[0x31, 0x04, 30, 0, 1, 0xc0]).unwrap();
        assert_eq!(response.frame_type, ConnectionFrameType::AssociationResponse);
        assert_eq!((response.algorithm, response.transaction, response.status_code), (None, None, Some(30)));

        let reassociation =
            parse_frame(FrameSubType::ReAssociationResponse, 0x30, 0x00, &[0x31, 0x04, 0, 0, 1, 0xc0]).unwrap();
        assert_eq!(reassociation.frame_type, ConnectionFrameType::ReassociationResponse);
        assert_eq!(reassociation.status_code, Some(0));
    }

    #[test]
    fn truncated_frames_are_errors() {
        assert!(parse_frame(FrameSubType::Authentication, 0xb0, 0x00, &[0, 0, 1, 0, 0]).is_err());
        assert!(parse_frame(FrameSubType::AssociationRequest, 0x00, 0x00, &[0x31, 0x04, 10]).is_err());
        assert!(parse_frame(FrameSubType::ReAssociationRequest, 0x20, 0x00, &[0x31, 0x04, 10, 0, 2, 0x11]).is_err());
        assert!(parse_frame(FrameSubType::AssociationResponse, 0x10, 0x00, &[0x31, 0x04, 0, 0, 1]).is_err());
        assert!(parse_frame(FrameSubType::ReAssociationResponse, 0x30, 0x00, &[]).is_err());

        let short = header(0xb0, 0, AP, CLIENT, AP, 5)[..20].to_vec();
        assert!(parse(&frame(FrameSubType::Authentication, short)).is_err());
    }

    #[test]
    fn other_frame_types_are_errors() {
        assert!(parse_frame(FrameSubType::Beacon, 0x80, 0x00, &[0; 12]).is_err());
    }
}
