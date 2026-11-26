use std::collections::{HashMap, HashSet};
use std::hash::Hash;
use std::time::Instant;

use crate::link::payloads::{
    Dot11ConnectionFrameReport, Dot11DiscoFrameReport, Dot11DiscoReport, Dot11FrameOverflowReport, SignalStrengthReport
};
use crate::wireless::dot11::frames::{ConnectionFrameType, DisconnectionFrameDetails, Dot11ConnectionFrame, RadiotapHeader};

pub const MAX_DISCO_FRAME_ENTRIES: usize = 2500;
pub const DISCO_FRAME_ENTRIES_FOR_ANY_BSSID: usize = 2000;
pub const MAX_CONNECTION_FRAME_ENTRIES: usize = 5000;
pub const CONNECTION_FRAME_ENTRIES_FOR_ANY_BSSID: usize = 4000;
pub const MAX_FRAME_ENTRIES_PER_BSSID: usize = 500;
pub const MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID: usize = 50;
pub const MAX_OVERFLOW_ENTRIES: usize = 500;
pub const OVERFLOW_ENTRIES_FOR_ANY_BSSID: usize = 250;
pub const MAX_PAIR_SEQUENCE_NUMBERS: usize = 64;

pub trait FrameKey: Eq + Hash {
    fn bssid(&self) -> &str;

    // Neither sent by nor sent to the BSSID it names. An access point takes part in every frame of its network.
    fn is_foreign(&self) -> bool;

    fn frame_type(&self) -> String;
}

// A new row needs room in the table and in the share of its BSSID. The last part of the table only takes rows of
// BSSIDs the tap knows as access points, so that frames with made up addresses do not push out the frames of real
// networks.
#[derive(Debug)]
pub struct BoundedFrameTable<K, V> {
    pub entries: HashMap<K, V>,
    pub overflow: HashMap<(String, String), OverflowStatistics>,
    pub dropped_frames: u64,
    entries_per_bssid: HashMap<String, usize>,
    foreign_entries_per_bssid: HashMap<String, usize>,
    max_entries: usize,
    entries_for_any_bssid: usize
}

impl<K: FrameKey, V: Default> BoundedFrameTable<K, V> {

    pub fn new(max_entries: usize, entries_for_any_bssid: usize) -> Self {
        Self {
            entries: HashMap::new(),
            overflow: HashMap::new(),
            dropped_frames: 0,
            entries_per_bssid: HashMap::new(),
            foreign_entries_per_bssid: HashMap::new(),
            max_entries,
            entries_for_any_bssid
        }
    }

    pub fn aggregate(&mut self, key: K, is_known_access_point: bool, captured_at: Instant) -> Option<&mut V> {
        if !self.entries.contains_key(&key) {
            let limit = if is_known_access_point { self.max_entries } else { self.entries_for_any_bssid };
            let (counters, share) = if key.is_foreign() {
                (&mut self.foreign_entries_per_bssid, MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID)
            } else {
                (&mut self.entries_per_bssid, MAX_FRAME_ENTRIES_PER_BSSID - MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID)
            };

            if self.entries.len() >= limit || counters.get(key.bssid()).copied().unwrap_or(0) >= share {
                self.count_overflow(&key, is_known_access_point, captured_at);
                return None;
            }

            *counters.entry(key.bssid().to_string()).or_insert(0) += 1;
        }

        Some(self.entries.entry(key).or_default())
    }

    fn count_overflow(&mut self, key: &K, is_known_access_point: bool, captured_at: Instant) {
        let overflow_key = (key.bssid().to_string(), key.frame_type());
        let has_rows = self.entries_per_bssid.contains_key(key.bssid())
            || self.foreign_entries_per_bssid.contains_key(key.bssid());
        let limit = if is_known_access_point { MAX_OVERFLOW_ENTRIES } else { OVERFLOW_ENTRIES_FOR_ANY_BSSID };

        if !self.overflow.contains_key(&overflow_key)
            && (self.overflow.len() >= limit || !(is_known_access_point || has_rows)) {
            self.dropped_frames += 1;
            return;
        }

        self.overflow.entry(overflow_key).or_default().register(captured_at);
    }

    pub fn overflow_report(&self, now: Instant) -> Vec<Dot11FrameOverflowReport> {
        self.overflow.iter().map(|((bssid, frame_type), statistics)| {
            let (oldest, newest) = statistics.captured.unwrap_or((now, now));

            Dot11FrameOverflowReport {
                bssid: bssid.clone(),
                frame_type: frame_type.clone(),
                frame_count: statistics.frame_count,
                oldest_frame_age_ms: now.saturating_duration_since(oldest).as_millis() as u64,
                newest_frame_age_ms: now.saturating_duration_since(newest).as_millis() as u64
            }
        }).collect()
    }

