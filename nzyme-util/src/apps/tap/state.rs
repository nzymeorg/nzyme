//! Everything the dashboard shows, derived from the stream of frames. Ticks carry totals, so rates are
//! computed here from consecutive ticks. Activity and error flashes are driven by the wall-clock moment a
//! frame was applied, so they fade the same way live and during a replay.

use std::collections::{BTreeMap, VecDeque};
use std::time::Instant;

use crate::apps::tap::protocol::{Frame, Hello, Sys};

/// Ticks kept for sparklines. At the default tick of 100 ms this is 60 seconds.
pub const HISTORY: usize = 600;
/// Seconds without a message after which a channel counts as idle.
pub const IDLE_SECONDS: u64 = 10;
const LOG_LINES: usize = 2000;
const ENGAGEMENT_DEDUP: usize = 64;

/// Fixed-size ring of samples for sparklines.
pub struct History {
    values: VecDeque<f64>,
    capacity: usize,
}

impl History {
    pub fn new(capacity: usize) -> Self {
        History { values: VecDeque::with_capacity(capacity), capacity }
    }

    pub fn push(&mut self, value: f64) {
        if self.values.len() == self.capacity {
            self.values.pop_front();
        }
        self.values.push_back(value);
    }

    /// The newest `n` samples, oldest first.
    pub fn last(&self, n: usize) -> Vec<f64> {
        let skip = self.values.len().saturating_sub(n);
        self.values.iter().skip(skip).copied().collect()
    }

    #[cfg(test)]
    pub fn latest(&self) -> f64 {
        self.values.back().copied().unwrap_or(0.0)
    }

    pub fn max(&self) -> f64 {
        self.values.iter().copied().fold(0.0, f64::max)
    }

    /// Mean of the newest `n` samples.
    pub fn mean_last(&self, n: usize) -> f64 {
        let last = self.last(n);
        if last.is_empty() { 0.0 } else { last.iter().sum::<f64>() / last.len() as f64 }
    }

}

pub struct ChannelState {
    pub name: String,
    pub bus: String,
    pub capacity: u64,
    pub watermark: u64,
    pub peak_watermark: u64,
    pub messages: u64,
    pub bytes: u64,
    pub errors: u64,
    /// Messages per second, one sample per tick.
    pub msg_rate: History,
    /// Bytes per second, one sample per tick.
    pub byte_rate: History,
    /// Watermark as a fraction of capacity, one sample per tick.
    pub fill: History,
    pub last_activity: Option<Instant>,
    pub last_error: Option<Instant>,
    /// Errors in the newest tick.
    pub new_errors: u64,
    /// Set once the channel appeared in a tick.
    pub seen: bool,
}

impl ChannelState {
    fn new(name: String, bus: String, capacity: u64) -> Self {
        ChannelState {
            name, bus, capacity,
            watermark: 0, peak_watermark: 0, messages: 0, bytes: 0, errors: 0,
            msg_rate: History::new(HISTORY), byte_rate: History::new(HISTORY), fill: History::new(HISTORY),
            last_activity: None, last_error: None, new_errors: 0, seen: false,
        }
    }

    pub fn fill_fraction(&self) -> f64 {
        if self.capacity == 0 { 0.0 } else { self.watermark as f64 / self.capacity as f64 }
    }

    pub fn is_idle(&self, now: Instant) -> bool {
        self.watermark == 0 && self.last_activity.map_or(true, |t| now.duration_since(t).as_secs() >= IDLE_SECONDS)
    }
}

pub struct CaptureState {
    pub name: String,
    pub kind: String,
    pub running: bool,
    pub received: u64,
    pub dropped_buffer: u64,
    pub dropped_interface: u64,
    /// Received per second, one sample per tick.
    pub rate: History,
    pub last_activity: Option<Instant>,
    pub last_drop: Option<Instant>,
    pub new_drops: u64,
    pub seen: bool,
}

