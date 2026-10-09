use std::fs;
use std::io::{BufWriter, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::str::FromStr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use anyhow::{bail, Error};
use chrono::{DateTime, Utc};
use crossbeam_channel::{bounded, Sender, TrySendError};
use log::{debug, error, info, warn};
use strum::IntoEnumIterator;

use crate::configuration::{Configuration, TELEMETRY_DEFAULT_LISTEN, TELEMETRY_DEFAULT_TICK_MS};
use crate::log_buffer::{LogBuffer, LogBufferLine};
use crate::messagebus::channel_names::{BluetoothChannelName, Dot11ChannelName, GenericChannelName, WiredChannelName};
use crate::metrics::Metrics;
use crate::rpi::rpi_model::detect_pi_model;
use crate::telemetry::events::{EventLog, TelemetryEvent};
use crate::telemetry::frames::*;
use crate::telemetry::system::SystemSampler;

/// Frames queued per client before it is considered dead and disconnected.
const CLIENT_QUEUE: usize = 4096;
const CLIENT_WRITE_TIMEOUT: Duration = Duration::from_secs(5);

struct Client {
    id: u64,
    address: SocketAddr,
    sender: Sender<Arc<str>>,
}

pub struct TelemetryServer {
    configuration: Configuration,
    metrics: Arc<Mutex<Metrics>>,
    log_buffer: Arc<LogBuffer>,
    events: Arc<EventLog>,
    process_started_at: DateTime<Utc>,
    clients: Mutex<Vec<Client>>,
    next_client_id: Mutex<u64>,
    tick: Duration,
}

impl TelemetryServer {
    pub fn new(configuration: Configuration,
               metrics: Arc<Mutex<Metrics>>,
               log_buffer: Arc<LogBuffer>,
               events: Arc<EventLog>,
               process_started_at: DateTime<Utc>) -> Self {
        let tick_ms = configuration.telemetry.as_ref()
            .and_then(|t| t.tick_ms)
            .unwrap_or(TELEMETRY_DEFAULT_TICK_MS);

        Self {
            configuration,
            metrics,
            log_buffer,
            events,
            process_started_at,
            clients: Mutex::new(Vec::new()),
            next_client_id: Mutex::new(1),
            tick: Duration::from_millis(tick_ms),
        }
    }

    /// Binds the socket and starts the acceptor and producer threads. Fails if the socket cannot be bound.
    pub fn start(self: Arc<Self>) -> Result<(), Error> {
        let listen = self.configuration.telemetry.as_ref()
            .and_then(|t| t.listen.clone())
            .unwrap_or_else(|| TELEMETRY_DEFAULT_LISTEN.to_string());

        let address = match SocketAddr::from_str(&listen) {
            Ok(address) => address,
            Err(e) => bail!("Invalid telemetry listen address [{}]: {}", listen, e),
        };

        // Validated by the configuration loader, but this is the last line of defense for an unauthenticated feed.
        if !address.ip().is_loopback() {
            bail!("Telemetry listen address [{}] is not a loopback address.", listen);
        }

        let listener = match TcpListener::bind(address) {
            Ok(listener) => listener,
            Err(e) => bail!("Could not bind telemetry socket [{}]: {}", listen, e),
        };

        info!("Telemetry feed listening on [{}] with a tick of <{}> ms. Attach with `nzyme-util tap top`.",
            listen, self.tick.as_millis());

        let acceptor = Arc::clone(&self);
        thread::spawn(move || acceptor.accept_loop(listener));

        let producer = Arc::clone(&self);
        thread::spawn(move || producer.produce_loop());

        Ok(())
    }

    fn accept_loop(&self, listener: TcpListener) {
        for stream in listener.incoming() {
            match stream {
                Ok(stream) => self.add_client(stream),
                Err(e) => {
                    warn!("Could not accept telemetry client: {}", e);
                    thread::sleep(Duration::from_millis(250));
                }
            }
        }
    }

    fn add_client(&self, stream: TcpStream) {
        let address = match stream.peer_addr() {
            Ok(address) => address,
            Err(e) => {
                warn!("Could not determine telemetry client address: {}", e);
                return;
            }
        };

        let _ = stream.set_nodelay(true);
        let _ = stream.set_write_timeout(Some(CLIENT_WRITE_TIMEOUT));

        let id = {
            let mut next = self.next_client_id.lock().unwrap_or_else(|e| e.into_inner());
            let id = *next;
            *next += 1;
            id
        };

        let hello = match serde_json::to_string(&Frame::Hello(self.build_hello())) {
            Ok(hello) => hello,
            Err(e) => {
                error!("Could not serialize telemetry hello frame: {}", e);
                return;
            }
        };

        let (sender, receiver) = bounded::<Arc<str>>(CLIENT_QUEUE);
        self.clients.lock().unwrap_or_else(|e| e.into_inner()).push(Client { id, address, sender });
        info!("Telemetry client [{}] connected.", address);

        thread::spawn(move || {
            let mut writer = BufWriter::new(stream);

            let result = (|| -> std::io::Result<()> {
                writer.write_all(hello.as_bytes())?;
                writer.write_all(b"\n")?;
                writer.flush()?;

                // The receiver fails once the server dropped the sender, which it does to disconnect a client.
                while let Ok(frame) = receiver.recv() {
                    writer.write_all(frame.as_bytes())?;
                    writer.write_all(b"\n")?;

                    // Drain whatever else is queued before flushing, to keep syscalls down under load.
                    while let Ok(frame) = receiver.try_recv() {
                        writer.write_all(frame.as_bytes())?;
                        writer.write_all(b"\n")?;
                    }
                    writer.flush()?;
                }
                Ok(())
            })();

            match result {
                Ok(()) => debug!("Telemetry client [{}] writer finished.", address),
                Err(e) => debug!("Telemetry client [{}] write failed: {}", address, e),
            }
        });

    }

    fn broadcast(&self, frames: &[String]) {
        if frames.is_empty() {
            return;
        }

        let mut clients = self.clients.lock().unwrap_or_else(|e| e.into_inner());
        let mut dead: Vec<u64> = Vec::new();

        for frame in frames {
            let frame: Arc<str> = Arc::from(frame.as_str());
            for client in clients.iter() {
                if dead.contains(&client.id) {
                    continue;
                }
                match client.sender.try_send(Arc::clone(&frame)) {
                    Ok(()) => {}
                    Err(TrySendError::Full(_)) => {
                        warn!("Telemetry client [{}] is not keeping up. Disconnecting.", client.address);
                        dead.push(client.id);
                    }
                    Err(TrySendError::Disconnected(_)) => {
                        info!("Telemetry client [{}] disconnected.", client.address);
                        dead.push(client.id);
                    }
                }
            }
        }

        // Dropping a client drops its sender, which ends its writer thread.
        clients.retain(|c| !dead.contains(&c.id));
    }

    fn has_clients(&self) -> bool {
        !self.clients.lock().unwrap_or_else(|e| e.into_inner()).is_empty()
    }

    fn produce_loop(&self) {
        // The CPU sampler only reads procfs while somebody is listening.
        let sampling = Arc::new(AtomicBool::new(false));
        let mut sampler = SystemSampler::start(Arc::clone(&sampling));
        let per_second = (1000 / self.tick.as_millis().max(1) as u64).max(1);

        let mut log_seq = self.log_buffer.latest_seq();
        let mut event_seq = self.events.latest_seq();
        let mut next = Instant::now();
        let mut n: u64 = 0;
        // Timer snapshots are taken from the cache the status report fills; only send them when they changed.
        let mut timer_generation: u64 = 0;

        loop {
            next += self.tick;
            let now = Instant::now();
            if next > now {
                thread::sleep(next - now);
            }
            let lag_ms = Instant::now().saturating_duration_since(next).as_millis() as u64;

            if !self.has_clients() {
                // Nobody is listening. Skip old lines and events so a new client only gets what happens from now on
                // (it receives a backlog of recent lines in its hello frame).
                sampling.store(false, Ordering::Relaxed);
                log_seq = self.log_buffer.latest_seq();
                event_seq = self.events.latest_seq();
                next = Instant::now();
                continue;
            }
            sampling.store(true, Ordering::Relaxed);

            n += 1;
            let mut frames: Vec<String> = Vec::with_capacity(8);

            if let Some(tick) = self.build_tick(lag_ms) {
                push_frame(&mut frames, &Frame::Tick(tick));
            }

            // Log lines.
            let (lines, skipped) = self.log_buffer.since(log_seq);
            if skipped > 0 {
                push_frame(&mut frames, &Frame::Log(LogLine {
                    t: Utc::now().timestamp_millis(),
                    level: "WARN".to_string(),
                    msg: format!("[telemetry] {} log lines were not captured for the feed.", skipped),
                }));
            }
            for (seq, line) in lines {
                log_seq = seq;
                push_frame(&mut frames, &Frame::Log(to_log_line(&line)));
            }

            // Events.
            for (seq, timestamp, event) in self.events.since(event_seq) {
                event_seq = seq;
                match event {
                    TelemetryEvent::Hop { device, frequency, width } => {
                        push_frame(&mut frames, &Frame::Hop(Hop {
                            t: timestamp.timestamp_millis(), dev: device, freq: frequency, width,
                        }));
                    }
                    TelemetryEvent::Report { path, ok, status, rtt_ms, bytes, error } => {
                        push_frame(&mut frames, &Frame::Report(Report {
                            t: timestamp.timestamp_millis(), path, ok, status, rtt_ms, bytes, error,
                        }));
                    }
                    TelemetryEvent::Cycle { total_ms, tables } => {
                        push_frame(&mut frames, &Frame::Cycle(Cycle {
                            t: timestamp.timestamp_millis(),
                            total_ms,
                            tables: tables.into_iter().map(|(name, ms)| CycleTable { name, ms }).collect(),
                        }));
                    }
                }
            }

            if n % per_second == 0 {
                push_frame(&mut frames, &Frame::Sys(self.build_sys(&mut sampler)));
                if let Some(stats) = self.build_stats(&mut timer_generation) {
                    push_frame(&mut frames, &Frame::Stats(stats));
                }
            }

            self.broadcast(&frames);
        }
    }

    fn build_tick(&self, lag_ms: u64) -> Option<Tick> {
        let lock_started = Instant::now();
        let metrics = match self.metrics.lock() {
            Ok(metrics) => metrics,
            Err(e) => {
                error!("Could not acquire metrics mutex for telemetry: {}", e);
                return None;
            }
        };
        let lock_us = lock_started.elapsed().as_micros() as u64;

        let ch = metrics.channel_utilizations().into_iter().map(|(name, c)| TickChannel {
            n: name,
            w: c.watermark as u64,
            c: c.capacity as u64,
            m: c.throughput_messages.total as u64,
            b: c.throughput_bytes.total as u64,
            e: c.errors.total as u64,
        }).collect();

        let mut cap: Vec<TickCapture> = metrics.get_captures().into_values().map(|c| TickCapture {
            n: c.interface_name,
            capture_type: c.capture_type.to_string(),
            run: c.is_running,
            rx: c.received as u64,
            db: c.dropped_buffer.total as u64,
            di: c.dropped_interface.total as u64,
        }).collect();
        cap.sort_by(|a, b| a.n.cmp(&b.n));

        let bytes = metrics.get_processed_bytes().total as u64;
        drop(metrics);

        Some(Tick { t: Utc::now().timestamp_millis(), lag_ms, lock_us, bytes, ch, cap })
    }

    fn build_sys(&self, sampler: &mut SystemSampler) -> Sys {
        let cpu = sampler.cpu();
        let (mem_total, mem_free, mem_available) = sampler.memory();
        let process = sampler.process();

        Sys {
            t: Utc::now().timestamp_millis(),
            cpu: cpu.aggregate,
            cores: cpu.cores,
            mem_total,
            mem_free,
            mem_available,
            load: sampler.load_average(),
            temp: sampler.temperature(),
            proc_cpu: process.cpu,
            proc_rss: process.rss,
            proc_threads: process.threads,
            proc_fds: process.fds,
        }
    }

    /// Gauges every second. Timers come from the snapshot the status report computes every ten seconds, so this
    /// never does the expensive percentile work itself; `timer_generation` tracks what was already sent.
    fn build_stats(&self, timer_generation: &mut u64) -> Option<Stats> {
        let mut metrics = match self.metrics.lock() {
            Ok(metrics) => metrics,
            Err(e) => {
                error!("Could not acquire metrics mutex for telemetry: {}", e);
                return None;
            }
        };

        let (generation, snapshots) = metrics.cached_timer_snapshots();
        let timers = if generation != *timer_generation {
            *timer_generation = generation;
            Some(snapshots.into_iter()
                .map(|(name, t)| (name, StatsTimer { mean: t.mean, p99: t.p99 }))
                .collect())
        } else {
            None
        };

        let engagement = metrics.get_engagement_logs().into_iter()
            .map(|e| format!("{} {}", e.timestamp.format("%H:%M:%S"), e.message))
            .collect();

        Some(Stats {
            t: Utc::now().timestamp_millis(),
            gauges: metrics.get_gauges_long().into_iter().map(|(k, v)| (k, v as i64)).collect(),
            gauges_f: metrics.get_gauges_float(),
            timers,
            engagement,
        })
    }

    fn build_hello(&self) -> Hello {
        let c = &self.configuration;

        let mut channels: Vec<HelloChannel> = Vec::new();
        for name in WiredChannelName::iter().map(|c| c.to_string()) {
            channels.push(HelloChannel { capacity: channel_capacity(&name, c), name, bus: "ethernet".to_string() });
        }
        for name in Dot11ChannelName::iter().map(|c| c.to_string()) {
            channels.push(HelloChannel { capacity: channel_capacity(&name, c), name, bus: "dot11".to_string() });
        }
        for name in BluetoothChannelName::iter().map(|c| c.to_string()) {
            channels.push(HelloChannel { capacity: channel_capacity(&name, c), name, bus: "bluetooth".to_string() });
        }
        for name in GenericChannelName::iter().map(|c| c.to_string()) {
            channels.push(HelloChannel { capacity: channel_capacity(&name, c), name, bus: "generic".to_string() });
        }

        let mut wifi: Vec<HelloWifiInterface> = Vec::new();
        let mut interfaces: Vec<HelloInterface> = Vec::new();

        if let Some(ifaces) = &c.wifi_interfaces {
            for (name, iface) in ifaces.iter().filter(|(_, i)| i.active) {
                wifi.push(HelloWifiInterface {
                    name: name.clone(),
                    hopper: !iface.disable_hopper.unwrap_or(false),
                    channels_2g: iface.channels_2g.clone().unwrap_or_default(),
                    channels_5g: iface.channels_5g.clone().unwrap_or_default(),
                    channels_6g: iface.channels_6g.clone().unwrap_or_default(),
                });
                interfaces.push(HelloInterface { name: name.clone(), interface_type: "WiFi".to_string() });
            }
        }
        if let Some(ifaces) = &c.ethernet_interfaces {
            for name in ifaces.iter().filter(|(_, i)| i.active).map(|(n, _)| n) {
                interfaces.push(HelloInterface { name: name.clone(), interface_type: "Ethernet".to_string() });
            }
        }
        if let Some(ifaces) = &c.rawip_interfaces {
            for name in ifaces.iter().filter(|(_, i)| i.active).map(|(n, _)| n) {
                interfaces.push(HelloInterface { name: name.clone(), interface_type: "RawIp".to_string() });
            }
        }
        if let Some(ifaces) = &c.bluetooth_interfaces {
            for name in ifaces.iter().filter(|(_, i)| i.active).map(|(n, _)| n) {
                interfaces.push(HelloInterface { name: name.clone(), interface_type: "Bluetooth".to_string() });
            }
        }
        if let Some(ifaces) = &c.wifi_engagement_interfaces {
            for name in ifaces.iter().filter(|(_, i)| i.active).map(|(n, _)| n) {
                interfaces.push(HelloInterface { name: name.clone(), interface_type: "WiFiEngagement".to_string() });
            }
        }
        wifi.sort_by(|a, b| a.name.cmp(&b.name));
        interfaces.sort_by(|a, b| a.name.cmp(&b.name));

        let backlog = self.log_buffer.snapshot().iter().map(to_log_line).collect();

        Hello {
            t: Utc::now().timestamp_millis(),
            protocol: PROTOCOL_VERSION,
            version: env!("CARGO_PKG_VERSION").to_string(),
            started_at: self.process_started_at.timestamp_millis(),
            hostname: fs::read_to_string("/proc/sys/kernel/hostname")
                .map(|h| h.trim().to_string())
                .unwrap_or_else(|_| "unknown".to_string()),
            pid: std::process::id(),
            tick_ms: self.tick.as_millis() as u64,
            cores: thread::available_parallelism().map(|n| n.get()).unwrap_or(0),
            rpi: detect_pi_model(),
            channels,
            wifi,
            interfaces,
            backlog,
        }
    }
}

fn push_frame(frames: &mut Vec<String>, frame: &Frame) {
    match serde_json::to_string(frame) {
        Ok(json) => frames.push(json),
        Err(e) => error!("Could not serialize telemetry frame: {}", e),
    }
}

fn to_log_line(line: &LogBufferLine) -> LogLine {
    LogLine {
        t: line.timestamp.timestamp_millis(),
        level: line.level.to_string().to_uppercase(),
        msg: line.message.clone(),
    }
}

/// Configured capacity of a channel, mirroring how `Bus::new` sizes them.
fn channel_capacity(name: &str, c: &Configuration) -> u64 {
    let size = match name {
        "EthernetBroker" => c.performance.ethernet_broker_buffer_capacity as i64,
        "Dot11Broker" => c.performance.wifi_broker_buffer_capacity as i64,
        "Dot11FramesPipeline" => c.protocols.wifi.pipeline_size as i64,
        "BluetoothDevicesPipeline" => c.performance.bluetooth_devices_pipeline_size.unwrap_or(1024) as i64,
        "ArpPipeline" => c.protocols.arp.pipeline_size as i64,
        "TcpPipeline" => c.protocols.tcp.pipeline_size as i64,
        "UdpPipeline" => c.protocols.udp.pipeline_size as i64,
        "DnsPipeline" => c.protocols.dns.pipeline_size as i64,
        "SocksPipeline" => c.protocols.socks.pipeline_size as i64,
        "SshPipeline" => c.protocols.ssh.pipeline_size as i64,
        "Dhcpv4Pipeline" => c.protocols.dhcpv4.pipeline_size as i64,
        "NtpPipeline" => c.protocols.ntp.pipeline_size as i64,
        "RtspPipeline" => c.protocols.rtsp.pipeline_size as i64,
        "StunPipeline" => c.protocols.stun.pipeline_size as i64,
        "WebRtcPipeline" => c.protocols.webrtc.pipeline_size as i64,
        "UavRemoteIdPipeline" => c.protocols.uav_remote_id.pipeline_size as i64,
        _ => 0,
    };
    size.max(0) as u64
}
