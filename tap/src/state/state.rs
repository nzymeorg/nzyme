use std::sync::{Arc, Mutex};
use std::thread;
use clokwerk::{Scheduler, TimeUnits};
use log::{error, info};
use std::sync::atomic::Ordering;
use crate::state::arp_state::ArpState;
use crate::metrics::Metrics;
use crate::protocols::tools::ip_tools::DROPPED_FRAGMENTS;

pub struct State {
    pub arp: Arc<Mutex<ArpState>>,
    metrics: Arc<Mutex<Metrics>>
}

impl State {
    
    pub fn new(metrics: Arc<Mutex<Metrics>>) -> Self {
        Self {
            arp: Arc::new(Mutex::new(ArpState::new(metrics.clone()))),
            metrics
        }
    }

    pub fn initialize(&self) {
        info!("Initializing state.");
        let mut scheduler = Scheduler::new();

        let retention_arp = self.arp.clone();
        scheduler.every(1.minutes()).run(move || {
            Self::retention_clean(&retention_arp);
        });

        let metrics_arp = self.arp.clone();
        let metrics = self.metrics.clone();
        scheduler.every(10.seconds()).run(move || {
            Self::calculate_metrics(&metrics_arp);
            Self::calculate_packet_metrics(&metrics);
        });

        thread::spawn(move || {
            loop {
                scheduler.run_pending();
                thread::sleep(std::time::Duration::from_secs(1));
            }
        });
    }

    fn calculate_metrics(arp: &Arc<Mutex<ArpState>>) {
        match arp.lock() {
            Ok(arp) => arp.calculate_metrics(),
            Err(e) => error!("Could not acquire ARP state for metrics calculation: {}", e)
        }
    }

    fn calculate_packet_metrics(metrics: &Arc<Mutex<Metrics>>) {
        match metrics.lock() {
            Ok(mut metrics) => metrics.set_gauge("packets.ipv4.fragments.dropped",
                DROPPED_FRAGMENTS.load(Ordering::Relaxed) as i128),
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

    fn retention_clean(arp: &Arc<Mutex<ArpState>>) {
        match arp.lock() {
            Ok(mut arp) => arp.retention_clean(),
            Err(e) => error!("Could not acquire ARP state for retention cleaning: {}", e)
        }
    }
    
}