impl CaptureState {
    fn new(name: String, kind: String) -> Self {
        CaptureState {
            name, kind, running: false, received: 0, dropped_buffer: 0, dropped_interface: 0,
            rate: History::new(HISTORY), last_activity: None, last_drop: None, new_drops: 0, seen: false,
        }
    }
}

pub struct WifiState {
    pub name: String,
    pub hopper: bool,
    pub channels_2g: Vec<u16>,
    pub channels_5g: Vec<u16>,
    pub channels_6g: Vec<u16>,
    /// Frequency in MHz and channel width label of the last hop.
    pub current: Option<(u32, String)>,
    pub hops: u64,
    pub last_hop: Option<Instant>,
    /// Timestamps of recent hops, for the hop rate.
    hop_times: VecDeque<i64>,
}

impl WifiState {
    fn new(name: String) -> Self {
        WifiState {
            name, hopper: true, channels_2g: Vec::new(), channels_5g: Vec::new(), channels_6g: Vec::new(),
            current: None, hops: 0, last_hop: None, hop_times: VecDeque::new(),
        }
    }

    /// Hops per second over the last five seconds of stream time.
    pub fn hop_rate(&self, now_t: i64) -> f64 {
        let window_ms = 5000;
        let count = self.hop_times.iter().filter(|t| now_t - **t <= window_ms).count();
        count as f64 / (window_ms as f64 / 1000.0)
    }
}

#[derive(Clone)]
pub struct LogEntry {
    pub t: i64,
    pub level: String,
    pub message: String,
    pub at: Instant,
}

pub struct GaugeState {
    pub value: f64,
    pub is_float: bool,
    pub history: History,
    pub updated: Instant,
}

/// One submission path to the leader, for example `tcp/sessions` or `status`.
pub struct LinkPathState {
    pub ok: u64,
    pub failed: u64,
    pub last_ok: bool,
    pub last_status: Option<u16>,
    pub last_rtt_ms: u64,
    pub max_rtt_ms: u64,
    pub last_bytes: u64,
    pub total_bytes: u64,
    pub rtt_history: History,
    pub last_error: Option<String>,
    pub last_at: Option<Instant>,
    pub last_failure_at: Option<Instant>,
}

pub struct CycleTableState {
    pub last_ms: u64,
    pub max_ms: u64,
    pub history: History,
}

/// The periodic table report cycle.
pub struct CycleState {
    pub count: u64,
    pub last_total_ms: u64,
    pub max_total_ms: u64,
    pub history: History,
    pub tables: BTreeMap<String, CycleTableState>,
    pub last_at: Option<Instant>,
    /// Stream time of the last cycle, to show the time since.
    pub last_t: i64,
}

impl CycleState {
    fn new() -> Self {
        CycleState {
            count: 0, last_total_ms: 0, max_total_ms: 0, history: History::new(120),
            tables: BTreeMap::new(), last_at: None, last_t: 0,
        }
    }
}

pub struct TimerState {
    pub mean: f64,
    pub p99: f64,
    pub mean_history: History,
    pub updated: Instant,
}

pub struct TopState {
    pub hello: Option<Hello>,
    pub channels: Vec<ChannelState>,
    pub captures: Vec<CaptureState>,
    pub wifi: Vec<WifiState>,

    pub bytes_total: u64,
    /// Processed bytes per second, one sample per tick.
    pub byte_rate: History,
    /// Messages per second across all channels, one sample per tick.
    pub msg_rate: History,
    pub peak_byte_rate: f64,

    pub sys: Option<Sys>,
    pub cpu_history: History,
    pub proc_cpu_history: History,

    pub gauges: BTreeMap<String, GaugeState>,
    pub timers: BTreeMap<String, TimerState>,
    pub links: BTreeMap<String, LinkPathState>,
    pub cycle: CycleState,

    pub logs: VecDeque<LogEntry>,
    /// Error, warn, info, debug, trace.
    pub log_counts: [u64; 5],
    engagement_seen: VecDeque<String>,

