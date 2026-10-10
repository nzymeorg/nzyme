use std::sync::Arc;
use anyhow::{bail, Error};
use bitvec::order::Lsb0;
use bitvec::view::BitView;
use byteorder::{ByteOrder, LittleEndian};
use log::{trace, warn};
use crate::helpers::buffer_cursor::BufferCursor;
use crate::helpers::network::dot11_frequency_to_channel;
use crate::wireless::dot11::frames::{Dot11RawFrame, Dot11Frame, RadiotapHeader, RadiotapHeaderPresentFlags, RadiotapHeaderFlags, FrameTypeInformation, FrameType, FrameSubType};

pub fn parse(data: &Arc<Dot11RawFrame>) -> Result<(Dot11Frame, u32), Error> {
    // Parse header.
    if data.data.len() < 4 {
        bail!("Received WiFi frame is too short. [{:?}]", data);
    }

    let header_length = LittleEndian::read_u16(&data.data[2..4]) as usize;

    if header_length == 0 {
        bail!("Header length is 0 or negative. Malformed radiotap header.");
    }

    // check if we have enough data (minus the first 4 bytes we already consumed) to read a header
    if header_length < 8 || data.data.len() < header_length {
        bail!("Received WiFi frame shorter than reported header length.");
    }

    // Load the radiotap buffer according to the length on the header.
    let mut radiotap_buffer = BufferCursor::new(&data.data[0..header_length]);

    // Advance the first four bytes of the buffer: version, pad and length. Already parsed.
    let _ = match radiotap_buffer.take(4) {
        Some(a) => a,
        None => bail!("Radiotap buffer length is less than 4 bytes."),
    };

    // Present flags bitmask
    let present_flags = match parse_present_flags(&mut radiotap_buffer) {
        Ok(flags) => flags,
        Err(e) => {
            bail!("Could not parse present flags bitmask: {}", e);
        }
    };

    // Variable fields follow. Not always set.

    // TSFT.
    if present_flags.tsft {
        // Radiotap spec: TSFT must be 8-byte aligned.
        radiotap_buffer.align(8);
        // not parsing
        let _ = match radiotap_buffer.take(8) {
            Some(a) => a,
            None => bail!("Radiotap buffer is malformed, not enough data to parse."),
        };
    }

    // Flags.
    let flags = if present_flags.flags {
        let frame_flags = match parse_flags(&mut radiotap_buffer) {
            Ok(flags) => flags,
            Err(e) => {
                bail!("Could not parse flags bitmask: {}", e);
            }
        };

        // Abort if checksum check failed. (Driver should not have passed this along.)
        if frame_flags.bad_fcs {
            trace!("Filtering out bad FCS frame from interface [{}].", data.interface_name);
            bail!("Could not parse bad FCS frame from interface [{}]", data.interface_name);
        }

        Some(frame_flags)
    } else {
        None
    };

    // Data Rate.
    let data_rate: Option<u32> = if present_flags.rate {
        let data_rate = match radiotap_buffer.take(1) {
            Some(a) => (a[0] as u32)*500,
            None => bail!("Radiotap buffer is malformed, not enough data."),
        };
        
        Some(data_rate)
    } else {
        None
    };

    // Channel.
    let frequency = if present_flags.channel {
        radiotap_buffer.align(2);
        
        let frequency_data = match radiotap_buffer.take(2) {
            Some(a) => a,
            None => bail!("Radiotap buffer is malformed, not enough data."),
        };
        
        
        let frequency = LittleEndian::read_u16(frequency_data);

        if frequency == 0 {
            trace!("Filtering out frame with bad reported frequency on [{}].",
                    data.interface_name);
            bail!("Could not parse bad frame with bad reported frequency");
        }

        // Skip the channel flags. Not parsing.
        let _ = match radiotap_buffer.take(2) {
            Some(a) => a,
            None => bail!("Radiotap buffer is malformed, not enough data."),
        };

        Some(frequency)
    } else {
        None
    };

    // FHSS.
    if present_flags.fhss {
        // not parsing
        let _ = match radiotap_buffer.take(2) {
            Some(a) => a,
            None => bail!("Radiotap buffer is malformed, not enough data."),
        };
    }

    // Antenna signal (dBm)
    let antenna_signal = if present_flags.antenna_signal_dbm {
        let dbm_data = match radiotap_buffer.take(1) {
            Some(a) => a,
            None => bail!("Radiotap buffer is malformed, not enough data."),
        };

        let dbm = dbm_data[0] as i8;
        Some(dbm)
    } else {
        None
    };

    // Antenna noise (dBm)
    if present_flags.antenna_noise_dbm {
        // not parsing
        let _ = match radiotap_buffer.take(1) {
            Some(a) => a,
            None => bail!("Radiotap buffer is malformed, not enough data."),
        };
    }

    // Lock quality.
    if present_flags.lock_quality {
        // not parsing
        radiotap_buffer.align(2);
        let _ = match radiotap_buffer.take(2) {
            Some(a) => a,
            None => bail!("Radiotap buffer is malformed, not enough data."),
        };
    }

    // TX attenuation.
    if present_flags.tx_attenuation {
        // not parsing
        radiotap_buffer.align(2);
        let _ = match radiotap_buffer.take(1) {
            Some(a) => a,
            None => bail!("Radiotap buffer is malformed, not enough data."),
        };
    }

    // TX attenuation (dB)
    if present_flags.tx_attenuation_db {
        // not parsing
        radiotap_buffer.align(2);
        let _ = match radiotap_buffer.take(2) {
            Some(a) => a,
            None => bail!("Radiotap buffer is malformed, not enough data."),
        };
    }

    // TX power (dBm)
    if present_flags.tx_power_dbm {
        // not parsing
        radiotap_buffer.align(1);
        let _ = match radiotap_buffer.take(1) {
            Some(a) => a,
            None => bail!("Radiotap buffer is malformed, not enough data."),
        };
    }

    // Antenna.
    let antenna = if present_flags.antenna {
        let antenna_data = match radiotap_buffer.take(1) {
            Some(a) => a,
            None => bail!("Radiotap buffer is malformed, not enough data."),
        };
        
        let antenna = antenna_data[0];
        Some(antenna)
    } else {
        None
    };

    // There are more variable fields after this, which we are not currently parsing.
    let is_wep = flags.map(|flags| flags.wep);

    let channel = if let Some(frequency) = frequency {
        let channel = match dot11_frequency_to_channel(frequency as u32) {
            Ok(c) => c.channel,

            Err(e) => {
                warn!("Could not parse channel number from frequency: {}. Present Flags: {:?}", e, present_flags);
                bail!("Could not parse channel number from frequency: {}", e);
            }
        };

        Some(channel)
    } else {
        None
    };

    let radiotap_header = RadiotapHeader {
        is_wep,
        data_rate,
        frequency,
        channel,
        antenna_signal,
        antenna
    };

    let payload = &data.data[header_length..data.data.len()];

    if payload.len() < 2 {
        trace!("Payload too short.");
        bail!("Payload too short");
    }

    let frame_type = parse_frame_type(&payload[0]);

    Ok((Dot11Frame{
        header: radiotap_header,
        frame_type: frame_type.frame_subtype,
        payload: payload.to_vec(),
        length: data.data.len()
    }, payload.len() as u32))
}

