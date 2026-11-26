use std::collections::hash_map::Entry;
use std::collections::{HashMap, VecDeque};
use std::time::{Duration, Instant};

use crate::link::payloads::{Dot11ChannelSwitchReport, SignalStrengthReport};
use crate::wireless::dot11::frames::{ChannelSwitchAnnouncement, ChannelSwitchAnnouncementType};

/*
 * A full table of announcements gives the place of a BSSID that has beaconed without an announcement for a
 * shorter time, or never, to one that has done so for longer. Made up BSSIDs then lose against access points the
 * tap has known for a while, but not against one that appears after them. A BSSID at its own limit gives the
 * place of its announcement with the fewest frames.
 */
pub const MAX_CHANNEL_SWITCH_ENTRIES: usize = 500;
pub const MAX_ENTRIES_PER_BSSID: usize = 16;

// New BSSIDs are refused when this many are tracked, so that a flood does not push out the known ones.
pub const MAX_BEACON_STATES: usize = 10_000;

// More are counted than reported, so that the channels with the most beacons are the ones that are reported.
pub const MAX_DECLARED_FREQUENCIES: usize = 8;
const MAX_COUNTED_DECLARED_FREQUENCIES: usize = 32;
const MAX_SIGNAL_FREQUENCIES: usize = 8;

// Longer than a tap takes to hop through all of its channels, or a BSSID is new again on every visit.
const BEACON_STATE_TTL: Duration = Duration::from_secs(900);
const INTERLEAVE_WINDOW: Duration = Duration::from_secs(2);
const RATE_WINDOW: Duration = Duration::from_secs(1);
const MAX_RATE_WINDOW_FRAMES: usize = 2048;
const TIMESTAMP_TOLERANCE_MICROS: u64 = 5_000_000;

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum ChannelSwitchFrameType {
    Beacon,
    ProbeResponse,
    Action
}

impl ChannelSwitchFrameType {
    fn report_name(&self) -> String {
        match self {
            ChannelSwitchFrameType::Beacon => "BEACON".to_string(),
            ChannelSwitchFrameType::ProbeResponse => "PROBE_RESPONSE".to_string(),
            ChannelSwitchFrameType::Action => "ACTION".to_string()
        }
    }
}

/*
 * The frequency is the channel the frame says it was sent on. Only without that it is the one the tap was tuned to.
 * Clients read the switch mode as a yes or no, so its other values do not make another announcement.
 */
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct ChannelSwitchKey {
    pub bssid: String,
    pub frame_type: ChannelSwitchFrameType,
    pub announcement_type: ChannelSwitchAnnouncementType,
    pub stops_transmission: bool,
    pub new_operating_class: Option<u8>,
    pub new_channel: u8,
    pub frequency: Option<u16>
}

#[derive(Debug)]
pub struct ChannelSwitchAggregate {
    pub switch_mode: u8,
    pub frame_count: u64,
    pub count_min: u8,
    pub count_max: u8,
    pub signal_min: i8,
    pub signal_max: i8,
    pub signal_sum: i64,
    pub signal_frames: u64,
    pub signal_histogram: HashMap<u16, HashMap<i8, u64>>,
    pub trailing_element_frames: u64,
    pub timestamp_repetitions: u32,
    pub timestamp_mismatch_frames: u64,
    pub interleaved_beacons: u64,
    pub peak_frames_per_second: u32,
    pub dsss_only: Option<bool>,
    pub country_code: Option<String>,
    recent_frames: VecDeque<Instant>,
    timestamp_run: Option<(u64, u32)>,
    known_since: Option<Instant>
}

impl ChannelSwitchAggregate {
    fn new(switch_mode: u8, switch_count: u8, known_since: Option<Instant>) -> Self {
        Self {
            switch_mode,
            frame_count: 0,
            count_min: switch_count,
            count_max: switch_count,
            signal_min: 0,
            signal_max: 0,
            signal_sum: 0,
            signal_frames: 0,
            signal_histogram: HashMap::new(),
            trailing_element_frames: 0,
            timestamp_repetitions: 0,
            timestamp_mismatch_frames: 0,
            interleaved_beacons: 0,
            peak_frames_per_second: 0,
            dsss_only: None,
            country_code: None,
            recent_frames: VecDeque::new(),
            timestamp_run: None,
            known_since
        }
    }

    fn register_frame(&mut self,
                      announcement: &ChannelSwitchAnnouncement,
                      tuned_frequency: Option<u16>,
                      signal: Option<i8>,
                      now: Instant) {
        self.frame_count += 1;
        self.count_min = self.count_min.min(announcement.switch_count);
        self.count_max = self.count_max.max(announcement.switch_count);

        if announcement.is_trailing_element {
            self.trailing_element_frames += 1;
        }

        if let Some(dsss_only) = announcement.dsss_only {
            self.dsss_only = Some(self.dsss_only.unwrap_or(true) && dsss_only);
        }

        if self.country_code.is_none() {
            self.country_code.clone_from(&announcement.country_code);
        }

        if let Some(signal) = signal {
            if self.signal_frames == 0 {
                self.signal_min = signal;
                self.signal_max = signal;
            } else {
                self.signal_min = self.signal_min.min(signal);
                self.signal_max = self.signal_max.max(signal);
            }
            self.signal_sum += signal as i64;
            self.signal_frames += 1;

            if let Some(tuned_frequency) = tuned_frequency {
                if self.signal_histogram.len() < MAX_SIGNAL_FREQUENCIES
                    || self.signal_histogram.contains_key(&tuned_frequency) {
                    *self.signal_histogram.entry(tuned_frequency).or_default().entry(signal).or_insert(0) += 1;
                }
            }
        }

        while let Some(oldest) = self.recent_frames.front() {
            if now.duration_since(*oldest) > RATE_WINDOW || self.recent_frames.len() >= MAX_RATE_WINDOW_FRAMES {
                self.recent_frames.pop_front();
            } else {
                break;
            }
        }
        self.recent_frames.push_back(now);
        self.peak_frames_per_second = self.peak_frames_per_second.max(self.recent_frames.len() as u32);
    }

