//! Where frames come from: a live loopback socket on the tap with automatic reconnects, optionally recorded
//! to a file, or a recording replayed at a chosen speed.

use std::fs::File;
use std::io::{BufRead, BufReader, BufWriter, ErrorKind, Write};
use std::net::{SocketAddr, TcpStream, ToSocketAddrs};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{channel, Receiver, Sender};
use std::sync::Arc;
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

use crate::apps::tap::protocol::Frame;

const CONNECT_TIMEOUT: Duration = Duration::from_secs(2);
const READ_TIMEOUT: Duration = Duration::from_millis(500);
const RETRY_DELAY: Duration = Duration::from_secs(1);
const RECORD_FLUSH: Duration = Duration::from_secs(1);

#[derive(Clone)]
pub enum LinkState {
    Connecting { attempt: u32, last_error: Option<String> },
    Connected { since: Instant },
    Lost { error: String, since: Instant },
}

pub enum SourceEvent {
    Link(LinkState),
    Frame(Frame),
    /// A line the client could not parse. Counted, not fatal.
    BadLine,
}

pub struct LiveSource {
    pub address: String,
    pub recording: Option<String>,
    receiver: Receiver<SourceEvent>,
    quit: Arc<AtomicBool>,
    worker: Option<JoinHandle<()>>,
}

impl LiveSource {
    pub fn start(address: String, record: Option<(String, File)>) -> Self {
        let (sender, receiver) = channel();
        let quit = Arc::new(AtomicBool::new(false));

        let recording = record.as_ref().map(|(path, _)| path.clone());
        let worker_address = address.clone();
        let worker_quit = Arc::clone(&quit);
        let worker = thread::spawn(move || {
            let mut recorder = record.map(|(_, file)| Recorder { writer: BufWriter::new(file), last_flush: Instant::now() });
            run_live(&worker_address, sender, worker_quit, &mut recorder);
            if let Some(recorder) = &mut recorder {
                let _ = recorder.writer.flush();
            }
        });

        LiveSource { address, recording, receiver, quit, worker: Some(worker) }
    }

    pub fn drain(&self) -> Vec<SourceEvent> {
        let mut events = Vec::new();
        while let Ok(event) = self.receiver.try_recv() {
            events.push(event);
        }
        events
    }
}

impl Drop for LiveSource {
    fn drop(&mut self) {
        self.quit.store(true, Ordering::Relaxed);
        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
    }
}

struct Recorder {
    writer: BufWriter<File>,
    last_flush: Instant,
}

fn run_live(address: &str, sender: Sender<SourceEvent>, quit: Arc<AtomicBool>, recorder: &mut Option<Recorder>) {
    let mut attempt: u32 = 0;
    let mut last_error: Option<String> = None;

    loop {
        if quit.load(Ordering::Relaxed) {
            return;
        }

        attempt += 1;
        if sender.send(SourceEvent::Link(LinkState::Connecting { attempt, last_error: last_error.clone() })).is_err() {
            return;
        }

        let stream = resolve(address).and_then(|addr| {
            TcpStream::connect_timeout(&addr, CONNECT_TIMEOUT).map_err(|e| e.to_string())
        });

        let stream = match stream {
            Ok(stream) => stream,
            Err(e) => {
                last_error = Some(e);
                if sleep_unless_quit(&quit, RETRY_DELAY) {
                    return;
                }
                continue;
            }
        };

        let _ = stream.set_read_timeout(Some(READ_TIMEOUT));
        let _ = stream.set_nodelay(true);
        if sender.send(SourceEvent::Link(LinkState::Connected { since: Instant::now() })).is_err() {
            return;
        }

        let mut reader = BufReader::new(stream);
        let mut line = String::new();
        let lost = loop {
            if quit.load(Ordering::Relaxed) {
                return;
            }

            line.clear();
            match reader.read_line(&mut line) {
                Ok(0) => break "Connection closed by the tap.".to_string(),
                Ok(_) => {
                    if let Some(recorder) = recorder {
                        let _ = recorder.writer.write_all(line.as_bytes());
                        if !line.ends_with('\n') {
                            let _ = recorder.writer.write_all(b"\n");
                        }
                        if recorder.last_flush.elapsed() >= RECORD_FLUSH {
                            let _ = recorder.writer.flush();
                            recorder.last_flush = Instant::now();
                        }
                    }

                    let event = match serde_json::from_str::<Frame>(line.trim_end()) {
                        Ok(frame) => SourceEvent::Frame(frame),
                        Err(_) => SourceEvent::BadLine,
                    };
                    if sender.send(event).is_err() {
                        return;
                    }
                }
                Err(e) if matches!(e.kind(), ErrorKind::WouldBlock | ErrorKind::TimedOut | ErrorKind::Interrupted) => continue,
                Err(e) => break e.to_string(),
            }
        };

        last_error = Some(lost.clone());
        if sender.send(SourceEvent::Link(LinkState::Lost { error: lost, since: Instant::now() })).is_err() {
            return;
        }
        attempt = 0;
        if sleep_unless_quit(&quit, RETRY_DELAY) {
            return;
        }
    }
}