fn parse_present_flags(radiotap_buffer: &mut BufferCursor) -> Result<RadiotapHeaderPresentFlags, Error> {

    let present_buffer = match radiotap_buffer.take(4) {
        Some(buffer) => buffer,
        None => bail!("Radiotap buffer is too short")
    };

    let bmask = present_buffer.view_bits::<Lsb0>();

    let first_flags = RadiotapHeaderPresentFlags {
        tsft: *bmask.get(0).unwrap(),
        flags: *bmask.get(1).unwrap(),
        rate: *bmask.get(2).unwrap(),
        channel: *bmask.get(3).unwrap(),
        fhss: *bmask.get(4).unwrap(),
        antenna_signal_dbm: *bmask.get(5).unwrap(),
        antenna_noise_dbm: *bmask.get(6).unwrap(),
        lock_quality: *bmask.get(7).unwrap(),
        tx_attenuation: *bmask.get(8).unwrap(),
        tx_attenuation_db: *bmask.get(9).unwrap(),
        tx_power_dbm: *bmask.get(10).unwrap(),
        antenna: *bmask.get(11).unwrap(),
        antenna_signal_db: *bmask.get(12).unwrap(),
        antenna_noise_db: *bmask.get(13).unwrap(),
        rx_flags: *bmask.get(14).unwrap(),
        tx_flags: *bmask.get(15).unwrap(),
        rts_retries: *bmask.get(16).unwrap(),
        data_retries: *bmask.get(17).unwrap(),
        channel_plus: *bmask.get(18).unwrap(),
        mcs: *bmask.get(19).unwrap(),
        ampdu: *bmask.get(20).unwrap(),
        vht: *bmask.get(21).unwrap(),
        timestamp: *bmask.get(22).unwrap(),
        he_info: *bmask.get(23).unwrap(),
        hemu_info: *bmask.get(24).unwrap(),
        hemu_user_info: *bmask.get(25).unwrap(),
        zero_length_psdu: *bmask.get(26).unwrap(),
        lsig: *bmask.get(27).unwrap(),
        tlvs: *bmask.get(28).unwrap(),
        radiotap_ns_next: *bmask.get(29).unwrap(),
        vendor_nx_next: *bmask.get(30).unwrap(),
        ext: *bmask.get(31).unwrap()
    };

    if first_flags.ext {
        loop {
            /*
             * Some Wi-Fi adapters will have multiple sets of present flags, indicated by
             * the `ext` set to true. Mainly extensions or vendor specific flags. Best way here appears to be
             * using the first set of flags, ignore any that follows, but sufficiently advance
             * the cursor to resume reading of trailing information in the frame.
             */

            let next_present = match radiotap_buffer.take(4) {
                Some(buffer) => buffer,
                None => bail!("Radiotap buffer is too short")
            };

            let next_mask = next_present.view_bits::<Lsb0>();

            if !*next_mask.get(31).unwrap() {
                break;
            }
        }
    }

    Ok(first_flags)
}

