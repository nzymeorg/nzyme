use std::sync::{LazyLock, Mutex};

use anyhow::{Result, bail};
use base64::Engine;
use chrono::Utc;
use log::{debug, info, warn};
use remoteid::{Observation, Tracker, TrackerConfig, Transmitter, Transport, Verdict};
use uuid::Uuid;
use crate::protocols::detection::taggers::remoteid::messages::{BasicIdMessage, ClassificationCategory, ClassificationClass, ClassificationType, HeightType, IdType, LocationVectorMessage, OperationalStatus, OperatorIdMessage, OperatorLocationType, UavRemoteIdMessage, SelfIdMessage, SystemMessage, UavIdSummary, UavType, RemoteIdType};
use crate::tracemark;

pub fn tag(data: &[u8], bssid: String, rssi: Option<i8>, frequency: Option<u16>)
    -> Option<UavRemoteIdMessage> {

    if frequency.is_none() {
        debug!("No frequency provided.");
        return None
    };

    if data.len() < 8 {
        return None
    }

    let message_type = (data[5] & 0xF0) >> 4;
    let protocol_version = data[5] & 0x0F;

    /*
     * We support both of the existing F3411 versions. The original version "19" has not been
     * significantly changed on the protocol level and is backwards-compatible. We do not need
     * to parse it differently.
     */
    if (message_type != 15 && message_type > 5) || (protocol_version != 1 && protocol_version != 2) {
        tracemark!("Not a supported version or not a Remote ID message at all");
        return None
    }

    let mut parent_message = UavRemoteIdMessage::default();
    parent_message.remote_id_type = RemoteIdType::WiFi;
    parent_message.timestamp = Utc::now();
    parent_message.bssid = bssid;
    parent_message.frequency = frequency.unwrap();
    parent_message.rssis = rssi.map(|rssi| vec![rssi])
        .unwrap_or_else(|| { 
            warn!("No RSSI provided to UAV Remote ID tagger.");
            vec![-100]
    });

    if message_type == 15 {
        // Message Pack. Iterate over all messages.
        let mut cursor = 8;

        let mut intact_messages = 0;
        loop {
            if data.len() < cursor+25 { // Message must be 25 bytes long.
                break
            }

            match parse_message(&data[cursor..cursor+25], &mut parent_message) { Err(e) => {
                tracemark!("Could not parse remote ID message in pack: {:?}", e);
                // We continue with other messages in this pack.
            } _ => {
                intact_messages += 1;
            }}

            cursor += 25;
        }

        // Check if we parsed anything successfully at all.
        if intact_messages == 0 {
            return None
        }

        parent_message.trust = assess_trust(
            &parent_message.bssid, data, rssi, parent_message.timestamp.timestamp_millis() as u64);

        Some(parent_message)
    } else {
        // Single message. Parse.
        if data.len() < 27 {
            tracemark!("Single message too short. Length: {}", data.len());
            return None
        }

        if let Err(e) = parse_message(&data[5..data.len()], &mut parent_message) {
            tracemark!("Could not parse individual remote ID message: {:?}", e);
            return None
        }

        parent_message.trust = assess_trust(
            &parent_message.bssid, data, rssi, parent_message.timestamp.timestamp_millis() as u64);

        Some(parent_message)
    }
}

/// Per-transmitter trust tracker. The hand-rolled parser above decodes one
/// Wi-Fi frame at a time and keeps no state, so it cannot see the cross-frame
/// inconsistencies (teleports, reused counters, impossible kinematics, known
/// spoofing-tool fingerprints) that separate a real drone from a spoofed one.
/// remoteid's `Tracker` keeps that state per transmitter and expires quiet
/// aircraft on its own.
static TRUST_TRACKER: LazyLock<Mutex<Tracker>> =
    LazyLock::new(|| Mutex::new(Tracker::new(TrackerConfig::default())));

/// Runs the decoded Wi-Fi Remote ID payload through remoteid's trust tracker
/// and returns the spoof/trust verdict for this transmitter.
fn assess_trust(bssid: &str, data: &[u8], rssi: Option<i8>, now_ms: u64) -> Option<Verdict> {
    let addr = parse_bssid(bssid)?;

    if data.len() < 6 {
        return None
    }

    // Wi-Fi Remote ID vendor element: 4-byte OUI and vendor type, then a
    // 1-byte message counter, then the message or message pack remoteid reads.
    let counter = data[4];
    let payload = &data[5..];

    let mut obs = Observation::new(Transport::WifiBeacon, Some(counter), payload, now_ms)
        .with_unix_ms(now_ms);
    if let Some(rssi) = rssi {
        obs = obs.with_rssi(rssi);
    }

    let mut tracker = TRUST_TRACKER.lock().ok()?;
    Some(tracker.observe(Transmitter::Wifi(addr), &obs).assessment.verdict)
}