fn resolve(address: &str) -> Result<SocketAddr, String> {
    address.to_socket_addrs()
        .map_err(|e| e.to_string())?
        .next()
        .ok_or_else(|| format!("Could not resolve [{}].", address))
}

/// Sleeps in small slices. Returns true if quit was requested meanwhile.
fn sleep_unless_quit(quit: &AtomicBool, duration: Duration) -> bool {
    let deadline = Instant::now() + duration;
    while Instant::now() < deadline {
        if quit.load(Ordering::Relaxed) {
            return true;
        }
        thread::sleep(Duration::from_millis(50));
    }
    false
}

// ---------------------------------------------------------------------------------------------------------------
// Replay
// ---------------------------------------------------------------------------------------------------------------

pub const SPEEDS: &[f64] = &[0.25, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0];

/// A recording loaded into memory, played back on a virtual clock. Frames are released in file order once
/// their stream time has been reached.
pub struct ReplaySource {
    pub path: String,
    frames: Vec<(i64, Frame)>,
    cursor: usize,
    first_t: i64,
    pub duration_ms: i64,
    /// Virtual playback position relative to the first frame.
    pub position_ms: f64,
    pub speed: f64,
    pub paused: bool,
    last_wall: Instant,
    pub bad_lines: usize,
}

impl ReplaySource {
    pub fn load(path: &str, speed: f64) -> Result<Self, String> {
        let file = File::open(path).map_err(|e| format!("Could not open [{}]: {}", path, e))?;
        let reader = BufReader::new(file);

        let mut frames: Vec<(i64, Frame)> = Vec::new();
        let mut bad_lines = 0;
        let mut last_t = 0;
        for line in reader.lines() {
            let line = line.map_err(|e| format!("Could not read [{}]: {}", path, e))?;
            if line.trim().is_empty() {
                continue;
            }
            match serde_json::from_str::<Frame>(&line) {
                Ok(frame) => {
                    // Frames without a timestamp or with a clock that went backwards keep the previous time.
                    let t = frame.timestamp().filter(|t| *t >= last_t).unwrap_or(last_t);
                    last_t = t;
                    frames.push((t, frame));
                }
                Err(_) => bad_lines += 1,
            }
        }

        if frames.is_empty() {
            return Err(format!("No telemetry frames found in [{}].", path));
        }

        let first_t = frames.first().map(|(t, _)| *t).unwrap_or(0);
        let duration_ms = frames.last().map(|(t, _)| *t - first_t).unwrap_or(0);

        Ok(ReplaySource {
            path: path.to_string(), frames, cursor: 0, first_t, duration_ms,
            position_ms: 0.0, speed, paused: false, last_wall: Instant::now(), bad_lines,
        })
    }