    /// Stream time of the newest frame, unix milliseconds.
    pub now_t: i64,
    pub last_tick_t: Option<i64>,
    pub last_tick_at: Option<Instant>,
    pub ticks: u64,
    pub lag_ms: u64,
    pub max_lag_ms: u64,
    pub lock_us: u64,
    pub max_lock_us: u64,
    pub lock_history: History,
    pub frames: u64,
}

impl TopState {
    pub fn new() -> Self {
        TopState {
            hello: None, channels: Vec::new(), captures: Vec::new(), wifi: Vec::new(),
            bytes_total: 0, byte_rate: History::new(HISTORY), msg_rate: History::new(HISTORY), peak_byte_rate: 0.0,
            sys: None, cpu_history: History::new(HISTORY), proc_cpu_history: History::new(HISTORY),
            gauges: BTreeMap::new(), timers: BTreeMap::new(), links: BTreeMap::new(), cycle: CycleState::new(),
            logs: VecDeque::with_capacity(LOG_LINES), log_counts: [0; 5], engagement_seen: VecDeque::new(),
            now_t: 0, last_tick_t: None, last_tick_at: None, ticks: 0,
            lag_ms: 0, max_lag_ms: 0, lock_us: 0, max_lock_us: 0, lock_history: History::new(HISTORY), frames: 0,
        }
    }

    pub fn tick_ms(&self) -> u64 {
        self.hello.as_ref().map(|h| h.tick_ms).filter(|t| *t > 0).unwrap_or(100)
    }

    /// Milliseconds the tap process has been running, in stream time.
    pub fn uptime_ms(&self) -> Option<i64> {
        self.hello.as_ref().filter(|h| h.started_at > 0).map(|h| (self.now_t - h.started_at).max(0))
    }

    /// Seconds since the newest tick arrived, in wall time.
    pub fn tick_age(&self, now: Instant) -> Option<f64> {
        self.last_tick_at.map(|t| now.duration_since(t).as_secs_f64())
    }

    pub fn apply(&mut self, frame: Frame, now: Instant) {
        self.frames += 1;
        if let Some(t) = frame.timestamp() {
            self.now_t = self.now_t.max(t);
        }

        match frame {
            Frame::Hello(hello) => self.apply_hello(hello, now),
            Frame::Tick(tick) => self.apply_tick(tick, now),
            Frame::Sys(sys) => {
                self.cpu_history.push(sys.cpu as f64);
                self.proc_cpu_history.push(sys.proc_cpu.unwrap_or(0.0) as f64);
                self.sys = Some(sys);
            }
            Frame::Stats(stats) => {
                for (name, value) in stats.gauges {
                    self.update_gauge(name, value as f64, false, now);
                }
                for (name, value) in stats.gauges_f {
                    self.update_gauge(name, value as f64, true, now);
                }
                if let Some(timers) = stats.timers {
                    for (name, timer) in timers {
                        let entry = self.timers.entry(name).or_insert_with(|| TimerState {
                            mean: 0.0, p99: 0.0, mean_history: History::new(120), updated: now,
                        });
                        entry.mean = timer.mean;
                        entry.p99 = timer.p99;
                        entry.mean_history.push(timer.mean);
                        entry.updated = now;
                    }
                }
                for line in stats.engagement {
                    if self.engagement_seen.contains(&line) {
                        continue;
                    }
                    if self.engagement_seen.len() == ENGAGEMENT_DEDUP {
                        self.engagement_seen.pop_front();
                    }
                    self.engagement_seen.push_back(line.clone());
                    self.push_log(stats.t, "INFO".to_string(), format!("[engagement] {}", line), now);
                }
            }
            Frame::Log(line) => self.push_log(line.t, line.level, line.msg, now),
            Frame::Hop(hop) => {
                let wifi = match self.wifi.iter_mut().find(|w| w.name == hop.dev) {
                    Some(wifi) => wifi,
                    None => {
                        self.wifi.push(WifiState::new(hop.dev.clone()));
                        self.wifi.last_mut().expect("just pushed")
                    }
                };
                wifi.current = Some((hop.freq, hop.width));
                wifi.hops += 1;
                wifi.last_hop = Some(now);
                if wifi.hop_times.len() == 64 {
                    wifi.hop_times.pop_front();
                }
                wifi.hop_times.push_back(hop.t);
            }
            Frame::Report(report) => {
                let link = self.links.entry(report.path).or_insert_with(|| LinkPathState {
                    ok: 0, failed: 0, last_ok: true, last_status: None, last_rtt_ms: 0, max_rtt_ms: 0,
                    last_bytes: 0, total_bytes: 0, rtt_history: History::new(120), last_error: None,
                    last_at: None, last_failure_at: None,
                });
                if report.ok { link.ok += 1 } else { link.failed += 1 }
                link.last_ok = report.ok;
                link.last_status = report.status;
                link.last_rtt_ms = report.rtt_ms;
                link.max_rtt_ms = link.max_rtt_ms.max(report.rtt_ms);
                link.last_bytes = report.bytes;
                link.total_bytes += report.bytes;
                link.rtt_history.push(report.rtt_ms as f64);
                link.last_at = Some(now);
                if !report.ok {
                    link.last_failure_at = Some(now);
                    link.last_error = report.error.or_else(|| report.status.map(|s| format!("HTTP {}", s)));
                }
            }
            Frame::Cycle(cycle) => {
                self.cycle.count += 1;
                self.cycle.last_total_ms = cycle.total_ms;
                self.cycle.max_total_ms = self.cycle.max_total_ms.max(cycle.total_ms);
                self.cycle.history.push(cycle.total_ms as f64);
                self.cycle.last_at = Some(now);
                self.cycle.last_t = cycle.t;
                for table in cycle.tables {
                    let entry = self.cycle.tables.entry(table.name).or_insert_with(|| CycleTableState {
                        last_ms: 0, max_ms: 0, history: History::new(120),
                    });
                    entry.last_ms = table.ms;
                    entry.max_ms = entry.max_ms.max(table.ms);
                    entry.history.push(table.ms as f64);
                }
            }
            Frame::Unknown => {}
        }
    }

