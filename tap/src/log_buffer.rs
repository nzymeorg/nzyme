use std::collections::VecDeque;
use std::sync::Mutex;
use chrono::{DateTime, Local};
use log::Level;

const MAX_LINES: usize = 100;

#[derive(Clone,)]
pub struct LogBufferLine {
    pub timestamp: DateTime<Local>,
    pub level: Level,
    pub message: String
}

/// Every line gets a monotonically increasing sequence number so that consumers can poll for
/// lines they have not seen yet and detect gaps when they fall behind.
pub struct LogBuffer {
    lines: Mutex<Inner>,
}

struct Inner {
    next_seq: u64,
    lines: VecDeque<(u64, LogBufferLine)>
}

impl LogBuffer {
    pub fn new() -> Self {
        Self {
            lines: Mutex::new(Inner {
                next_seq: 1,
                lines: VecDeque::with_capacity(MAX_LINES)
            }),
        }
    }

    pub fn push(&self, line: LogBufferLine) {
        if let Ok(mut inner) = self.lines.lock() {
            if inner.lines.len() == MAX_LINES {
                inner.lines.pop_front();
            }
            let seq = inner.next_seq;
            inner.next_seq += 1;
            inner.lines.push_back((seq, line));
        }
    }

    pub fn snapshot(&self) -> Vec<LogBufferLine> {
        match self.lines.lock() {
            Ok(inner) => inner.lines.iter().map(|(_, l)| l.clone()).collect(),
            Err(_) => Vec::new(),
        }
    }

    /// Sequence number of the most recently pushed line. Zero if nothing has been logged yet.
    pub fn latest_seq(&self) -> u64 {
        match self.lines.lock() {
            Ok(inner) => inner.next_seq - 1,
            Err(_) => 0,
        }
    }

    /// All lines with a sequence number greater than `after`, and the number of lines that were already
    /// evicted from the buffer before they could be returned.
    pub fn since(&self, after: u64) -> (Vec<(u64, LogBufferLine)>, u64) {
        match self.lines.lock() {
            Ok(inner) => {
                let lines: Vec<(u64, LogBufferLine)> = inner.lines.iter()
                    .filter(|(seq, _)| *seq > after)
                    .cloned()
                    .collect();

                let skipped = match lines.first() {
                    Some((first, _)) => first - after - 1,
                    None => 0,
                };

                (lines, skipped)
            }
            Err(_) => (Vec::new(), 0),
        }
    }
}