fn parse_bssid(bssid: &str) -> Option<[u8; 6]> {
    let mut addr = [0u8; 6];
    let mut parts = bssid.split(':');
    for byte in addr.iter_mut() {
        *byte = u8::from_str_radix(parts.next()?, 16).ok()?;
    }
    if parts.next().is_some() {
        return None
    }
    Some(addr)
}

fn parse_message(data: &[u8], parent_message: &mut UavRemoteIdMessage) -> Result<()> {
    if data.len() < 3 {
        bail!("Message too short. Length: {}", data.len());
    }

    let message_type = (data[0] & 0xF0) >> 4;
    let message_payload = &data[1..data.len()];

    match message_type {
        0 => {
            // Operator ID message.
            match parse_basic_id_message(message_payload) {
                Ok(msg) => {
                    parent_message.uav_type = Some(msg.uav_type);
                    parent_message.ids.push(UavIdSummary {
                        id: msg.uav_id, id_type: msg.id_type
                    });
                    Ok(())
                },
                Err(e) => bail!("Failed to parse basic ID message: {}", e)
            }
        },
        1 => {
            // Location / Vector message.
            match parse_location_vector_message(message_payload) {
                Ok(msg) => {
                    parent_message.location_and_vector = Some(msg);
                    Ok(())
                },
                Err(e) => bail!("Failed to parse location / vector message: {}", e)
            }
        },
        3 => {
            // Self-ID message.
            match parse_self_id_message(message_payload) {
                Ok(msg) => {
                    parent_message.self_id = Some(msg);
                    Ok(())
                },
                Err(e) => bail!("Failed to parse Self-ID message: {}", e)
            }
        },
        4 => {
            // System message.
            match parse_system_message(message_payload) {
                Ok(msg) => {
                    parent_message.system = Some(msg);
                    Ok(())
                },
                Err(e) => bail!("Failed to parse system message: {}", e)
            }
        }
        5 => {
            // Operator ID message.
            match parse_operator_id_message(message_payload) {
                Ok(msg) => {
                    parent_message.operator_license_id = Some(msg);
                    Ok(())
                },
                Err(e) => bail!("Failed to parse operator ID message: {}", e)
            }
        },
        _ => {
            bail!("Unknown or ignored message type: {}", message_type)
        }
    }
}

fn parse_basic_id_message(data: &[u8]) -> Result<BasicIdMessage> {
    if data.len() < 24 {
        bail!("Basic ID message too short. Length: {}", data.len());
    }

    let id_type_num: u8 = (data[0] & 0xF0) >> 4;
    let uav_type_num: u8 = data[0] & 0x0F;

    if id_type_num == 0 || id_type_num > 4 {
        bail!("Invalid ID type: {}", id_type_num);
    }

    let id_type = match IdType::try_from(id_type_num) {
        Ok(id_type) => id_type,
        Err(_) => bail!("Invalid ID type: {}", id_type_num)
    };

    if uav_type_num > 15 {
        bail!("Invalid UA type: {}", uav_type_num);
    }

    let uav_type = match UavType::try_from(uav_type_num) {
        Ok(uav_type) => uav_type,
        Err(_) => bail!("Invalid UA type: {}", uav_type_num)
    };

    let id_bytes = &data[1..21];
    let uav_id = match id_type {
        IdType::AnsiCtaSerial | IdType::CaaRegistrationId => ascii_padded_to_string(id_bytes),
        IdType::UtmAssignedUuid => {
            match Uuid::from_slice(&id_bytes[0..16]) {
                Ok(uuid) => uuid.to_string(),
                Err(e) => bail!("Could not parse UA UUID: {}", e)
            }
        },
        IdType::SpecificSessionId => base64::engine::general_purpose::STANDARD.encode(id_bytes),
    };

    Ok(BasicIdMessage { id_type, uav_type, uav_id, })
}