    /// Sum of the latest round-trip times of all paths, to compare with the cycle time.
    pub fn link_failures(&self) -> u64 {
        self.links.values().map(|l| l.failed).sum()
    }

    /// The most recent submission across all paths, for the leader LED.
    pub fn last_submission(&self) -> Option<Instant> {
        self.links.values().filter_map(|l| l.last_at).max()
    }

    pub fn last_submission_failure(&self) -> Option<Instant> {
        self.links.values().filter_map(|l| l.last_failure_at).max()
    }

    /// A hello means a new connection or a restarted tap, so everything counted so far starts over.
    fn apply_hello(&mut self, hello: Hello, now: Instant) {
        let logs = std::mem::take(&mut self.logs);
        let log_counts = self.log_counts;
        let now_t = self.now_t;

        *self = TopState::new();
        self.now_t = now_t;

        // Keep what was logged before a reconnect, the backlog fills the gap.
        self.logs = logs;
        self.log_counts = log_counts;

        for c in &hello.channels {
            self.channels.push(ChannelState::new(c.name.clone(), c.bus.clone(), c.capacity));
        }
        for i in &hello.interfaces {
            self.captures.push(CaptureState::new(i.name.clone(), i.interface_type.clone()));
        }
        for w in &hello.wifi {
            let mut wifi = WifiState::new(w.name.clone());
            wifi.hopper = w.hopper;
            wifi.channels_2g = w.channels_2g.clone();
            wifi.channels_5g = w.channels_5g.clone();
            wifi.channels_6g = w.channels_6g.clone();
            self.wifi.push(wifi);
        }

        let backlog_is_new = self.logs.is_empty();
        for line in &hello.backlog {
            if backlog_is_new || self.logs.back().map_or(true, |last| line.t > last.t) {
                self.push_log(line.t, line.level.clone(), line.msg.clone(), now - std::time::Duration::from_secs(60));
            }
        }

        self.hello = Some(hello);
    }