    pub fn clear(&mut self) {
        self.entries.clear();
        self.overflow.clear();
        self.entries_per_bssid.clear();
        self.foreign_entries_per_bssid.clear();
        self.dropped_frames = 0;
    }

}

#[derive(Debug, Default)]
pub struct OverflowStatistics {
    pub frame_count: u64,
    captured: Option<(Instant, Instant)>
}

impl OverflowStatistics {
    fn register(&mut self, captured_at: Instant) {
        self.frame_count += 1;
        self.captured = Some(match self.captured {
            Some((oldest, newest)) => (oldest.min(captured_at), newest.max(captured_at)),
            None => (captured_at, captured_at)
        });
    }
}

#[derive(Debug, Default)]
pub struct FrameStatistics {
    pub frame_count: u64,
    pub retry_frames: u64,
    signal_min: i8,
    signal_max: i8,
    signal_sum: i64,
    signal_frames: u64,
    captured: Option<(Instant, Instant)>
}

impl FrameStatistics {

    fn register(&mut self, header: &RadiotapHeader, is_retry: bool) {
        self.frame_count += 1;

        if is_retry {
            self.retry_frames += 1;
        }

        if let Some(signal) = header.antenna_signal {
            if self.signal_frames == 0 {
                self.signal_min = signal;
                self.signal_max = signal;
            } else {
                self.signal_min = self.signal_min.min(signal);
                self.signal_max = self.signal_max.max(signal);
            }

            self.signal_sum += i64::from(signal);
            self.signal_frames += 1;
        }

        // Several processor threads feed the table, so frames do not arrive in the order they were captured.
        self.captured = Some(match self.captured {
            Some((oldest, newest)) => (oldest.min(header.captured_at), newest.max(header.captured_at)),
            None => (header.captured_at, header.captured_at)
        });
    }

    fn signal_strength(&self) -> Option<SignalStrengthReport> {
        if self.signal_frames == 0 {
            return None;
        }

        Some(SignalStrengthReport {
            min: self.signal_min,
            max: self.signal_max,
            average: self.signal_sum as f32 / self.signal_frames as f32
        })
    }

    // Oldest, newest.
    fn frame_ages_ms(&self, now: Instant) -> (u64, u64) {
        match self.captured {
            Some((oldest, newest)) => (
                now.saturating_duration_since(oldest).as_millis() as u64,
                now.saturating_duration_since(newest).as_millis() as u64
            ),
            None => (0, 0)
        }
    }

}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum DiscoType {
    Deauthentication,
    Disassociation
}