    // A replayed frame keeps the timestamp it was captured with. Hardware stamps every frame it sends.
    fn register_timestamp(&mut self, timestamp: u64) {
        let run = match self.timestamp_run {
            Some((previous, run)) if previous == timestamp => run + 1,
            _ => 1
        };

        self.timestamp_run = Some((timestamp, run));
        self.timestamp_repetitions = self.timestamp_repetitions.max(run);
    }
}

fn band(frequency: u16) -> u8 {
    match frequency {
        frequency if frequency < 3000 => 2,
        frequency if frequency < 5925 => 5,
        _ => 6
    }
}

/*
 * A frame was sent on the channel it names, if that is in the band the tap was tuned to: a tap does not receive
 * across bands, and a client only follows what fits where it listens. A frame that names nothing usable was sent
 * where the tap was tuned, as far as the tap can tell.
 */
pub fn sent_on(tuned_frequency: Option<u16>, declared_frequency: Option<u16>) -> Option<u16> {
    match (tuned_frequency, declared_frequency) {
        (Some(tuned), Some(declared)) if band(tuned) == band(declared) => Some(declared),
        (Some(tuned), _) => Some(tuned),
        (None, declared) => declared
    }
}

#[derive(Debug)]
struct BeaconState {
    last_seen: Instant,
    first_plain: Option<Instant>,
    declared_frequency: Option<u16>,
    last_plain: Option<(u64, Instant)>,
    last_announcement: Option<Instant>,
    plain_since_announcement: u32
}

#[derive(Debug, Default)]
pub struct ChannelSwitchTable {
    pub entries: HashMap<ChannelSwitchKey, ChannelSwitchAggregate>,
    pub dropped_frames: u64,
    pub action_frames: u64,
    pub rejected_beacon_states: u64,
    pub dropped_declared_frequencies: u64,
    bssid_entries: HashMap<String, usize>,
    declared_frequencies: HashMap<String, HashMap<u16, u64>>,
    beacon_states: HashMap<String, HashMap<u16, BeaconState>>,
    beacon_state_count: usize
}

impl ChannelSwitchTable {

    pub fn register_beacon(&mut self,
                           bssid: &str,
                           frequency: Option<u16>,
                           signal: Option<i8>,
                           timestamp: u64,
                           announcement: Option<&ChannelSwitchAnnouncement>,
                           declared: Option<u16>,
                           now: Instant) {
        let sent_on = sent_on(frequency, declared);

        let (interleaved_beacons, timestamp_mismatch) = match frequency {
            Some(frequency) => {
                self.track_frame(bssid, frequency, Some(timestamp), sent_on, announcement.is_some(), now)
            },
            None => (0, false)
        };

        if let Some(sent_on) = sent_on {
            self.register_declared_frequency(bssid, sent_on);
        }

        let Some(announcement) = announcement else {
            return;
        };

        let Some(aggregate) = self.aggregate(bssid, ChannelSwitchFrameType::Beacon, sent_on, announcement) else {
            return;
        };

        aggregate.register_frame(announcement, frequency, signal, now);
        aggregate.register_timestamp(timestamp);
        aggregate.interleaved_beacons += u64::from(interleaved_beacons);

        if timestamp_mismatch {
            aggregate.timestamp_mismatch_frames += 1;
        }
    }

    pub fn register_frame(&mut self,
                          bssid: &str,
                          frame_type: ChannelSwitchFrameType,
                          frequency: Option<u16>,
                          signal: Option<i8>,
                          announcement: &ChannelSwitchAnnouncement,
                          now: Instant) {
        let interleaved_beacons = match frequency {
            Some(frequency) => self.track_frame(bssid, frequency, None, None, true, now).0,
            None => 0
        };

        // Action frames do not say where they were sent, so the beacons this tuning last decoded speak for them.
        let sent_on = match announcement.current_frequency {
            Some(declared) => sent_on(frequency, Some(declared)),
            None => self.beacon_states.get(bssid)
                .and_then(|frequencies| frequencies.get(&frequency?))
                .and_then(|state| state.declared_frequency)
                .or(frequency)
        };

        let Some(aggregate) = self.aggregate(bssid, frame_type, sent_on, announcement) else {
            return;
        };

        aggregate.register_frame(announcement, frequency, signal, now);
        aggregate.interleaved_beacons += u64::from(interleaved_beacons);
    }

    /*
     * Returns the number of beacons without an announcement that arrived since the previous frame with one,
     * and if the timestamp of a beacon with an announcement is off the clock of the beacons without one.
     * Only beacons pass a timestamp.
     */
    fn track_frame(&mut self,
                   bssid: &str,
                   frequency: u16,
                   beacon_timestamp: Option<u64>,
                   declared_frequency: Option<u16>,
                   has_announcement: bool,
                   now: Instant) -> (u32, bool) {
        if !self.beacon_states.contains_key(bssid) {
            if self.beacon_state_count >= MAX_BEACON_STATES {
                self.rejected_beacon_states += 1;
                return (0, false);
            }

            self.beacon_states.insert(bssid.to_string(), HashMap::new());
        }

        let state = match self.beacon_states.get_mut(bssid).unwrap().entry(frequency) {
            Entry::Occupied(state) => state.into_mut(),
            Entry::Vacant(vacant) => {
                if self.beacon_state_count >= MAX_BEACON_STATES {
                    self.rejected_beacon_states += 1;
                    return (0, false);
                }

                self.beacon_state_count += 1;
                vacant.insert(BeaconState {
                    last_seen: now,
                    first_plain: None,
                    declared_frequency: None,
                    last_plain: None,
                    last_announcement: None,
                    plain_since_announcement: 0
                })
            }
        };
        state.last_seen = now;
        if declared_frequency.is_some() {
            state.declared_frequency = declared_frequency;
        }

        let follows_announcement = state.last_announcement
            .is_some_and(|at| now.duration_since(at) <= INTERLEAVE_WINDOW);

        if !has_announcement {
            if follows_announcement {
                state.plain_since_announcement += 1;
            } else {
                state.last_announcement = None;
                state.plain_since_announcement = 0;
            }

            if let Some(timestamp) = beacon_timestamp {
                state.first_plain.get_or_insert(now);
                state.last_plain = Some((timestamp, now));
            }
            return (0, false);
        }

        let interleaved = if follows_announcement { state.plain_since_announcement } else { 0 };
        state.last_announcement = Some(now);
        state.plain_since_announcement = 0;

        let timestamp_mismatch = match (state.last_plain, beacon_timestamp) {
            (Some((plain_timestamp, at)), Some(timestamp)) if now.duration_since(at) <= BEACON_STATE_TTL => {
                let expected = plain_timestamp.saturating_add(now.duration_since(at).as_micros() as u64);
                expected.abs_diff(timestamp) > TIMESTAMP_TOLERANCE_MICROS
            },
            _ => false
        };

        (interleaved, timestamp_mismatch)
    }