    fn apply_tick(&mut self, tick: crate::apps::tap::protocol::Tick, now: Instant) {
        let tick_ms = self.tick_ms();
        let dt_ms = match self.last_tick_t {
            Some(last) if tick.t > last => (tick.t - last) as f64,
            _ => tick_ms as f64,
        };
        let per_second = 1000.0 / dt_ms;
        let first = self.ticks == 0;

        let mut total_messages_delta: u64 = 0;

        for c in tick.ch {
            let index = match self.channels.iter().position(|x| x.name == c.n) {
                Some(index) => index,
                None => {
                    self.channels.push(ChannelState::new(c.n.clone(), "unknown".to_string(), c.c));
                    self.channels.len() - 1
                }
            };
            let channel = &mut self.channels[index];

            let delta_m = if channel.seen { c.m.saturating_sub(channel.messages) } else { 0 };
            let delta_b = if channel.seen { c.b.saturating_sub(channel.bytes) } else { 0 };
            let delta_e = if channel.seen { c.e.saturating_sub(channel.errors) } else { 0 };

            channel.messages = c.m;
            channel.bytes = c.b;
            channel.errors = c.e;
            channel.watermark = c.w;
            channel.peak_watermark = channel.peak_watermark.max(c.w);
            if c.c > 0 {
                channel.capacity = c.c;
            }
            channel.msg_rate.push(delta_m as f64 * per_second);
            channel.byte_rate.push(delta_b as f64 * per_second);
            channel.fill.push(channel.fill_fraction());
            channel.new_errors = delta_e;
            if delta_m > 0 {
                channel.last_activity = Some(now);
            }
            if delta_e > 0 {
                channel.last_error = Some(now);
            }
            channel.seen = true;
            total_messages_delta += delta_m;
        }

        for c in tick.cap {
            let index = match self.captures.iter().position(|x| x.name == c.n) {
                Some(index) => index,
                None => {
                    self.captures.push(CaptureState::new(c.n.clone(), c.capture_type.clone()));
                    self.captures.len() - 1
                }
            };
            let capture = &mut self.captures[index];

            let delta_rx = if capture.seen { c.rx.saturating_sub(capture.received) } else { 0 };
            let delta_drop = if capture.seen {
                c.db.saturating_sub(capture.dropped_buffer) + c.di.saturating_sub(capture.dropped_interface)
            } else { 0 };

            capture.kind = c.capture_type;
            capture.running = c.run;
            capture.received = c.rx;
            capture.dropped_buffer = c.db;
            capture.dropped_interface = c.di;
            capture.rate.push(delta_rx as f64 * per_second);
            capture.new_drops = delta_drop;
            if delta_rx > 0 {
                capture.last_activity = Some(now);
            }
            if delta_drop > 0 {
                capture.last_drop = Some(now);
            }
            capture.seen = true;
        }

        let delta_bytes = if first { 0 } else { tick.bytes.saturating_sub(self.bytes_total) };
        self.bytes_total = tick.bytes;
        let byte_rate = delta_bytes as f64 * per_second;
        self.byte_rate.push(byte_rate);
        self.peak_byte_rate = self.peak_byte_rate.max(byte_rate);
        self.msg_rate.push(total_messages_delta as f64 * per_second);

        self.lag_ms = tick.lag_ms;
        self.max_lag_ms = self.max_lag_ms.max(tick.lag_ms);
        self.lock_us = tick.lock_us;
        self.max_lock_us = self.max_lock_us.max(tick.lock_us);
        self.lock_history.push(tick.lock_us as f64);

        self.last_tick_t = Some(tick.t);
        self.last_tick_at = Some(now);
        self.ticks += 1;
    }

    fn update_gauge(&mut self, name: String, value: f64, is_float: bool, now: Instant) {
        let entry = self.gauges.entry(name).or_insert_with(|| GaugeState {
            value, is_float, history: History::new(120), updated: now,
        });
        entry.value = value;
        entry.is_float = is_float;
        entry.history.push(value);
        entry.updated = now;
    }

