use std::fs;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use log::warn;
use systemstat::{Platform, System};

use crate::rpi::rpi_model::detect_pi_model;
use crate::rpi::rpi_temperature;

#[derive(Clone, Default)]
pub struct CpuSample {
    pub aggregate: f32,
    pub cores: Vec<f32>,
}

pub struct SystemSampler {
    system: System,
    cpu: Arc<Mutex<CpuSample>>,
    is_pi: bool,
    clock_ticks_per_second: f64,
    page_size: u64,
    last_proc: Option<(Instant, u64)>,
}

pub struct ProcessSample {
    pub cpu: Option<f32>,
    pub rss: Option<u64>,
    pub threads: Option<u32>,
    pub fds: Option<u32>,
}

impl SystemSampler {
    /// Samples CPU load once a second while `active` is set and sleeps otherwise.
    pub fn start(active: Arc<AtomicBool>) -> Self {
        let cpu = Arc::new(Mutex::new(CpuSample::default()));

        let cpu_bg = Arc::clone(&cpu);
        thread::spawn(move || {
            let system = System::new();
            let mut warned = false;
            loop {
                if !active.load(Ordering::Relaxed) {
                    thread::sleep(Duration::from_secs(1));
                    continue;
                }

                let aggregate = system.cpu_load_aggregate();
                let cores = system.cpu_load();
                thread::sleep(Duration::from_secs(1));

                let sample = match (aggregate, cores) {
                    (Ok(aggregate), Ok(cores)) => match (aggregate.done(), cores.done()) {
                        (Ok(aggregate), Ok(cores)) => Some(CpuSample {
                            aggregate: load_percent(&aggregate),
                            cores: cores.iter().map(load_percent).collect(),
                        }),
                        _ => None,
                    },
                    _ => None,
                };

                match sample {
                    Some(sample) => {
                        if let Ok(mut cpu) = cpu_bg.lock() {
                            *cpu = sample;
                        }
                    }
                    None if !warned => {
                        warn!("Could not sample CPU load for telemetry feed.");
                        warned = true;
                    }
                    None => {}
                }
            }
        });

        Self {
            system: System::new(),
            cpu,
            is_pi: detect_pi_model().is_some(),
            clock_ticks_per_second: sysconf(libc::_SC_CLK_TCK).unwrap_or(100) as f64,
            page_size: sysconf(libc::_SC_PAGESIZE).unwrap_or(4096) as u64,
            last_proc: None,
        }
    }

    pub fn cpu(&self) -> CpuSample {
        self.cpu.lock().map(|c| c.clone()).unwrap_or_default()
    }

    /// Total, free and available memory in bytes.
    pub fn memory(&self) -> (u64, u64, Option<u64>) {
        match self.system.memory() {
            Ok(mem) => {
                let available = mem.platform_memory.meminfo.get("MemAvailable").map(|b| b.as_u64());
                (mem.total.as_u64(), mem.free.as_u64(), available)
            }
            Err(_) => (0, 0, None),
        }
    }

    pub fn load_average(&self) -> Option<[f32; 3]> {
        self.system.load_average().ok().map(|l| [l.one, l.five, l.fifteen])
    }

    pub fn temperature(&self) -> Option<f32> {
        if self.is_pi { Some(rpi_temperature::read_cpu_temp_c()) } else { None }
    }

    /// CPU usage of this process since the previous call, resident set size, thread count and open file
    /// descriptors. Read from procfs.
    pub fn process(&mut self) -> ProcessSample {
        let now = Instant::now();
        let stat = fs::read_to_string("/proc/self/stat").ok();

        let mut cpu = None;
        let mut rss = None;
        let mut threads = None;

        if let Some(stat) = stat {
            // The command name is in parentheses and may contain spaces; everything after it is positional.
            if let Some(end) = stat.rfind(')') {
                let fields: Vec<&str> = stat[end + 1..].split_whitespace().collect();
                // Field numbers from proc(5), minus the three fields before the split (pid, comm, state is fields[0]).
                let utime: u64 = fields.get(11).and_then(|f| f.parse().ok()).unwrap_or(0);
                let stime: u64 = fields.get(12).and_then(|f| f.parse().ok()).unwrap_or(0);
                threads = fields.get(17).and_then(|f| f.parse().ok());
                rss = fields.get(21).and_then(|f| f.parse::<u64>().ok()).map(|pages| pages * self.page_size);

                let ticks = utime + stime;
                if let Some((last_at, last_ticks)) = self.last_proc {
                    let elapsed = now.duration_since(last_at).as_secs_f64();
                    if elapsed > 0.0 {
                        let seconds = ticks.saturating_sub(last_ticks) as f64 / self.clock_ticks_per_second;
                        cpu = Some((seconds / elapsed * 100.0) as f32);
                    }
                }
                self.last_proc = Some((now, ticks));
            }
        }

        let fds = fs::read_dir("/proc/self/fd").ok().map(|d| d.count() as u32);

        ProcessSample { cpu, rss, threads, fds }
    }
}

fn load_percent(load: &systemstat::CPULoad) -> f32 {
    (load.user + load.nice + load.system + load.interrupt) * 100.0
}

fn sysconf(name: libc::c_int) -> Option<i64> {
    // SAFETY: sysconf has no preconditions and only reads system configuration.
    let value = unsafe { libc::sysconf(name) };
    if value > 0 { Some(value as i64) } else { None }
}
