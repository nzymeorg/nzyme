use std::collections::VecDeque;
use std::sync::Mutex;

use chrono::{DateTime, Utc};

const MAX_EVENTS: usize = 512;

#[derive(Clone, Debug)]
pub enum TelemetryEvent {
    /// A WiFi adapter was tuned to a new frequency.
    Hop { device: String, frequency: u32, width: &'static str },
    /// A submission to the leader finished, successfully or not.
    Report { path: String, ok: bool, status: Option<u16>, rtt_ms: u64, bytes: u64, error: Option<String> },
    /// One pass of the table report cycle finished, with the time each table took (including its upload).
    Cycle { total_ms: u64, tables: Vec<(String, u64)> },
}

pub struct EventLog {
    inner: Mutex<Inner>,
}

struct Inner {
    next_seq: u64,
    events: VecDeque<(u64, DateTime<Utc>, TelemetryEvent)>,
}

impl EventLog {
    pub fn new() -> Self {
        Self {
            inner: Mutex::new(Inner { next_seq: 1, events: VecDeque::with_capacity(MAX_EVENTS) }),
        }
    }

    pub fn push(&self, event: TelemetryEvent) {
        if let Ok(mut inner) = self.inner.lock() {
            if inner.events.len() == MAX_EVENTS {
                inner.events.pop_front();
            }
            let seq = inner.next_seq;
            inner.next_seq += 1;
            inner.events.push_back((seq, Utc::now(), event));
        }
    }

    pub fn latest_seq(&self) -> u64 {
        match self.inner.lock() {
            Ok(inner) => inner.next_seq - 1,
            Err(_) => 0,
        }
    }

    /// All events with a sequence number greater than `after`.
    pub fn since(&self, after: u64) -> Vec<(u64, DateTime<Utc>, TelemetryEvent)> {
        match self.inner.lock() {
            Ok(inner) => inner.events.iter().filter(|(seq, _, _)| *seq > after).cloned().collect(),
            Err(_) => Vec::new(),
        }
    }
}

impl Default for EventLog {
    fn default() -> Self {
        Self::new()
    }
}