    fn aggregate(&mut self,
                 bssid: &str,
                 frame_type: ChannelSwitchFrameType,
                 frequency: Option<u16>,
                 announcement: &ChannelSwitchAnnouncement) -> Option<&mut ChannelSwitchAggregate> {
        let key = ChannelSwitchKey {
            bssid: bssid.to_string(),
            frame_type,
            announcement_type: announcement.announcement_type.clone(),
            stops_transmission: announcement.switch_mode != 0,
            new_operating_class: announcement.new_operating_class,
            new_channel: announcement.new_channel,
            frequency
        };

        if !self.entries.contains_key(&key) {
            let known_since = self.known_since(bssid);

            if self.bssid_entries.get(bssid).is_some_and(|entries| *entries >= MAX_ENTRIES_PER_BSSID) {
                let fewest_frames = self.entries.iter()
                    .filter(|(other, _)| other.bssid == bssid)
                    .min_by_key(|(_, aggregate)| aggregate.frame_count)
                    .map(|(other, _)| other.clone());

                if let Some(fewest_frames) = fewest_frames {
                    self.remove_entry(&fewest_frames);
                }
            } else if self.entries.len() >= MAX_CHANNEL_SWITCH_ENTRIES && !self.make_room_for(known_since) {
                self.dropped_frames += 1;
                return None;
            }

            *self.bssid_entries.entry(bssid.to_string()).or_insert(0) += 1;
            self.entries.insert(
                key.clone(),
                ChannelSwitchAggregate::new(announcement.switch_mode, announcement.switch_count, known_since)
            );
        }

        self.entries.get_mut(&key)
    }

    fn make_room_for(&mut self, known_since: Option<Instant>) -> bool {
        let Some(known_since) = known_since else {
            return false;
        };

        let newest = self.entries.iter()
            .filter(|(_, aggregate)| aggregate.known_since.is_none_or(|other| other > known_since))
            .max_by_key(|(_, aggregate)| (aggregate.known_since.is_none(), aggregate.known_since))
            .map(|(key, _)| key.clone());

        let Some(key) = newest else {
            return false;
        };

        self.remove_entry(&key);

        true
    }

    fn remove_entry(&mut self, key: &ChannelSwitchKey) {
        if let Some(aggregate) = self.entries.remove(key) {
            self.dropped_frames += aggregate.frame_count;
        }

        if let Some(entries) = self.bssid_entries.get_mut(&key.bssid) {
            *entries -= 1;
            if *entries == 0 {
                self.bssid_entries.remove(&key.bssid);
            }
        }
    }

    fn known_since(&self, bssid: &str) -> Option<Instant> {
        self.beacon_states.get(bssid)
            .and_then(|frequencies| frequencies.values().filter_map(|state| state.first_plain).min())
    }

    // The channels the beacons of a BSSID were sent on in this report, which is what the node judges where it is by.
    fn register_declared_frequency(&mut self, bssid: &str, declared_frequency: u16) {
        if !self.declared_frequencies.contains_key(bssid) {
            if self.declared_frequencies.len() >= MAX_BEACON_STATES {
                self.dropped_declared_frequencies += 1;
                return;
            }

            self.declared_frequencies.insert(bssid.to_string(), HashMap::new());
        }

        if let Some(frequencies) = self.declared_frequencies.get_mut(bssid) {
            if frequencies.len() < MAX_COUNTED_DECLARED_FREQUENCIES || frequencies.contains_key(&declared_frequency) {
                *frequencies.entry(declared_frequency).or_insert(0) += 1;
            } else {
                self.dropped_declared_frequencies += 1;
            }
        }
    }

    pub fn declared_frequencies(&self) -> HashMap<String, HashMap<u16, u64>> {
        self.declared_frequencies.iter().map(|(bssid, frequencies)| {
            let mut most_beacons: Vec<(u16, u64)> = frequencies.iter()
                .map(|(frequency, beacons)| (*frequency, *beacons))
                .collect();
            most_beacons.sort_by(|a, b| b.1.cmp(&a.1).then(a.0.cmp(&b.0)));
            most_beacons.truncate(MAX_DECLARED_FREQUENCIES);

            (bssid.clone(), most_beacons.into_iter().collect())
        }).collect()
    }

    pub fn has_beaconed_for(&self, bssid: &str, duration: Duration) -> bool {
        self.known_since(bssid).is_some_and(|since| since.elapsed() >= duration)
    }

    pub fn beacon_state_count(&self) -> usize {
        self.beacon_state_count
    }

    pub fn to_report(&self) -> Vec<Dot11ChannelSwitchReport> {
        self.entries.iter().map(|(key, aggregate)| {
            let signal_strength = if aggregate.signal_frames > 0 {
                Some(SignalStrengthReport {
                    min: aggregate.signal_min,
                    max: aggregate.signal_max,
                    average: aggregate.signal_sum as f32 / aggregate.signal_frames as f32
                })
            } else {
                None
            };

            Dot11ChannelSwitchReport {
                bssid: key.bssid.clone(),
                frame_type: key.frame_type.report_name(),
                announcement_type: match key.announcement_type {
                    ChannelSwitchAnnouncementType::Standard => "CSA".to_string(),
                    ChannelSwitchAnnouncementType::Extended => "ECSA".to_string()
                },
                switch_mode: aggregate.switch_mode,
                new_operating_class: key.new_operating_class,
                new_channel: key.new_channel,
                frequency: key.frequency,
                count_min: aggregate.count_min,
                count_max: aggregate.count_max,
                frame_count: aggregate.frame_count,
                peak_frames_per_second: aggregate.peak_frames_per_second,
                timestamp_repetitions: aggregate.timestamp_repetitions,
                timestamp_mismatch_frames: aggregate.timestamp_mismatch_frames,
                interleaved_beacons: aggregate.interleaved_beacons,
                trailing_element_frames: aggregate.trailing_element_frames,
                dsss_only: aggregate.dsss_only,
                country_code: aggregate.country_code.clone(),
                signal_strength,
                signal_histogram: aggregate.signal_histogram.clone()
            }
        }).collect()
    }

