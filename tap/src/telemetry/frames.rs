//! Wire format of the telemetry feed. Short field names keep the fast tick frame small, because it is sent
//! ten times a second and recorded verbatim. `nzyme-util` has a mirror of these structs.

use std::collections::HashMap;

use serde::Serialize;

pub const PROTOCOL_VERSION: u32 = 1;

#[derive(Serialize)]
#[serde(tag = "f")]
pub enum Frame {
    #[serde(rename = "hello")]
    Hello(Hello),
    #[serde(rename = "tick")]
    Tick(Tick),
    #[serde(rename = "sys")]
    Sys(Sys),
    #[serde(rename = "stats")]
    Stats(Stats),
    #[serde(rename = "log")]
    Log(LogLine),
    #[serde(rename = "hop")]
    Hop(Hop),
    #[serde(rename = "report")]
    Report(Report),
    #[serde(rename = "cycle")]
    Cycle(Cycle),
}

/// First frame on every connection. Describes the tap and everything the client needs to label the stream.
#[derive(Serialize)]
pub struct Hello {
    /// Unix milliseconds.
    pub t: i64,
    pub protocol: u32,
    pub version: String,
    pub started_at: i64,
    pub hostname: String,
    pub pid: u32,
    pub tick_ms: u64,
    pub cores: usize,
    pub rpi: Option<String>,
    pub channels: Vec<HelloChannel>,
    pub wifi: Vec<HelloWifiInterface>,
    pub interfaces: Vec<HelloInterface>,
    /// Recent log lines so a freshly connected client does not start with an empty log.
    pub backlog: Vec<LogLine>,
}

#[derive(Serialize)]
pub struct HelloChannel {
    pub name: String,
    pub bus: String,
    pub capacity: u64,
}

#[derive(Serialize)]
pub struct HelloWifiInterface {
    pub name: String,
    pub hopper: bool,
    pub channels_2g: Vec<u16>,
    pub channels_5g: Vec<u16>,
    pub channels_6g: Vec<u16>,
}

#[derive(Serialize)]
pub struct HelloInterface {
    pub name: String,
    #[serde(rename = "type")]
    pub interface_type: String,
}

/// Fast frame. Totals only; the client derives rates from consecutive ticks, which keeps the stream
/// self-contained even when frames are lost.
#[derive(Serialize)]
pub struct Tick {
    pub t: i64,
    /// Milliseconds the tick fired late, usually because the producer thread was not scheduled in time.
    pub lag_ms: u64,
    /// Microseconds the producer waited for the metrics mutex. A direct view of lock contention in the tap.
    pub lock_us: u64,
    /// Total processed bytes.
    pub bytes: u64,
    pub ch: Vec<TickChannel>,
    pub cap: Vec<TickCapture>,
}

#[derive(Serialize)]
pub struct TickChannel {
    pub n: &'static str,
    /// Current watermark (queued messages).
    pub w: u64,
    /// Capacity, as last seen by the sender. Zero until the first message was sent.
    pub c: u64,
    /// Messages total.
    pub m: u64,
    /// Bytes total.
    pub b: u64,
    /// Submit errors total (messages dropped because the channel was full).
    pub e: u64,
}

#[derive(Serialize)]
pub struct TickCapture {
    pub n: String,
    #[serde(rename = "type")]
    pub capture_type: String,
    pub run: bool,
    /// Received packets or frames total.
    pub rx: u64,
    /// Dropped by buffer, total.
    pub db: u64,
    /// Dropped by interface, total.
    pub di: u64,
}

/// System and process metrics, once a second.
#[derive(Serialize)]
pub struct Sys {
    pub t: i64,
    /// Aggregate CPU load in percent.
    pub cpu: f32,
    /// Per core CPU load in percent.
    pub cores: Vec<f32>,
    pub mem_total: u64,
    pub mem_free: u64,
    pub mem_available: Option<u64>,
    pub load: Option<[f32; 3]>,
    pub temp: Option<f32>,
    pub proc_cpu: Option<f32>,
    pub proc_rss: Option<u64>,
    pub proc_threads: Option<u32>,
    pub proc_fds: Option<u32>,
}

/// Gauges and timers, once a second. Timers are expensive to compute and are only included every few seconds.
#[derive(Serialize)]
pub struct Stats {
    pub t: i64,
    pub gauges: HashMap<String, i64>,
    pub gauges_f: HashMap<String, f32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub timers: Option<HashMap<String, StatsTimer>>,
    pub engagement: Vec<String>,
}

#[derive(Serialize)]
pub struct StatsTimer {
    pub mean: f64,
    pub p99: f64,
}

#[derive(Serialize, Clone)]
pub struct LogLine {
    pub t: i64,
    pub level: String,
    pub msg: String,
}

#[derive(Serialize)]
pub struct Hop {
    pub t: i64,
    pub dev: String,
    pub freq: u32,
    pub width: &'static str,
}

/// A submission to the leader. `status` is the HTTP status if a response arrived; `ok` is false for transport
/// errors and non-success statuses other than 403, which means the subsystem is disabled on the leader.
#[derive(Serialize)]
pub struct Report {
    pub t: i64,
    pub path: String,
    pub ok: bool,
    pub status: Option<u16>,
    pub rtt_ms: u64,
    pub bytes: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub error: Option<String>,
}

/// One pass of the table report cycle. Each table's time includes building and uploading its report while
/// holding the table lock, which is what the processors wait on.
#[derive(Serialize)]
pub struct Cycle {
    pub t: i64,
    pub total_ms: u64,
    pub tables: Vec<CycleTable>,
}

#[derive(Serialize)]
pub struct CycleTable {
    pub name: String,
    pub ms: u64,
}