    pub fn frame_count(&self) -> usize {
        self.frames.len()
    }

    pub fn finished(&self) -> bool {
        self.cursor >= self.frames.len()
    }

    /// Advances the virtual clock and returns the frames that became due.
    pub fn advance(&mut self, now: Instant) -> Vec<Frame> {
        if !self.paused {
            let elapsed = now.duration_since(self.last_wall).as_secs_f64() * 1000.0;
            self.position_ms = (self.position_ms + elapsed * self.speed).min(self.duration_ms as f64);
        }
        self.last_wall = now;
        self.take_due()
    }

    fn take_due(&mut self) -> Vec<Frame> {
        let mut due = Vec::new();
        while self.cursor < self.frames.len() {
            let (t, frame) = &self.frames[self.cursor];
            if (*t - self.first_t) as f64 > self.position_ms {
                break;
            }
            due.push(frame.clone());
            self.cursor += 1;
        }
        due
    }

    pub fn toggle_pause(&mut self) {
        self.paused = !self.paused;
        self.last_wall = Instant::now();
    }

    pub fn change_speed(&mut self, direction: i32) {
        let index = SPEEDS.iter().position(|s| (*s - self.speed).abs() < f64::EPSILON).unwrap_or(2) as i32;
        let next = (index + direction).clamp(0, SPEEDS.len() as i32 - 1) as usize;
        self.speed = SPEEDS[next];
    }

    /// Moves to an absolute position and returns every frame from the start up to it, so the caller can rebuild
    /// its state. Seeking backwards is the same as seeking forwards: the state is always replayed from the start.
    pub fn seek_to(&mut self, position_ms: f64) -> Vec<Frame> {
        self.position_ms = position_ms.clamp(0.0, self.duration_ms as f64);
        self.cursor = 0;
        self.last_wall = Instant::now();
        self.take_due()
    }

    pub fn seek_by(&mut self, delta_ms: f64) -> Vec<Frame> {
        self.seek_to(self.position_ms + delta_ms)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    /// Each test gets its own file: tests run in parallel and would otherwise race on one recording.
    fn recording(name: &str) -> String {
        let dir = std::env::temp_dir().join(format!("nzyme-util-replay-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join(format!("{}.ndjson", name));
        let mut file = File::create(&path).unwrap();
        writeln!(file, r#"{{"f":"hello","t":1000,"tick_ms":100}}"#).unwrap();
        writeln!(file, r#"{{"f":"tick","t":1100}}"#).unwrap();
        writeln!(file, "this is not json").unwrap();
        writeln!(file, r#"{{"f":"tick","t":1200}}"#).unwrap();
        writeln!(file, r#"{{"f":"tick","t":2200}}"#).unwrap();
        path.to_string_lossy().to_string()
    }

    #[test]
    fn loads_and_paces_a_recording() {
        let mut replay = ReplaySource::load(&recording("paces"), 1.0).unwrap();
        assert_eq!(replay.frame_count(), 4);
        assert_eq!(replay.bad_lines, 1);
        assert_eq!(replay.duration_ms, 1200);

        // At position 0 only the hello is due.
        let due = replay.seek_to(0.0);
        assert_eq!(due.len(), 1);
        assert!(matches!(due[0], Frame::Hello(_)));

        // Seeking rebuilds from the start.
        let due = replay.seek_to(250.0);
        assert_eq!(due.len(), 3);
        assert!(!replay.finished());

        let due = replay.seek_to(5000.0);
        assert_eq!(due.len(), 4);
        assert!(replay.finished());
    }

    #[test]
    fn speed_steps_through_presets() {
        let mut replay = ReplaySource::load(&recording("speed"), 1.0).unwrap();
        replay.change_speed(1);
        assert_eq!(replay.speed, 2.0);
        replay.change_speed(-2);
        assert_eq!(replay.speed, 0.5);
        for _ in 0..20 {
            replay.change_speed(1);
        }
        assert_eq!(replay.speed, 16.0);
    }
}