fn parse_flags(radiotap_buffer: &mut BufferCursor) -> Result<RadiotapHeaderFlags, Error> {
    let data = match radiotap_buffer.take(1) {
        Some(buffer) => buffer,
        None => bail!("Radiotap buffer is too short")
    };
    
    
    let bmask = data.view_bits::<Lsb0>();

    /*
     * We are using unwrap() here because the passed u8
     * ensures enough bits for the access ops.
     */

    Ok(RadiotapHeaderFlags {
        cfp: *bmask.get(0).unwrap(),
        preamble: *bmask.get(1).unwrap(),
        wep: *bmask.get(2).unwrap(),
        fragmentation: *bmask.get(3).unwrap(),
        fcs_at_end: *bmask.get(4).unwrap(),
        data_pad: *bmask.get(5).unwrap(),
        bad_fcs: *bmask.get(6).unwrap(),
        short_gi: *bmask.get(7).unwrap(),
    })
}

fn parse_frame_type(mask: &u8) -> FrameTypeInformation {
    match mask {
        // Management.
        0b0000_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::AssociationRequest },
        0b0001_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::AssociationResponse },
        0b0010_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::ReAssociationRequest },
        0b0011_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::ReAssociationResponse },
        0b0100_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::ProbeRequest },
        0b0101_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::ProbeResponse },
        0b0110_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::TimingAdvertisement },
        0b0111_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::Reserved },
        0b1000_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::Beacon },
        0b1001_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::Atim },
        0b1010_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::Disassocation },
        0b1011_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::Authentication },
        0b1100_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::Deauthentication },
        0b1101_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::Action },
        0b1110_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::ActionNoAck },
        0b1111_0000 => FrameTypeInformation { frame_type: FrameType::Management, frame_subtype: FrameSubType::Reserved },

        // Control.
        0b0000_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::Reserved },
        0b0001_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::Reserved },
        0b0010_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::Trigger },
        0b0011_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::Tack },
        0b0100_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::BeamformingReportPoll },
        0b0101_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::VhtHeNdpAnnouncement },
        0b0110_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::ControlFrameExtension },
        0b0111_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::ControlWrapper },
        0b1000_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::BlockAckRequest },
        0b1001_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::BlockAck },
        0b1010_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::PsPoll },
        0b1011_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::Rts },
        0b1100_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::Cts },
        0b1101_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::Ack },
        0b1110_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::CfEnd },
        0b1111_0100 => FrameTypeInformation { frame_type: FrameType::Control, frame_subtype: FrameSubType::CfEndCfAck },

        // Data.
        0b0000_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::Data },
        0b0001_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::Reserved },
        0b0010_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::Reserved },
        0b0011_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::Reserved },
        0b0100_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::Null },
        0b0101_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::Reserved },
        0b0110_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::Reserved },
        0b0111_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::Reserved },
        0b1000_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::QosData },
        0b1001_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::QosDataCfAck },
        0b1010_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::QosDataCfPoll },
        0b1011_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::QosDataCfAckCfPoll },
        0b1100_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::QosNull },
        0b1101_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::Reserved },
        0b1110_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::QosCfPoll },
        0b1111_1000 => FrameTypeInformation { frame_type: FrameType::Data, frame_subtype: FrameSubType::QosCfAckCfPoll },

        // Extension.
        0b0000_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::DmgBeacon },
        0b0001_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::S1gBeacon },
        0b0010_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b0011_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b0100_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b0101_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b0110_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b0111_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b1000_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b1001_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b1010_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b1011_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b1100_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b1101_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b1110_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },
        0b1111_1100 => FrameTypeInformation { frame_type: FrameType::Extension, frame_subtype: FrameSubType::Reserved },

        _ => FrameTypeInformation { frame_type: FrameType::Invalid, frame_subtype: FrameSubType::Invalid }
    }
}