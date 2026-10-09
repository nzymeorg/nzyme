//! Mirror of the tap's telemetry wire format (`tap/src/telemetry/frames.rs`). Every struct tolerates missing
//! fields so a newer tap or an older recording still loads.

use std::collections::HashMap;

use serde::Deserialize;

pub const SUPPORTED_PROTOCOL: u32 = 1;

#[derive(Deserialize, Clone, Debug)]
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
    #[serde(other)]
    Unknown,
}

impl Frame {
    /// Unix milliseconds the frame was produced at, used to pace replays.
    pub fn timestamp(&self) -> Option<i64> {
        match self {
            Frame::Hello(f) => Some(f.t),
            Frame::Tick(f) => Some(f.t),
            Frame::Sys(f) => Some(f.t),
            Frame::Stats(f) => Some(f.t),
            Frame::Log(f) => Some(f.t),
            Frame::Hop(f) => Some(f.t),
            Frame::Report(f) => Some(f.t),
            Frame::Cycle(f) => Some(f.t),
            Frame::Unknown => None,
        }
    }
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct Hello {
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
    pub backlog: Vec<LogLine>,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct HelloChannel {
    pub name: String,
    pub bus: String,
    pub capacity: u64,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct HelloWifiInterface {
    pub name: String,
    pub hopper: bool,
    pub channels_2g: Vec<u16>,
    pub channels_5g: Vec<u16>,
    pub channels_6g: Vec<u16>,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct HelloInterface {
    pub name: String,
    #[serde(rename = "type")]
    pub interface_type: String,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct Tick {
    pub t: i64,
    pub lag_ms: u64,
    pub lock_us: u64,
    pub bytes: u64,
    pub ch: Vec<TickChannel>,
    pub cap: Vec<TickCapture>,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct TickChannel {
    pub n: String,
    pub w: u64,
    pub c: u64,
    pub m: u64,
    pub b: u64,
    pub e: u64,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct TickCapture {
    pub n: String,
    #[serde(rename = "type")]
    pub capture_type: String,
    pub run: bool,
    pub rx: u64,
    pub db: u64,
    pub di: u64,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct Sys {
    pub t: i64,
    pub cpu: f32,
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

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct Stats {
    pub t: i64,
    /// Plain `i64`: serde's internally tagged enums cannot carry `i128`.
    pub gauges: HashMap<String, i64>,
    pub gauges_f: HashMap<String, f32>,
    pub timers: Option<HashMap<String, StatsTimer>>,
    pub engagement: Vec<String>,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct StatsTimer {
    pub mean: f64,
    pub p99: f64,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct LogLine {
    pub t: i64,
    pub level: String,
    pub msg: String,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct Hop {
    pub t: i64,
    pub dev: String,
    pub freq: u32,
    pub width: String,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct Report {
    pub t: i64,
    pub path: String,
    pub ok: bool,
    pub status: Option<u16>,
    pub rtt_ms: u64,
    pub bytes: u64,
    pub error: Option<String>,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct Cycle {
    pub t: i64,
    pub total_ms: u64,
    pub tables: Vec<CycleTable>,
}

#[derive(Deserialize, Clone, Debug, Default)]
#[serde(default)]
pub struct CycleTable {
    pub name: String,
    pub ms: u64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_tick() {
        let json = r#"{"f":"tick","t":1700000000000,"lag_ms":1,"lock_us":12,"bytes":42,
            "ch":[{"n":"TcpPipeline","w":3,"c":65535,"m":10,"b":100,"e":0}],
            "cap":[{"n":"eth0","type":"Ethernet","run":true,"rx":5,"db":0,"di":1}]}"#;
        let frame: Frame = serde_json::from_str(json).unwrap();
        match frame {
            Frame::Tick(tick) => {
                assert_eq!(tick.t, 1700000000000);
                assert_eq!(tick.ch[0].n, "TcpPipeline");
                assert_eq!(tick.cap[0].capture_type, "Ethernet");
                assert_eq!(tick.cap[0].di, 1);
            }
            _ => panic!("wrong frame"),
        }
    }

    #[test]
    fn parses_stats() {
        let json = r#"{"f":"stats","t":1700000001000,"gauges":{"tables.tcp.sessions.size":1100,"context.macs.size":87},
            "gauges_f":{"sona.rssi.avg":-61.3},"timers":{"tables.tcp.sessions.write":{"mean":123.4,"p99":1530.0}},
            "engagement":["engaged UAV"]}"#;
        let frame: Frame = serde_json::from_str(json).unwrap_or_else(|e| panic!("{}", e));
        match frame {
            Frame::Stats(stats) => {
                assert_eq!(stats.gauges["tables.tcp.sessions.size"], 1100);
                assert_eq!(stats.timers.unwrap()["tables.tcp.sessions.write"].p99, 1530.0);
                assert_eq!(stats.engagement, vec!["engaged UAV".to_string()]);
            }
            _ => panic!("wrong frame"),
        }
    }

    #[test]
    fn tolerates_missing_fields_and_unknown_frames() {
        let frame: Frame = serde_json::from_str(r#"{"f":"sys","t":1}"#).unwrap();
        assert!(matches!(frame, Frame::Sys(s) if s.cores.is_empty()));

        let frame: Frame = serde_json::from_str(r#"{"f":"something_new","t":1}"#).unwrap();
        assert!(matches!(frame, Frame::Unknown));
    }
}