fn parse_location_vector_message(data: &[u8]) -> Result<LocationVectorMessage> {
    if data.len() < 24 {
        bail!("Location/Vector message too short. Length: {}", data.len());
    }

    let operational_status_num = (data[0] >> 4) & 0x0F;
    let height_type_flag: bool = (data[0] >> 2) & 0x01 != 0;
    let east_west_flag: bool = (data[0] >> 1) & 0x01 != 0;
    let speed_multiplier_flag: bool = data[0] & 0x01 != 0;

    let ground_track_value: u16 = match east_west_flag {
        true => data[1] as u16 + 180,
        false => data[1] as u16,
    };

    // Some UAS send nonsensical values when not determining a ground track.
    let ground_track = if ground_track_value <= 360 {
        Some(ground_track_value)
    } else {
        None
    };

    let speed = match speed_multiplier_flag {
        true => (data[2] as f32 * 0.75) + (255.0*0.25),
        false => data[2] as f32 * 0.25
    };
    
    if !(0.0..=254.25).contains(&speed) {
        bail!("Invalid speed: {}", speed);
    }

    // Vertical speed can be positive or negative.
    let vertical_speed = (data[3] as i8) as f32 * 0.5;

    if vertical_speed > 62.0 {
        bail!("Invalid vertical speed: {}", vertical_speed);
    }
    
    let latitude = decode_coordinate(&data[4..8]);
    let longitude = decode_coordinate(&data[8..12]);

    let altitude_pressure = decode_altitude(&data[12..14]);
    let altitude_geodetic = decode_altitude(&data[14..16]);
    let height = decode_altitude(&data[16..18]);

    let vertical_accuracy: u8 = (data[18] >> 4) & 0x0F;
    let horizontal_accuracy: u8 = data[18] & 0x0F;
    let barometer_accuracy: u8 = (data[19] >> 4) & 0x0F;
    let speed_accuracy: u8 = data[19] & 0x0F;
    
    if vertical_accuracy > 15 {
        bail!("Invalid vertical accuracy: {}", horizontal_accuracy);
    }

    if horizontal_accuracy > 15 {
        bail!("Invalid horizontal accuracy: {}", horizontal_accuracy);
    }

    if barometer_accuracy > 15 {
        bail!("Invalid barometer accuracy: {}", barometer_accuracy);
    }

    if speed_accuracy > 15 {
        bail!("Invalid speed accuracy: {}", speed_accuracy);
    }
    
    let operational_status = match OperationalStatus::try_from(operational_status_num) {
        Ok(os) => os,
        Err(_) => bail!("Invalid operational status: {}", operational_status_num)
    };

    let height_type = match height_type_flag {
        true => HeightType::AboveGround,
        false => HeightType::AboveTakeoffLocation
    };
    
    Ok(LocationVectorMessage {
        operational_status,
        height_type,
        ground_track,
        speed,
        vertical_speed,
        latitude,
        longitude,
        altitude_pressure,
        altitude_geodetic,
        height,
        horizontal_accuracy,
        vertical_accuracy,
        barometer_accuracy,
        speed_accuracy,
    })
}

fn parse_self_id_message(data: &[u8]) -> Result<SelfIdMessage> {
    if data.len() < 24 {
        bail!("Self-ID message too short. Length: {}", data.len());
    }

    // Message is all remaining bytes, padded ASCII.
    let flight_description = ascii_padded_to_string(&data[1..data.len()]).trim().to_string();

    if flight_description.is_empty() {
        bail!("Empty flight description");
    }

    Ok(SelfIdMessage { flight_description })
}

fn parse_system_message(data: &[u8]) -> Result<SystemMessage> {
    if data.len() < 24 {
        bail!("System message too short. Length: {}", data.len());
    }

    let classification_type_num = (data[0] >> 2) & 0x03;

    let classification_type = match ClassificationType::try_from(classification_type_num) {
        Ok(classification_type) => classification_type,
        Err(_) => bail!("Invalid classification type: {}", classification_type_num)
    };

    let operator_location_type_num = data[0] & 0x03;
    let operator_location_type = match OperatorLocationType::try_from(operator_location_type_num) {
        Ok(operator_location_type) => operator_location_type,
        Err(_) => bail!("Invalid operator location type: {}", operator_location_type_num)
    };

    let operator_location_latitude = decode_coordinate(&data[1..5]);
    let operator_location_longitude = decode_coordinate(&data[5..9]);

    let area_count: u16 = u16::from_le_bytes(
        data[9..11].try_into()?
    );
    
    let area_radius = data[11] as u16 * 10;

    let area_ceiling = decode_altitude(&data[12..14]);
    let area_floor = decode_altitude(&data[14..16]);

    let classification_category_num: u8 = (data[16] & 0xF0) >> 4;
    let classification_class_num: u8 = data[16] & 0x0F;

    let operator_altitude = decode_altitude(&data[17..19]);

    let (classification_category, classification_class) = match classification_type {
        ClassificationType::EuropeanUnion => {
            let category = match ClassificationCategory::try_from(classification_category_num) {
                Ok(cat) => cat,
                Err(_) => bail!("Invalid classification category: {}", classification_category_num)
            };

            let class = match ClassificationClass::try_from(classification_class_num) {
                Ok(class) => class,
                Err(_) => bail!("Invalid classification class: {}", classification_class_num)
            };

            (category, class)
        },
        _ => (ClassificationCategory::Undefined, ClassificationClass::Undefined)
    };

    Ok(SystemMessage {
        classification_type,
        operator_location_type,
        operator_location_latitude,
        operator_location_longitude,
        area_count,
        area_radius,
        area_ceiling,
        area_floor,
        classification_category,
        classification_class,
        operator_altitude,
    })
}