    fn push_log(&mut self, t: i64, level: String, message: String, at: Instant) {
        match level.as_str() {
            "ERROR" => self.log_counts[0] += 1,
            "WARN" => self.log_counts[1] += 1,
            "INFO" => self.log_counts[2] += 1,
            "DEBUG" => self.log_counts[3] += 1,
            _ => self.log_counts[4] += 1,
        }
        if self.logs.len() == LOG_LINES {
            self.logs.pop_front();
        }
        self.logs.push_back(LogEntry { t, level, message, at });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::apps::tap::protocol::{Hello, HelloChannel, Tick, TickChannel};

    fn hello() -> Frame {
        Frame::Hello(Hello {
            tick_ms: 100,
            channels: vec![HelloChannel { name: "TcpPipeline".into(), bus: "ethernet".into(), capacity: 100 }],
            ..Hello::default()
        })
    }

    fn tick(t: i64, messages: u64, errors: u64) -> Frame {
        Frame::Tick(Tick {
            t, bytes: messages * 10,
            ch: vec![TickChannel { n: "TcpPipeline".into(), w: 5, c: 100, m: messages, b: messages * 10, e: errors }],
            ..Tick::default()
        })
    }

    #[test]
    fn derives_rates_from_consecutive_ticks() {
        let now = Instant::now();
        let mut state = TopState::new();
        state.apply(hello(), now);
        state.apply(tick(1000, 10, 0), now);
        state.apply(tick(1100, 60, 0), now);

        let channel = &state.channels[0];
        assert_eq!(channel.messages, 60);
        assert!((channel.msg_rate.latest() - 500.0).abs() < 0.01);
        assert!(channel.last_activity.is_some());
        assert!(channel.last_error.is_none());
        assert!((state.byte_rate.latest() - 5000.0).abs() < 0.01);
        assert_eq!(state.ticks, 2);
    }

    #[test]
    fn flags_new_errors() {
        let now = Instant::now();
        let mut state = TopState::new();
        state.apply(hello(), now);
        state.apply(tick(1000, 10, 0), now);
        state.apply(tick(1100, 10, 3), now);

        let channel = &state.channels[0];
        assert_eq!(channel.new_errors, 3);
        assert!(channel.last_error.is_some());
        assert!((channel.msg_rate.latest()).abs() < 0.01);
    }

    #[test]
    fn tracks_leader_submissions_and_cycles() {
        use crate::apps::tap::protocol::{Cycle, CycleTable, Report};
        let now = Instant::now();
        let mut state = TopState::new();
        state.apply(hello(), now);
        state.apply(Frame::Report(Report { t: 1, path: "tcp/sessions".into(), ok: true, status: Some(200), rtt_ms: 30, bytes: 500, error: None }), now);
        state.apply(Frame::Report(Report { t: 2, path: "tcp/sessions".into(), ok: false, status: None, rtt_ms: 2000, bytes: 500, error: Some("timeout".into()) }), now);
        state.apply(Frame::Cycle(Cycle { t: 3, total_ms: 120, tables: vec![CycleTable { name: "tcp".into(), ms: 100 }] }), now);

        let link = &state.links["tcp/sessions"];
        assert_eq!((link.ok, link.failed), (1, 1));
        assert_eq!(link.max_rtt_ms, 2000);
        assert_eq!(link.last_error.as_deref(), Some("timeout"));
        assert_eq!(state.link_failures(), 1);
        assert_eq!(state.cycle.count, 1);
        assert_eq!(state.cycle.tables["tcp"].last_ms, 100);
    }

    #[test]
    fn hello_resets_counters_but_keeps_logs() {
        let now = Instant::now();
        let mut state = TopState::new();
        state.apply(hello(), now);
        state.apply(tick(1000, 10, 0), now);
        state.apply(Frame::Log(crate::apps::tap::protocol::LogLine { t: 1, level: "WARN".into(), msg: "x".into() }), now);
        state.apply(hello(), now);

        assert_eq!(state.ticks, 0);
        assert_eq!(state.channels[0].messages, 0);
        assert_eq!(state.logs.len(), 1);
        assert_eq!(state.log_counts[1], 1);
    }
}