    // Beacon states outlive the report because an announcement is judged against beacons seen before it.
    pub fn end_report(&mut self, now: Instant) {
        self.entries.clear();
        self.dropped_frames = 0;
        self.action_frames = 0;
        self.rejected_beacon_states = 0;
        self.dropped_declared_frequencies = 0;
        self.bssid_entries.clear();
        self.declared_frequencies.clear();

        for frequencies in self.beacon_states.values_mut() {
            frequencies.retain(|_, state| now.duration_since(state.last_seen) <= BEACON_STATE_TTL);
        }
        self.beacon_states.retain(|_, frequencies| !frequencies.is_empty());
        self.beacon_state_count = self.beacon_states.values().map(|frequencies| frequencies.len()).sum();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const AP: &str = "02:11:22:33:44:55";
    const BEACON_INTERVAL_MICROS: u64 = 102_400;

    fn announcement(new_channel: u8, switch_count: u8, is_trailing_element: bool) -> ChannelSwitchAnnouncement {
        ChannelSwitchAnnouncement {
            announcement_type: ChannelSwitchAnnouncementType::Standard,
            switch_mode: 1,
            new_operating_class: None,
            new_channel,
            switch_count,
            is_trailing_element,
            dsss_only: Some(false),
            country_code: None,
            current_frequency: None
        }
    }

    fn only_entry(table: &ChannelSwitchTable) -> &ChannelSwitchAggregate {
        assert_eq!(table.entries.len(), 1);
        table.entries.values().next().unwrap()
    }

    struct Timeline {
        start: Instant,
        events: Vec<(u64, u64, Option<ChannelSwitchAnnouncement>)>
    }

    impl Timeline {
        fn new() -> Self {
            Self { start: Instant::now(), events: Vec::new() }
        }

        fn beacon(&mut self, at_micros: u64, timestamp: u64, announcement: Option<ChannelSwitchAnnouncement>) {
            self.events.push((at_micros, timestamp, announcement));
        }

        fn replay(mut self, table: &mut ChannelSwitchTable) {
            self.events.sort_by_key(|event| event.0);
            for (at_micros, timestamp, announcement) in &self.events {
                table.register_beacon(
                    AP, Some(2437), Some(-48), *timestamp, announcement.as_ref(), None,
                    self.start + Duration::from_micros(*at_micros)
                );
            }
        }
    }

    #[test]
    fn genuine_countdown_shows_no_tells() {
        let mut table = ChannelSwitchTable::default();
        let mut timeline = Timeline::new();
        let uptime = 86_400_000_000;

        for i in 0..20u64 {
            let at = i * BEACON_INTERVAL_MICROS;
            let announcement = if i >= 15 { Some(announcement(11, (20 - i) as u8, false)) } else { None };
            timeline.beacon(at, uptime + at, announcement);
        }
        timeline.replay(&mut table);

        let entry = only_entry(&table);
        assert_eq!(entry.frame_count, 5);
        assert_eq!(entry.count_max, 5);
        assert_eq!(entry.count_min, 1);
        assert_eq!(entry.interleaved_beacons, 0);
        assert_eq!(entry.timestamp_repetitions, 1);
        assert_eq!(entry.timestamp_mismatch_frames, 0);
        assert_eq!(entry.trailing_element_frames, 0);
        assert_eq!(entry.peak_frames_per_second, 5);
        assert_eq!(entry.signal_min, -48);
        assert_eq!(entry.signal_max, -48);
    }

    // Bursts of 64 replayed beacons 2 ms apart with 180 ms between bursts, next to the beacons of the real access point.
    fn forged_bursts(timeline: &mut Timeline, uptime: u64, from_micros: u64, until_micros: u64) {
        let captured_timestamp = uptime + from_micros;
        let mut at = from_micros + 180_000;

        while at < until_micros {
            for _ in 0..64 {
                timeline.beacon(at, captured_timestamp, Some(announcement(14, 0, true)));
                at += 2_000;
            }
            at += 180_000;
        }
    }

    #[test]
    fn replayed_bursts_show_every_tell() {
        let mut table = ChannelSwitchTable::default();
        let mut timeline = Timeline::new();
        let uptime = 86_400_000_000;

        for i in 0..100u64 {
            let at = i * BEACON_INTERVAL_MICROS;
            timeline.beacon(at, uptime + at, None);
        }
        forged_bursts(&mut timeline, uptime, 1_000_000, 10_000_000);
        timeline.replay(&mut table);

        let entry = only_entry(&table);
        assert!(entry.frame_count > 1000);
        assert_eq!(entry.count_min, 0);
        assert_eq!(entry.count_max, 0);
        assert_eq!(entry.trailing_element_frames, entry.frame_count);
        assert_eq!(entry.timestamp_repetitions as u64, entry.frame_count);
        assert!(entry.interleaved_beacons >= 50, "interleaved: {}", entry.interleaved_beacons);
        assert!(entry.peak_frames_per_second >= 150, "peak: {}", entry.peak_frames_per_second);
        assert!(entry.timestamp_mismatch_frames > 0);
    }

    #[test]
    fn one_second_on_the_channel_is_enough() {
        let mut table = ChannelSwitchTable::default();
        let mut timeline = Timeline::new();
        let uptime = 86_400_000_000;

        for i in 0..10u64 {
            let at = i * BEACON_INTERVAL_MICROS;
            timeline.beacon(at, uptime + at, None);
        }
        forged_bursts(&mut timeline, uptime, 0, 1_000_000);
        timeline.replay(&mut table);

        let entry = only_entry(&table);
        assert!(entry.interleaved_beacons >= 3, "interleaved: {}", entry.interleaved_beacons);
        assert!(entry.timestamp_repetitions >= 64);
        assert!(entry.peak_frames_per_second >= 100, "peak: {}", entry.peak_frames_per_second);
        assert_eq!(entry.timestamp_mismatch_frames, 0);
    }

    #[test]
    fn timestamps_of_another_clock_are_a_mismatch() {
        let mut table = ChannelSwitchTable::default();
        let mut timeline = Timeline::new();
        let uptime = 86_400_000_000;
        let injector_uptime = 120_000_000;

        for i in 0..10u64 {
            let at = i * BEACON_INTERVAL_MICROS;
            timeline.beacon(at, uptime + at, None);
            timeline.beacon(at + 50_000, injector_uptime + at, Some(announcement(1, 3, false)));
        }
        timeline.replay(&mut table);

        let entry = only_entry(&table);
        assert_eq!(entry.timestamp_mismatch_frames, 10);
        assert_eq!(entry.timestamp_repetitions, 1);
    }

    #[test]
    fn announcement_without_a_recent_plain_beacon_is_no_mismatch() {
        let mut table = ChannelSwitchTable::default();
        let start = Instant::now();

        table.register_beacon(AP, Some(2437), None, 5_000_000, None, None, start);
        table.register_beacon(
            AP, Some(2437), None, 900_000_000, Some(&announcement(11, 3, false)), None,
            start + BEACON_STATE_TTL + Duration::from_secs(1)
        );
        table.register_beacon("02:00:00:00:00:99", Some(2437), None, 1, Some(&announcement(11, 3, false)), None, start);

        assert!(table.entries.values().all(|entry| entry.timestamp_mismatch_frames == 0));
    }

    #[test]
    fn frames_processed_out_of_order_interleave_once() {
        let mut table = ChannelSwitchTable::default();
        let mut timeline = Timeline::new();
        let uptime = 86_400_000_000;

        timeline.beacon(0, uptime, None);
        timeline.beacon(100, uptime + 2 * BEACON_INTERVAL_MICROS, Some(announcement(11, 5, false)));
        timeline.beacon(200, uptime + BEACON_INTERVAL_MICROS, None);
        timeline.beacon(300, uptime + 3 * BEACON_INTERVAL_MICROS, Some(announcement(11, 4, false)));
        timeline.beacon(400, uptime + 4 * BEACON_INTERVAL_MICROS, Some(announcement(11, 3, false)));
        timeline.replay(&mut table);

        let entry = only_entry(&table);
        assert_eq!(entry.interleaved_beacons, 1);
        assert_eq!(entry.timestamp_mismatch_frames, 0);
    }

    #[test]
    fn forged_action_frames_and_probe_responses_interleave_with_real_beacons() {
        for frame_type in [ChannelSwitchFrameType::Action, ChannelSwitchFrameType::ProbeResponse] {
            let mut table = ChannelSwitchTable::default();
            let start = Instant::now();
            let uptime = 86_400_000_000;

            for i in 0..30u64 {
                let at = start + Duration::from_micros(i * BEACON_INTERVAL_MICROS);
                table.register_beacon(AP, Some(2437), None, uptime + i * BEACON_INTERVAL_MICROS, None, None, at);

                if i % 5 == 4 {
                    table.register_frame(
                        AP, frame_type.clone(), Some(2437), None, &announcement(11, 0, false),
                        at + Duration::from_millis(50)
                    );
                }
            }

            let entry = only_entry(&table);
            assert_eq!(entry.frame_count, 6);
            assert_eq!(entry.interleaved_beacons, 25);
            assert_eq!(entry.timestamp_repetitions, 0);
            assert_eq!(entry.timestamp_mismatch_frames, 0);
        }
    }

    #[test]
    fn genuine_switch_with_action_frame_and_probe_responses_does_not_interleave() {
        let mut table = ChannelSwitchTable::default();
        let start = Instant::now();
        let uptime = 86_400_000_000;

        for i in 0..20u64 {
            let at = start + Duration::from_micros(i * BEACON_INTERVAL_MICROS);
            let timestamp = uptime + i * BEACON_INTERVAL_MICROS;

            if i < 10 {
                table.register_beacon(AP, Some(5180), None, timestamp, None, None, at);
                continue;
            }

            if i == 10 {
                table.register_frame(
                    AP, ChannelSwitchFrameType::Action, Some(5180), None, &announcement(100, 10, false),
                    at - Duration::from_millis(20)
                );
            }

            table.register_beacon(AP, Some(5180), None, timestamp, Some(&announcement(100, (20 - i) as u8, false)), None, at);
            table.register_frame(
                AP, ChannelSwitchFrameType::ProbeResponse, Some(5180), None,
                &announcement(100, (20 - i) as u8, false), at + Duration::from_millis(30)
            );
        }

        assert_eq!(table.entries.len(), 3);
        assert!(table.entries.values().all(|entry| entry.interleaved_beacons == 0));
    }

    #[test]
    fn beacon_that_lags_one_interval_behind_an_action_frame_interleaves_once() {
        let mut table = ChannelSwitchTable::default();
        let start = Instant::now();

        table.register_beacon(AP, Some(5180), None, 1_000_000, None, None, start);
        table.register_frame(
            AP, ChannelSwitchFrameType::Action, Some(5180), None, &announcement(100, 10, false),
            start + Duration::from_millis(10)
        );
        table.register_beacon(AP, Some(5180), None, 1_102_400, None, None, start + Duration::from_millis(102));
        table.register_beacon(
            AP, Some(5180), None, 1_204_800, Some(&announcement(100, 9, false)), None, start + Duration::from_millis(204)
        );

        let interleaved: u64 = table.entries.values().map(|entry| entry.interleaved_beacons).sum();
        assert_eq!(interleaved, 1);
    }

    #[test]
    fn switch_on_the_same_channel_does_not_interleave() {
        let mut table = ChannelSwitchTable::default();
        let mut timeline = Timeline::new();
        let uptime = 86_400_000_000;

        for i in 0..30u64 {
            let at = i * BEACON_INTERVAL_MICROS;
            let announcement = if (5..10).contains(&i) { Some(announcement(6, (10 - i) as u8, false)) } else { None };
            timeline.beacon(at, uptime + at, announcement);
        }
        timeline.replay(&mut table);

        assert_eq!(only_entry(&table).interleaved_beacons, 0);
    }

    #[test]
    fn beacon_seen_by_two_adapters_repeats_its_timestamp_once() {
        let mut table = ChannelSwitchTable::default();
        let mut timeline = Timeline::new();
        let uptime = 86_400_000_000;

        for i in 0..5u64 {
            let at = i * BEACON_INTERVAL_MICROS;
            timeline.beacon(at, uptime + at, Some(announcement(11, (5 - i) as u8, false)));
            timeline.beacon(at + 10, uptime + at, Some(announcement(11, (5 - i) as u8, false)));
        }
        timeline.replay(&mut table);

        assert_eq!(only_entry(&table).timestamp_repetitions, 2);
    }

    #[test]
    fn keys_by_frame_type_target_and_frequency() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();

        table.register_beacon(AP, Some(2437), Some(-40), 1, Some(&announcement(11, 3, false)), None, now);
        table.register_beacon(AP, Some(2437), Some(-60), 2, Some(&announcement(11, 2, false)), None, now);
        table.register_beacon(AP, Some(2437), None, 3, Some(&announcement(1, 2, false)), None, now);
        table.register_beacon(AP, Some(5180), None, 4, Some(&announcement(11, 2, false)), None, now);
        table.register_frame(
            AP, ChannelSwitchFrameType::ProbeResponse, Some(2437), None, &announcement(11, 3, false), now
        );
        table.register_frame(AP, ChannelSwitchFrameType::Action, Some(2437), None, &announcement(11, 3, false), now);

        assert_eq!(table.entries.len(), 5);

        let report = table.to_report();
        let beacons = report.iter()
            .find(|r| r.frame_type == "BEACON" && r.new_channel == 11 && r.frequency == Some(2437))
            .unwrap();
        assert_eq!(beacons.bssid, AP);
        assert_eq!(beacons.announcement_type, "CSA");
        assert_eq!(beacons.frame_count, 2);
        assert_eq!(beacons.count_min, 2);
        assert_eq!(beacons.count_max, 3);

        let signal = beacons.signal_strength.as_ref().unwrap();
        assert_eq!((signal.min, signal.max, signal.average), (-60, -40, -50.0));

        let action = report.iter().find(|r| r.frame_type == "ACTION").unwrap();
        assert!(action.signal_strength.is_none());
        assert!(action.signal_histogram.is_empty());
        assert_eq!(action.timestamp_repetitions, 0);
    }