fn parse_operator_id_message(data: &[u8]) -> Result<OperatorIdMessage> {
    if data.len() < 24 {
        bail!("Operator ID message too short. Length: {}", data.len());
    }

    // Message is 20 bytes padded ASCII.
    let operator_id = ascii_padded_to_string(&data[1..21]).trim().to_string();

    if operator_id.is_empty() {
        bail!("Empty operator ID");
    }

    Ok(OperatorIdMessage { operator_id })
}

fn ascii_padded_to_string(buf: &[u8]) -> String {
    // Find the first null byte (if any). If none found, treat the entire buffer as the string.
    let end = buf.iter().position(|&b| b == 0).unwrap_or(buf.len());

    // Slice up to the null terminator (or the entire buffer).
    let text_slice = &buf[..end];

    // Convert tp string. (ASCII is subset of UTF-8)
    match std::str::from_utf8(text_slice) {
        Ok(s) => s.to_string(),
        Err(_) => {
            debug!("Could not convert to buffer to string. Returning empty string.");
            "".to_string()
        }
    }
}

fn decode_coordinate(data: &[u8]) -> f64 {
    assert_eq!(data.len(), 4, "Coordinate must be 4 bytes / i32.");

    let raw_value: i32 = i32::from_le_bytes(
        data[0..4].try_into().unwrap()
    );

    raw_value as f64 / 10_000_000.0
}

fn decode_altitude(data: &[u8]) -> Option<f32> {
    assert_eq!(data.len(), 2, "Altitude must be 2 bytes / u16.");

    let raw_value: u16 = u16::from_le_bytes(
        data[0..2].try_into().unwrap()
    );

    let decoded = (raw_value as f32 * 0.5) - 1000.0;

    if decoded == -1000.0 {
        None
    } else {
        Some(decoded)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use remoteid::{BasicId, HorizontalAccuracy, IdType, Location, Message, MessagePackBuilder, UaType, Verdict};

    fn basic_id(serial: &str) -> [u8; 25] {
        Message::BasicId(
            BasicId::new(IdType::SerialNumber, UaType::HelicopterOrMultirotor, serial).unwrap(),
        )
        .encode()
        .unwrap()
    }

    fn location(lat: f64, lon: f64, speed: f32, dir: f32, ts: f32) -> [u8; 25] {
        Message::Location(Location {
            latitude: lat,
            longitude: lon,
            speed_horizontal: Some(speed),
            direction: Some(dir),
            altitude_geo: Some(100.0),
            timestamp: Some(ts),
            horizontal_accuracy: HorizontalAccuracy(10),
            ..Location::default()
        })
        .encode()
        .unwrap()
    }

    // Open Drone ID Wi-Fi vendor element framing the hand-rolled tagger reads:
    // OUI 0xFA-0x0B-0xBC, vendor type 0x0D, a message counter, then a pack.
    fn wifi_frame(serial: &str, lat: f64, lon: f64, speed: f32, dir: f32, ts: f32, counter: u8)
        -> Vec<u8> {
        let mut b = MessagePackBuilder::new();
        b.push_raw(&basic_id(serial)).unwrap();
        b.push_raw(&location(lat, lon, speed, dir, ts)).unwrap();
        let mut f = vec![0xFA, 0x0B, 0xBC, 0x0D, counter];
        f.extend_from_slice(b.as_bytes());
        f
    }

    #[test]
    fn steady_wifi_track_is_plausible() {
        let bssid = "aa:bb:cc:00:00:01";
        let mut verdict = None;
        for i in 0..20u64 {
            // 10 m/s due north, one frame per second, clock consistent.
            let lat = 47.0 + (10.0 * i as f64) / 111_195.0;
            let frame = wifi_frame("1596F35074529ABCDEFG", lat, 8.0, 10.0, 0.0, 600.0 + i as f32, i as u8);
            verdict = assess_trust(bssid, &frame, Some(-50), 600_000 + 1_000 * i);
        }
        assert_eq!(verdict, Some(Verdict::Plausible));
    }

    #[test]
    fn teleporting_wifi_transmitter_is_spoofed() {
        let bssid = "aa:bb:cc:00:00:02";
        let mut verdict = None;
        for i in 0..20u64 {
            // Jumps 2 km east every second while claiming 5 m/s north: the kind
            // of broadcast the stateless parser accepts and remoteid rejects.
            let lon = 8.0 + (2_000.0 * i as f64) / 75_800.0;
            let frame = wifi_frame("1781F79079137HJKLMNP", 47.0, lon, 5.0, 0.0, 600.0 + i as f32, i as u8);
            verdict = assess_trust(bssid, &frame, Some(-50), 600_000 + 1_000 * i);
        }
        assert_eq!(verdict, Some(Verdict::Spoofed));
    }
}