impl DiscoType {
    fn report_name(&self) -> String {
        match self {
            DiscoType::Deauthentication => "DEAUTHENTICATION".to_string(),
            DiscoType::Disassociation => "DISASSOCIATION".to_string()
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct DiscoFrameKey {
    pub disco_type: DiscoType,
    pub transmitter: String,
    pub receiver: String,
    pub bssid: String,
    pub reason_code: Option<u16>,
    pub protected: bool,
    pub frequency: Option<u16>
}

impl FrameKey for DiscoFrameKey {
    fn bssid(&self) -> &str {
        &self.bssid
    }

    fn is_foreign(&self) -> bool {
        self.transmitter != self.bssid && self.receiver != self.bssid
    }

    fn frame_type(&self) -> String {
        self.disco_type.report_name()
    }
}

#[derive(Debug, Default)]
pub struct DiscoFrameAggregate {
    pub frames: FrameStatistics,
    pub integrity_frames: u64,
    pub order_frames: u64,

    pub signal_histogram: HashMap<i8, u64>
}

// Transmitter, receiver, BSSID.
type DiscoPair = (String, String, String);

#[derive(Debug)]
pub struct DiscoFrames {
    pub table: BoundedFrameTable<DiscoFrameKey, DiscoFrameAggregate>,

    // Per pair and not per row: rotating reason codes would otherwise spread a burst over rows with one number each.
    pair_sequence_numbers: HashMap<DiscoPair, HashSet<u16>>
}

impl Default for DiscoFrames {
    fn default() -> Self {
        Self {
            table: BoundedFrameTable::new(MAX_DISCO_FRAME_ENTRIES, DISCO_FRAME_ENTRIES_FOR_ANY_BSSID),
            pair_sequence_numbers: HashMap::new()
        }
    }
}

impl DiscoFrames {

    #[allow(clippy::too_many_arguments)]
    pub fn register(&mut self,
                    disco_type: DiscoType,
                    transmitter: String,
                    receiver: String,
                    bssid: String,
                    details: &DisconnectionFrameDetails,
                    header: &RadiotapHeader,
                    is_known_access_point: bool) {
        let pair = (transmitter.clone(), receiver.clone(), bssid.clone());
        let key = DiscoFrameKey {
            disco_type,
            transmitter,
            receiver,
            bssid,
            reason_code: details.reason_code,
            protected: details.protected,
            frequency: header.frequency
        };

        let Some(aggregate) = self.table.aggregate(key, is_known_access_point, header.captured_at) else {
            return;
        };

        aggregate.frames.register(header, details.retry);

        if details.has_integrity_element {
            aggregate.integrity_frames += 1;
        }

        if details.has_order_bit {
            aggregate.order_frames += 1;
        }

        if let Some(signal) = header.antenna_signal {
            *aggregate.signal_histogram.entry(signal).or_insert(0) += 1;
        }

        // Counted per pair over all its rows and frequencies. At the limit, the number means "this many or more".
        let sequence_numbers = self.pair_sequence_numbers.entry(pair).or_default();
        if sequence_numbers.len() < MAX_PAIR_SEQUENCE_NUMBERS {
            sequence_numbers.insert(details.sequence_number);
        }
    }

    pub fn to_report(&self, now: Instant) -> Dot11DiscoReport {
        let mut frames = Vec::with_capacity(self.table.entries.len());

        for (key, aggregate) in &self.table.entries {
            let pair = (key.transmitter.clone(), key.receiver.clone(), key.bssid.clone());
            let (oldest_frame_age_ms, newest_frame_age_ms) = aggregate.frames.frame_ages_ms(now);

            frames.push(Dot11DiscoFrameReport {
                disco_type: key.disco_type.report_name(),
                transmitter: key.transmitter.clone(),
                receiver: key.receiver.clone(),
                bssid: key.bssid.clone(),
                reason_code: key.reason_code,
                protected: key.protected,
                frequency: key.frequency,
                frame_count: aggregate.frames.frame_count,
                retry_frames: aggregate.frames.retry_frames,
                pair_sequence_numbers: self.pair_sequence_numbers.get(&pair).map_or(0, |numbers| numbers.len() as u32),
                integrity_frames: aggregate.integrity_frames,
                order_frames: aggregate.order_frames,
                oldest_frame_age_ms,
                newest_frame_age_ms,
                signal_strength: aggregate.frames.signal_strength(),
                signal_histogram: aggregate.signal_histogram.clone()
            });
        }

        Dot11DiscoReport { frames, overflow: self.table.overflow_report(now) }
    }

    pub fn clear(&mut self) {
        self.table.clear();
        self.pair_sequence_numbers.clear();
    }

}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct ConnectionFrameKey {
    pub frame_type: ConnectionFrameType,
    pub transmitter: String,
    pub receiver: String,
    pub bssid: String,
    pub algorithm: Option<u16>,
    pub transaction: Option<u16>,
    pub status_code: Option<u16>,
    pub frequency: Option<u16>
}

impl FrameKey for ConnectionFrameKey {
    fn bssid(&self) -> &str {
        &self.bssid
    }

    fn is_foreign(&self) -> bool {
        self.transmitter != self.bssid && self.receiver != self.bssid
    }

    fn frame_type(&self) -> String {
        self.frame_type.report_name().to_string()
    }
}

#[derive(Debug)]
pub struct ConnectionFrames {
    pub table: BoundedFrameTable<ConnectionFrameKey, FrameStatistics>
}

impl Default for ConnectionFrames {
    fn default() -> Self {
        Self {
            table: BoundedFrameTable::new(MAX_CONNECTION_FRAME_ENTRIES, CONNECTION_FRAME_ENTRIES_FOR_ANY_BSSID)
        }
    }
}

impl ConnectionFrames {

    pub fn register(&mut self, frame: Dot11ConnectionFrame, is_known_access_point: bool) {
        let key = ConnectionFrameKey {
            frame_type: frame.frame_type,
            transmitter: frame.transmitter,
            receiver: frame.receiver,
            bssid: frame.bssid,
            algorithm: frame.algorithm,
            transaction: frame.transaction,
            status_code: frame.status_code,
            frequency: frame.header.frequency
        };

        if let Some(statistics) = self.table.aggregate(key, is_known_access_point, frame.header.captured_at) {
            statistics.register(&frame.header, frame.retry);
        }
    }

    pub fn clear(&mut self) {
        self.table.clear();
    }

    pub fn overflow_report(&self, now: Instant) -> Vec<Dot11FrameOverflowReport> {
        self.table.overflow_report(now)
    }

    pub fn to_report(&self, now: Instant) -> Vec<Dot11ConnectionFrameReport> {
        let mut frames = Vec::with_capacity(self.table.entries.len());

        for (key, statistics) in &self.table.entries {
            let (oldest_frame_age_ms, newest_frame_age_ms) = statistics.frame_ages_ms(now);

            frames.push(Dot11ConnectionFrameReport {
                frame_type: key.frame_type.report_name().to_string(),
                transmitter: key.transmitter.clone(),
                receiver: key.receiver.clone(),
                bssid: key.bssid.clone(),
                algorithm: key.algorithm,
                transaction: key.transaction,
                status_code: key.status_code,
                frequency: key.frequency,
                frame_count: statistics.frame_count,
                retry_frames: statistics.retry_frames,
                oldest_frame_age_ms,
                newest_frame_age_ms,
                signal_strength: statistics.signal_strength()
            });
        }

        frames
    }

}

#[cfg(test)]
mod tests {
    use std::time::Duration;

    use super::*;

    const AP: &str = "02:11:22:33:44:55";
    const CLIENT: &str = "02:AA:BB:CC:DD:EE";

    fn header(frequency: u16, signal: i8, captured_at: Instant) -> RadiotapHeader {
        RadiotapHeader {
            is_wep: None,
            data_rate: None,
            frequency: Some(frequency),
            channel: None,
            antenna_signal: Some(signal),
            antenna: None,
            captured_at
        }
    }

    fn details(reason_code: Option<u16>, sequence_number: u16) -> DisconnectionFrameDetails {
        DisconnectionFrameDetails {
            reason_code,
            protected: reason_code.is_none(),
            retry: false,
            has_order_bit: false,
            sequence_number,
            has_integrity_element: false
        }
    }

    fn deauth(table: &mut DiscoFrames, transmitter: &str, receiver: &str, bssid: &str,
              details: &DisconnectionFrameDetails, known: bool) {
        table.register(
            DiscoType::Deauthentication, transmitter.to_string(), receiver.to_string(), bssid.to_string(),
            details, &header(2437, -50, Instant::now()), known
        );
    }

    fn mac(prefix: u8, number: usize) -> String {
        format!("{:02X}:00:00:00:{:02X}:{:02X}", prefix, number >> 8, number & 0xff)
    }

    fn connection_frame(frame_type: ConnectionFrameType, transmitter: &str, bssid: &str, status_code: Option<u16>,
                        captured_at: Instant) -> Dot11ConnectionFrame {
        Dot11ConnectionFrame {
            header: header(5180, -60, captured_at),
            frame_type,
            receiver: bssid.to_string(),
            transmitter: transmitter.to_string(),
            bssid: bssid.to_string(),
            algorithm: None,
            transaction: None,
            status_code,
            retry: false
        }
    }

    fn rows_of(report: &Dot11DiscoReport, reason_code: Option<u16>) -> Vec<&Dot11DiscoFrameReport> {
        report.frames.iter().filter(|frame| frame.reason_code == reason_code).collect()
    }

    #[test]
    fn aggregates_frames_of_a_key_with_retries_integrity_and_signal() {
        let mut table = DiscoFrames::default();
        let now = Instant::now();

        let first = DisconnectionFrameDetails {
            reason_code: Some(7), protected: false, retry: false, has_order_bit: false, sequence_number: 10,
            has_integrity_element: true
        };
        let retry = DisconnectionFrameDetails { retry: true, has_integrity_element: false, ..first };

        table.register(DiscoType::Deauthentication, AP.to_string(), CLIENT.to_string(), AP.to_string(), &first,
                       &header(2437, -40, now), false);
        table.register(DiscoType::Deauthentication, AP.to_string(), CLIENT.to_string(), AP.to_string(), &retry,
                       &header(2437, -60, now), false);
        table.register(DiscoType::Deauthentication, AP.to_string(), CLIENT.to_string(), AP.to_string(), &retry,
                       &header(2437, -50, now), false);

        let report = table.to_report(now);
        assert_eq!(report.frames.len(), 1);

        let row = &report.frames[0];
        assert_eq!(row.disco_type, "DEAUTHENTICATION");
        assert_eq!(row.reason_code, Some(7));
        assert!(!row.protected);
        assert_eq!(row.frame_count, 3);
        assert_eq!(row.retry_frames, 2);
        assert_eq!(row.integrity_frames, 1);
        assert_eq!(row.order_frames, 0);
        assert_eq!(row.pair_sequence_numbers, 1);

        let signal = row.signal_strength.as_ref().unwrap();
        assert_eq!((signal.min, signal.max, signal.average), (-60, -40, -50.0));
    }

    #[test]
    fn counts_frames_with_the_order_bit_without_splitting_the_row() {
        let mut table = DiscoFrames::default();
        let with_order_bit = DisconnectionFrameDetails { has_order_bit: true, ..details(Some(7), 1) };

        deauth(&mut table, AP, CLIENT, AP, &details(Some(7), 1), false);
        deauth(&mut table, AP, CLIENT, AP, &with_order_bit, false);
        deauth(&mut table, AP, CLIENT, AP, &with_order_bit, false);
        deauth(&mut table, CLIENT, AP, AP, &details(Some(7), 1), false);

        let report = table.to_report(Instant::now());
        assert_eq!(report.frames.len(), 2);

        let from_ap = report.frames.iter().find(|frame| frame.transmitter == AP).unwrap();
        assert_eq!((from_ap.frame_count, from_ap.order_frames), (3, 2));

        let from_client = report.frames.iter().find(|frame| frame.transmitter == CLIENT).unwrap();
        assert_eq!((from_client.frame_count, from_client.order_frames), (1, 0));
    }

    #[test]
    fn protected_and_unprotected_frames_do_not_share_a_row() {
        let mut table = DiscoFrames::default();

        deauth(&mut table, AP, CLIENT, AP, &details(Some(3), 1), false);
        deauth(&mut table, AP, CLIENT, AP, &details(None, 2), false);

        let report = table.to_report(Instant::now());
        assert_eq!(report.frames.len(), 2);
        assert!(rows_of(&report, None)[0].protected);
        assert!(!rows_of(&report, Some(3))[0].protected);
    }

    #[test]
    fn counts_sequence_numbers_per_pair_across_rows() {
        let mut table = DiscoFrames::default();

        for sequence_number in 0..5 {
            deauth(&mut table, AP, CLIENT, AP, &details(Some(7), sequence_number), false);
        }
        for sequence_number in 3..9 {
            deauth(&mut table, AP, CLIENT, AP, &details(Some(1), sequence_number), false);
        }
        deauth(&mut table, CLIENT, AP, AP, &details(Some(7), 100), false);

        let report = table.to_report(Instant::now());
        assert_eq!(report.frames.len(), 3);

        for row in &report.frames {
            let expected = if row.transmitter == AP { 9 } else { 1 };
            assert_eq!(row.pair_sequence_numbers, expected);
        }
    }

    #[test]
    fn sequence_numbers_of_a_pair_saturate() {
        let mut table = DiscoFrames::default();

        for sequence_number in 0..200 {
            deauth(&mut table, AP, CLIENT, AP, &details(Some(7), sequence_number), false);
        }

        let report = table.to_report(Instant::now());
        assert_eq!(report.frames[0].frame_count, 200);
        assert_eq!(report.frames[0].pair_sequence_numbers, MAX_PAIR_SEQUENCE_NUMBERS as u32);
    }

    #[test]
    fn reports_the_ages_of_the_oldest_and_newest_frame() {
        let mut table = DiscoFrames::default();
        let mut connections = ConnectionFrames::default();

        let first = Instant::now();
        let second = first + Duration::from_millis(2500);
        let report_at = second + Duration::from_millis(1000);

        // Out of order on purpose.
        for captured_at in [second, first] {
            table.register(DiscoType::Disassociation, AP.to_string(), CLIENT.to_string(), AP.to_string(),
                           &details(Some(8), 1), &header(2437, -50, captured_at), false);
            connections.register(
                connection_frame(ConnectionFrameType::Handshake2, CLIENT, AP, None, captured_at), false
            );
        }

        let disco = &table.to_report(report_at).frames[0];
        assert_eq!(disco.oldest_frame_age_ms, 3500);
        assert_eq!(disco.newest_frame_age_ms, 1000);
        assert_eq!(disco.oldest_frame_age_ms - disco.newest_frame_age_ms, 2500);

        let connection = &connections.to_report(report_at)[0];
        assert_eq!(connection.oldest_frame_age_ms, 3500);
        assert_eq!(connection.newest_frame_age_ms, 1000);

        let early = &table.to_report(first).frames[0];
        assert_eq!((early.oldest_frame_age_ms, early.newest_frame_age_ms), (0, 0));
    }

    #[test]
    fn serializes_the_fields_the_node_reads() {
        let mut table = DiscoFrames::default();
        let mut connections = ConnectionFrames::default();
        let now = Instant::now();

        deauth(&mut table, AP, CLIENT, AP, &details(None, 1), false);
        connections.register(connection_frame(ConnectionFrameType::EapFailure, AP, AP, None, now), false);

        let disco = serde_json::to_value(table.to_report(now)).unwrap();
        let row = disco["frames"][0].as_object().unwrap();
        assert!(row["reason_code"].is_null());
        assert_eq!(row["protected"], true);
        let mut fields: Vec<&str> = row.keys().map(String::as_str).collect();
        fields.sort();
        assert_eq!(fields, vec![
            "bssid", "disco_type", "frame_count", "frequency", "integrity_frames", "newest_frame_age_ms",
            "oldest_frame_age_ms", "order_frames", "pair_sequence_numbers", "protected", "reason_code", "receiver",
            "retry_frames", "signal_histogram", "signal_strength", "transmitter"
        ]);
        assert_eq!(row["signal_histogram"]["-50"], 1);

        let connections = serde_json::to_value(connections.to_report(now)).unwrap();
        let row = connections[0].as_object().unwrap();
        assert_eq!(row["frame_type"], "EAP_FAILURE");
        assert!(row["algorithm"].is_null() && row["transaction"].is_null() && row["status_code"].is_null());
        assert_eq!(row["signal_strength"]["min"], -60);
        let mut fields: Vec<&str> = row.keys().map(String::as_str).collect();
        fields.sort();
        assert_eq!(fields, vec![
            "algorithm", "bssid", "frame_count", "frame_type", "frequency", "newest_frame_age_ms",
            "oldest_frame_age_ms", "receiver", "retry_frames", "signal_strength", "status_code", "transaction",
            "transmitter"
        ]);
    }

    #[test]
    fn disco_table_drops_new_keys_when_full_and_keeps_counting_known_ones() {
        let mut table = DiscoFrames::default();

        for i in 0..MAX_DISCO_FRAME_ENTRIES + 100 {
            deauth(&mut table, &mac(0x02, i), CLIENT, &mac(0x06, i), &details(Some(7), 1), true);
        }

        assert_eq!(table.table.entries.len(), MAX_DISCO_FRAME_ENTRIES);
        assert_eq!(table.table.overflow.len(), 100);
        assert_eq!(table.table.dropped_frames, 0);

        deauth(&mut table, &mac(0x0a, 1), CLIENT, &mac(0x0e, 1), &details(Some(7), 1), false);
        assert_eq!(table.table.overflow.len(), 100);
        assert_eq!(table.table.dropped_frames, 1);

        deauth(&mut table, &mac(0x02, 0), CLIENT, &mac(0x06, 0), &details(Some(7), 2), false);
        assert_eq!(table.table.dropped_frames, 1);

        let report = table.to_report(Instant::now());
        let counted = report.frames.iter().find(|frame| frame.transmitter == mac(0x02, 0)).unwrap();
        assert_eq!(counted.frame_count, 2);
        assert_eq!(counted.pair_sequence_numbers, 2);
        assert_eq!(table.pair_sequence_numbers.len(), MAX_DISCO_FRAME_ENTRIES);
    }

    #[test]
    fn one_bssid_cannot_fill_the_disco_table() {
        let mut table = DiscoFrames::default();

        for i in 0..MAX_FRAME_ENTRIES_PER_BSSID + 50 {
            deauth(&mut table, AP, &mac(0x02, i), AP, &details(Some(7), 1), true);
        }
        for reason_code in 0..100 {
            deauth(&mut table, AP, &mac(0x02, 0), AP, &details(Some(1000 + reason_code), 1), true);
        }

        let share = MAX_FRAME_ENTRIES_PER_BSSID - MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID;
        assert_eq!(table.table.entries.len(), share);
        assert_eq!(table.table.dropped_frames, 0);

        deauth(&mut table, "02:00:00:00:00:01", CLIENT, "02:00:00:00:00:01", &details(Some(7), 1), false);
        assert_eq!(table.table.entries.len(), share + 1);

        let report = table.to_report(Instant::now());
        assert_eq!(report.overflow.len(), 1);
        assert_eq!(report.overflow[0].bssid, AP);
        assert_eq!(report.overflow[0].frame_type, "DEAUTHENTICATION");
        assert_eq!(report.overflow[0].frame_count, 200);

        table.clear();
        assert!(table.table.overflow.is_empty());
    }

    #[test]
    fn overflow_rows_are_limited_and_kept_apart_by_frame_type() {
        let mut table = DiscoFrames::default();

        for i in 0..OVERFLOW_ENTRIES_FOR_ANY_BSSID + 20 {
            deauth(&mut table, &mac(0x02, i), CLIENT, &mac(0x02, i), &details(Some(7), 1), false);
        }
        for i in 0..OVERFLOW_ENTRIES_FOR_ANY_BSSID + 20 {
            for other in 0..MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID + 1 {
                deauth(&mut table, &mac(0x0a, other), &mac(0x0e, other), &mac(0x02, i), &details(Some(7), 1), false);
            }
        }

        let dropped = 20 * (MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID as u64 + 1);
        assert_eq!(table.table.overflow.len(), OVERFLOW_ENTRIES_FOR_ANY_BSSID);
        assert_eq!(table.table.dropped_frames, dropped);

        for i in 0..MAX_FRAME_ENTRIES_PER_BSSID + 3 {
            deauth(&mut table, AP, &mac(0x12, i), AP, &details(Some(7), 1), true);
        }
        table.register(
            DiscoType::Disassociation, AP.to_string(), mac(0x16, 0), AP.to_string(), &details(Some(8), 1),
            &header(2437, -50, Instant::now()), true
        );

        let report = table.to_report(Instant::now());
        let of_ap: Vec<_> = report.overflow.iter().filter(|overflow| overflow.bssid == AP).collect();
        assert_eq!(of_ap.len(), 2);
        assert_eq!(of_ap.iter().find(|o| o.frame_type == "DEAUTHENTICATION").unwrap().frame_count, 53);
        assert_eq!(of_ap.iter().find(|o| o.frame_type == "DISASSOCIATION").unwrap().frame_count, 1);
        assert_eq!(table.table.dropped_frames, dropped);
    }

    #[test]
    fn connection_frames_overflow_and_serialize_what_the_node_reads() {
        let mut table = ConnectionFrames::default();
        let captured = Instant::now();

        for i in 0..MAX_FRAME_ENTRIES_PER_BSSID {
            table.register(connection_frame(ConnectionFrameType::AssociationRequest, &mac(0x02, i), AP, None, captured), true);
        }
        table.register(connection_frame(ConnectionFrameType::Authentication, CLIENT, AP, None, captured), true);

        let report = table.overflow_report(captured + Duration::from_millis(1500));
        assert_eq!(report.len(), 2);

        let authentication = report.iter().find(|overflow| overflow.frame_type == "AUTHENTICATION").unwrap();
        assert_eq!(authentication.frame_count, 1);

        let json = serde_json::to_value(&report).unwrap();
        let row = json.as_array().unwrap().iter().find(|row| row["frame_type"] == "ASSOCIATION_REQUEST").unwrap();
        assert_eq!(row["bssid"], AP);
        assert_eq!(row["frame_count"], MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID as u64);
        assert_eq!(row["oldest_frame_age_ms"], 1500);
        assert_eq!(row["newest_frame_age_ms"], 1500);
        assert_eq!(row.as_object().unwrap().len(), 5);
    }

    #[test]
    fn frames_that_only_name_a_bssid_cannot_use_up_its_share() {
        let mut table = DiscoFrames::default();

        for i in 0..MAX_FRAME_ENTRIES_PER_BSSID {
            deauth(&mut table, &mac(0x02, i), &mac(0x06, i), AP, &details(Some(7), 1), true);
        }
        assert_eq!(table.table.entries.len(), MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID);

        deauth(&mut table, AP, CLIENT, AP, &details(Some(7), 1), true);
        deauth(&mut table, CLIENT, AP, AP, &details(Some(3), 2), true);
        assert_eq!(table.table.entries.len(), MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID + 2);

        let mut connections = ConnectionFrames::default();
        let now = Instant::now();
        for i in 0..MAX_FRAME_ENTRIES_PER_BSSID {
            let mut frame = connection_frame(ConnectionFrameType::AssociationRequest, &mac(0x02, i), &mac(0x06, i), None, now);
            frame.bssid = AP.to_string();
            connections.register(frame, true);
        }
        assert_eq!(connections.table.entries.len(), MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID);

        connections.register(connection_frame(ConnectionFrameType::Authentication, CLIENT, AP, None, now), true);
        assert_eq!(connections.table.entries.len(), MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID + 1);
    }

    #[test]
    fn last_part_of_the_disco_table_is_kept_for_known_access_points() {
        let mut table = DiscoFrames::default();

        for i in 0..MAX_DISCO_FRAME_ENTRIES {
            deauth(&mut table, &mac(0x02, i), CLIENT, &mac(0x06, i), &details(Some(7), 1), false);
        }

        assert_eq!(table.table.entries.len(), DISCO_FRAME_ENTRIES_FOR_ANY_BSSID);
        assert_eq!(table.table.dropped_frames, (MAX_DISCO_FRAME_ENTRIES - DISCO_FRAME_ENTRIES_FOR_ANY_BSSID) as u64);

        deauth(&mut table, AP, CLIENT, AP, &details(Some(7), 1), true);
        assert_eq!(table.table.entries.len(), DISCO_FRAME_ENTRIES_FOR_ANY_BSSID + 1);
    }

    #[test]
    fn clearing_gives_every_bssid_its_share_back() {
        let mut table = DiscoFrames::default();

        for i in 0..MAX_FRAME_ENTRIES_PER_BSSID {
            deauth(&mut table, AP, &mac(0x02, i), AP, &details(Some(7), 1), true);
        }
        table.clear();

        deauth(&mut table, AP, CLIENT, AP, &details(Some(7), 1), true);
        assert_eq!(table.table.entries.len(), 1);
        assert_eq!(table.table.dropped_frames, 0);
        assert_eq!(table.to_report(Instant::now()).frames[0].pair_sequence_numbers, 1);
    }

    #[test]
    fn connection_frames_key_by_type_addresses_fields_and_frequency() {
        let mut table = ConnectionFrames::default();
        let now = Instant::now();

        table.register(connection_frame(ConnectionFrameType::AssociationResponse, AP, AP, Some(30), now), false);
        table.register(connection_frame(ConnectionFrameType::AssociationResponse, AP, AP, Some(30), now), false);
        table.register(connection_frame(ConnectionFrameType::AssociationResponse, AP, AP, Some(0), now), false);
        table.register(connection_frame(ConnectionFrameType::ReassociationResponse, AP, AP, Some(0), now), false);

        let mut retry = connection_frame(ConnectionFrameType::AssociationResponse, AP, AP, Some(30), now);
        retry.retry = true;
        retry.header.antenna_signal = None;
        table.register(retry, false);

        let mut other_channel = connection_frame(ConnectionFrameType::AssociationResponse, AP, AP, Some(30), now);
        other_channel.header.frequency = Some(5200);
        other_channel.header.antenna_signal = None;
        table.register(other_channel, false);

        let report = table.to_report(now);
        assert_eq!(report.len(), 4);

        let rejected = report.iter()
            .find(|frame| frame.status_code == Some(30) && frame.frequency == Some(5180))
            .unwrap();
        assert_eq!(rejected.frame_type, "ASSOCIATION_RESPONSE");
        assert_eq!(rejected.frame_count, 3);
        assert_eq!(rejected.retry_frames, 1);
        assert_eq!(rejected.signal_strength.as_ref().unwrap().average, -60.0);

        let without_signal = report.iter().find(|frame| frame.frequency == Some(5200)).unwrap();
        assert!(without_signal.signal_strength.is_none());
    }

    #[test]
    fn connection_frames_table_bounds() {
        let mut table = ConnectionFrames::default();
        let now = Instant::now();

        for i in 0..MAX_CONNECTION_FRAME_ENTRIES {
            table.register(
                connection_frame(ConnectionFrameType::Authentication, &mac(0x02, i), &mac(0x06, i), None, now), false
            );
        }
        assert_eq!(table.table.entries.len(), CONNECTION_FRAME_ENTRIES_FOR_ANY_BSSID);

        for i in 0..MAX_FRAME_ENTRIES_PER_BSSID + 100 {
            table.register(connection_frame(ConnectionFrameType::Authentication, &mac(0x0a, i), AP, None, now), true);
        }
        assert_eq!(
            table.table.entries.len(),
            CONNECTION_FRAME_ENTRIES_FOR_ANY_BSSID + MAX_FRAME_ENTRIES_PER_BSSID - MAX_FOREIGN_FRAME_ENTRIES_PER_BSSID
        );

        for i in 0..MAX_CONNECTION_FRAME_ENTRIES {
            table.register(
                connection_frame(ConnectionFrameType::Authentication, &mac(0x0e, i), &mac(0x12, i), None, now), true
            );
        }
        assert_eq!(table.table.entries.len(), MAX_CONNECTION_FRAME_ENTRIES);

        let dropped = table.table.dropped_frames;
        table.register(connection_frame(ConnectionFrameType::Authentication, &mac(0x0a, 0), AP, None, now), false);
        assert_eq!(table.table.dropped_frames, dropped);
    }
}