    #[test]
    fn records_the_signal_of_every_frame_by_tuned_frequency() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();

        table.register_beacon(AP, Some(2437), Some(-40), 1, Some(&announcement(11, 3, false)), Some(2437), now);
        table.register_beacon(AP, Some(2437), Some(-40), 2, Some(&announcement(11, 3, false)), Some(2437), now);
        table.register_beacon(AP, Some(2437), Some(-70), 3, Some(&announcement(11, 3, false)), Some(2437), now);
        table.register_beacon(AP, Some(2432), Some(-62), 4, Some(&announcement(11, 3, false)), Some(2437), now);
        table.register_beacon(AP, Some(2432), None, 5, Some(&announcement(11, 3, false)), Some(2437), now);
        table.register_beacon(AP, None, Some(-10), 6, Some(&announcement(11, 3, false)), Some(2437), now);

        let report = table.to_report();
        assert_eq!(report.len(), 1);
        assert_eq!(report[0].frame_count, 6);
        assert_eq!(report[0].signal_histogram.len(), 2);
        assert_eq!(report[0].signal_histogram[&2437], HashMap::from([(-40, 2), (-70, 1)]));
        assert_eq!(report[0].signal_histogram[&2432], HashMap::from([(-62, 1)]));

        let json = serde_json::to_value(&report[0]).unwrap();
        assert_eq!(json["signal_histogram"]["2437"]["-40"], 2);
    }

    #[test]
    fn limits_the_tuned_frequencies_of_an_entry() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();

        for i in 0..(MAX_SIGNAL_FREQUENCIES as u16 + 4) {
            table.register_beacon(
                AP, Some(5180 + i * 20), Some(-50), u64::from(i), Some(&announcement(36, 3, false)), Some(5180), now
            );
        }
        table.register_beacon(AP, Some(5180), Some(-51), 99, Some(&announcement(36, 3, false)), Some(5180), now);

        let report = table.to_report();
        assert_eq!(report.len(), 1);
        assert_eq!(report[0].frame_count, MAX_SIGNAL_FREQUENCIES as u64 + 5);
        assert_eq!(report[0].signal_histogram.len(), MAX_SIGNAL_FREQUENCIES);
        assert_eq!(report[0].signal_histogram[&5180], HashMap::from([(-50, 1), (-51, 1)]));
    }

    #[test]
    fn reports_dsss_only_if_every_frame_was_and_the_first_country_seen() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();
        let frame = |dsss_only: Option<bool>, country: Option<&str>| ChannelSwitchAnnouncement {
            dsss_only,
            country_code: country.map(str::to_string),
            ..announcement(14, 3, false)
        };

        table.register_beacon(AP, Some(2437), None, 1, Some(&frame(None, None)), None, now);
        assert_eq!(table.to_report()[0].dsss_only, None);
        assert_eq!(table.to_report()[0].country_code, None);

        table.register_beacon(AP, Some(2437), None, 2, Some(&frame(Some(true), Some("JP"))), None, now);
        assert_eq!(table.to_report()[0].dsss_only, Some(true));

        table.register_beacon(AP, Some(2437), None, 3, Some(&frame(Some(false), Some("US"))), None, now);
        table.register_beacon(AP, Some(2437), None, 4, Some(&frame(Some(true), None)), None, now);
        assert_eq!(table.to_report()[0].dsss_only, Some(false));
        assert_eq!(table.to_report()[0].country_code, Some("JP".to_string()));
    }

    fn flood(table: &mut ChannelSwitchTable, bssids: usize, now: Instant) {
        for i in 0..bssids {
            let bssid = format!("02:00:00:00:{:02X}:{:02X}", i / 256, i % 256);
            table.register_beacon(&bssid, Some(2437), None, 1, Some(&announcement(14, 0, true)), None, now);
        }
    }

    #[test]
    fn drops_frames_of_new_keys_when_full() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();

        flood(&mut table, MAX_CHANNEL_SWITCH_ENTRIES + 10, now);
        table.register_beacon("02:00:00:00:00:00", Some(2437), None, 2, Some(&announcement(14, 0, true)), None, now);

        assert_eq!(table.entries.len(), MAX_CHANNEL_SWITCH_ENTRIES);
        assert_eq!(table.dropped_frames, 10);
        assert_eq!(table.entries.values().map(|entry| entry.frame_count).sum::<u64>(), MAX_CHANNEL_SWITCH_ENTRIES as u64 + 1);
    }

    fn flood_of_beaconing_bssids(table: &mut ChannelSwitchTable, now: Instant) {
        for i in 0..MAX_CHANNEL_SWITCH_ENTRIES {
            let bssid = format!("02:AA:00:00:{:02X}:{:02X}", i / 256, i % 256);
            table.register_beacon(&bssid, Some(2437), None, 1, None, None, now);
            table.register_beacon(&bssid, Some(2437), None, 2, Some(&announcement(11, 3, false)), None, now);
        }
    }

    #[test]
    fn access_point_known_for_longer_replaces_newer_bssids() {
        let mut table = ChannelSwitchTable::default();
        let start = Instant::now();
        let later = start + Duration::from_secs(60);

        table.register_beacon(AP, Some(2437), None, 1_000_000, None, None, start);
        flood(&mut table, MAX_CHANNEL_SWITCH_ENTRIES / 2, later);
        flood_of_beaconing_bssids(&mut table, later);
        assert_eq!(table.entries.len(), MAX_CHANNEL_SWITCH_ENTRIES);

        table.register_beacon(AP, Some(2437), None, 61_000_000, Some(&announcement(14, 0, true)), None, later);
        table.register_frame(AP, ChannelSwitchFrameType::Action, Some(2437), None, &announcement(14, 0, false), later);

        assert_eq!(table.entries.len(), MAX_CHANNEL_SWITCH_ENTRIES);
        assert_eq!(table.entries.keys().filter(|key| key.bssid == AP).count(), 2);
        assert_eq!(table.entries.keys().filter(|key| key.bssid.starts_with("02:00")).count(), 0);
        assert_eq!(table.dropped_frames, MAX_CHANNEL_SWITCH_ENTRIES as u64 / 2 + 2);
    }

    #[test]
    fn bssid_that_appears_after_the_flood_does_not_replace_it() {
        let mut table = ChannelSwitchTable::default();
        let start = Instant::now();
        let later = start + Duration::from_secs(60);

        flood_of_beaconing_bssids(&mut table, start);
        table.register_beacon(AP, Some(2437), None, 1_000_000, None, None, later);
        table.register_beacon(AP, Some(2437), None, 1_100_000, Some(&announcement(14, 0, true)), None, later);

        assert_eq!(table.entries.keys().filter(|key| key.bssid == AP).count(), 0);
        assert_eq!(table.dropped_frames, 1);
    }

    #[test]
    fn one_bssid_cannot_fill_the_table() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();

        table.register_beacon(AP, Some(2437), None, 1_000_000, None, None, now);
        for _ in 0..5 {
            table.register_frame(AP, ChannelSwitchFrameType::Action, Some(2437), None, &announcement(200, 0, false), now);
        }
        for channel in 0..100 {
            table.register_frame(AP, ChannelSwitchFrameType::Action, Some(2437), None, &announcement(channel, 0, false), now);
        }

        // The announcement with the most frames stays, the ones with a single frame give way to each other.
        assert_eq!(table.entries.len(), MAX_ENTRIES_PER_BSSID);
        assert_eq!(table.dropped_frames, 101 - MAX_ENTRIES_PER_BSSID as u64);
        assert_eq!(table.entries.iter().find(|(key, _)| key.new_channel == 200).unwrap().1.frame_count, 5);

        table.register_frame("02:00:00:00:00:99", ChannelSwitchFrameType::Action, Some(2437), None, &announcement(1, 0, false), now);
        assert_eq!(table.entries.len(), MAX_ENTRIES_PER_BSSID + 1);

        table.end_report(now);
        table.register_frame(AP, ChannelSwitchFrameType::Action, Some(2437), None, &announcement(99, 0, false), now);
        assert_eq!(table.entries.len(), 1);
    }

    #[test]
    fn switch_mode_values_do_not_split_an_announcement() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();

        for mode in 1..=16u8 {
            let frame = ChannelSwitchAnnouncement { switch_mode: mode, ..announcement(11, 3, false) };
            table.register_beacon(AP, Some(2437), None, u64::from(mode), Some(&frame), Some(2437), now);
        }
        let frame = ChannelSwitchAnnouncement { switch_mode: 0, ..announcement(11, 3, false) };
        table.register_beacon(AP, Some(2437), None, 17, Some(&frame), Some(2437), now);

        assert_eq!(table.entries.len(), 2);
        let report = table.to_report();
        let stopping = report.iter().find(|entry| entry.switch_mode != 0).unwrap();
        assert_eq!(stopping.switch_mode, 1);
        assert_eq!(stopping.frame_count, 16);
        assert_eq!(stopping.peak_frames_per_second, 16);
    }

    #[test]
    fn keys_by_the_channel_a_frame_declares() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();

        // Decoded while tuned to channel 11, sent on channel 6.
        table.register_beacon(AP, Some(2462), None, 1, Some(&announcement(1, 3, false)), Some(2437), now);
        table.register_beacon(AP, Some(2437), None, 2, Some(&announcement(1, 3, false)), Some(2437), now);
        table.register_frame(AP, ChannelSwitchFrameType::Action, Some(2462), None, &announcement(1, 3, false), now);
        table.register_frame("02:00:00:00:00:99", ChannelSwitchFrameType::Action, Some(2462), None, &announcement(1, 3, false), now);

        let mut keys: Vec<(String, Option<u16>)> = table.entries.keys()
            .map(|key| (key.bssid.clone(), key.frequency))
            .collect();
        keys.sort();
        keys.dedup();
        assert_eq!(keys, vec![("02:00:00:00:00:99".to_string(), Some(2462)), (AP.to_string(), Some(2437))]);
        assert_eq!(table.entries.values().map(|entry| entry.frame_count).sum::<u64>(), 4);
    }

    #[test]
    fn reports_the_channels_a_bssid_declares() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();

        // The access point is also decoded off channel, and a clone says it is somewhere else.
        table.register_beacon(AP, Some(2437), None, 1, None, Some(2437), now);
        table.register_beacon(AP, Some(2462), None, 2, None, Some(2437), now);
        table.register_beacon(AP, Some(2462), None, 3, Some(&announcement(1, 3, false)), Some(2462), now);
        table.register_beacon(AP, Some(2462), None, 4, None, None, now);
        table.register_beacon("02:00:00:00:00:99", Some(2462), None, 5, None, Some(2462), now);

        let declared = table.declared_frequencies();
        assert_eq!(declared.len(), 2);
        // A beacon that names no channel was sent where the tap was tuned, as far as the tap can tell.
        assert_eq!(declared[AP], HashMap::from([(2437, 2), (2462, 2)]));
        assert_eq!(declared["02:00:00:00:00:99"], HashMap::from([(2462, 1)]));

        table.end_report(now);
        assert!(table.declared_frequencies().is_empty());
    }

    #[test]
    fn a_channel_of_another_band_is_not_believed() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();

        table.register_beacon(AP, Some(5180), None, 1, Some(&announcement(40, 3, false)), Some(2437), now);
        table.register_beacon(AP, Some(2437), None, 2, None, Some(6135), now);
        table.register_beacon(AP, None, None, 3, None, Some(2462), now);

        assert_eq!(table.entries.keys().next().unwrap().frequency, Some(5180));
        assert_eq!(table.declared_frequencies()[AP], HashMap::from([(5180, 1), (2437, 1), (2462, 1)]));
    }

    #[test]
    fn reports_the_declared_channels_with_the_most_beacons() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();

        // Made up channels first, which must not keep the channel of the access point out of the report.
        for channel in [1u16, 2, 3, 4, 5, 7, 8, 9, 10] {
            table.register_beacon(AP, Some(2437), None, 1, None, Some(2407 + channel * 5), now);
        }
        for _ in 0..10 {
            table.register_beacon(AP, Some(2437), None, 2, None, Some(2437), now);
        }

        let declared = table.declared_frequencies();
        assert_eq!(declared[AP].len(), MAX_DECLARED_FREQUENCIES);
        assert_eq!(declared[AP][&2437], 10);
        assert_eq!(table.dropped_declared_frequencies, 0);

        for frequency in 5000..5000 + MAX_COUNTED_DECLARED_FREQUENCIES as u16 {
            table.register_beacon(AP, Some(5180), None, 3, None, Some(frequency), now);
        }
        assert_eq!(table.dropped_declared_frequencies, 10);
        assert_eq!(table.declared_frequencies()[AP][&2437], 10);
    }

    #[test]
    fn knows_a_bssid_by_its_plain_beacons_across_reports() {
        let mut table = ChannelSwitchTable::default();
        let now = Instant::now();

        table.register_frame(
            "02:00:00:00:00:01", ChannelSwitchFrameType::Action, Some(2437), None, &announcement(11, 3, false), now
        );
        table.register_beacon(AP, Some(2437), None, 1, None, None, now);
        table.end_report(now + Duration::from_secs(10));

        assert!(table.has_beaconed_for(AP, Duration::ZERO));
        assert!(!table.has_beaconed_for("02:00:00:00:00:01", Duration::ZERO));
        assert!(!table.has_beaconed_for("02:00:00:00:00:02", Duration::ZERO));

        table.end_report(now + BEACON_STATE_TTL + Duration::from_secs(1));
        assert!(!table.has_beaconed_for(AP, Duration::ZERO));
    }

    #[test]
    fn beacon_states_are_capped_and_expire() {
        let mut table = ChannelSwitchTable::default();
        let start = Instant::now();

        for i in 0..MAX_BEACON_STATES + 50 {
            let bssid = format!("02:00:00:{:02X}:{:02X}:{:02X}", i / 65536, (i / 256) % 256, i % 256);
            table.register_beacon(&bssid, Some(2437), None, 1, None, None, start);
        }
        assert_eq!(table.beacon_state_count(), MAX_BEACON_STATES);
        assert_eq!(table.rejected_beacon_states, 50);
        assert!(table.entries.is_empty());

        table.register_beacon(AP, Some(5180), None, 1, None, None, start + Duration::from_secs(100));
        table.end_report(start + Duration::from_secs(100));
        assert_eq!(table.beacon_state_count(), MAX_BEACON_STATES);

        table.end_report(start + BEACON_STATE_TTL + Duration::from_secs(1));
        assert_eq!(table.beacon_state_count(), 0);

        table.register_beacon(AP, Some(5180), None, 1, None, None, start + BEACON_STATE_TTL + Duration::from_secs(2));
        assert_eq!(table.beacon_state_count(), 1);
    }

    #[test]
    fn report_window_ends_without_losing_the_beacon_clock() {
        let mut table = ChannelSwitchTable::default();
        let start = Instant::now();

        table.register_beacon(AP, Some(2437), None, 1_000_000, None, None, start);
        table.register_beacon(AP, Some(2437), None, 1_100_000, Some(&announcement(11, 3, false)), None, start);
        table.action_frames = 7;
        table.end_report(start + Duration::from_secs(1));
        assert!(table.entries.is_empty());
        assert_eq!(table.dropped_frames, 0);
        assert_eq!(table.action_frames, 0);

        table.register_beacon(
            AP, Some(2437), None, 500, Some(&announcement(11, 3, false)), None, start + Duration::from_secs(10)
        );
        assert_eq!(only_entry(&table).timestamp_mismatch_frames, 1);
    }
}
