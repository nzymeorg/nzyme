use std::sync::{Arc, Mutex};
use std::time::Instant;

use log::{debug, error, info};

use crate::{
    messagebus::bus::Bus,
    metrics::Metrics,
};
use crate::wireless::dot11::capture_helpers::prepare_device;
use crate::wireless::dot11::frames::Dot11CaptureSource::Acquisition;
use crate::wireless::dot11::frames::Dot11RawFrame;

pub struct Capture {
    pub metrics: Arc<Mutex<Metrics>>,
    pub bus: Arc<Bus>
}

impl Capture {

    pub fn run(&mut self, device_name: &str) {
        info!("Starting WiFi capture on [{}]", device_name);

        if let Err(e) = prepare_device(device_name) {
            error!("Could not prepare device [{}]: {}", device_name, e);
            return;
        }

        let device = match pcap::Capture::from_device(device_name) {
            Ok(device) => {
                device
                    .immediate_mode(true)
                    .promisc(true)
            },
            Err(e) => {
                error!("Could not get PCAP device handle on [{}]: {}", device_name, e);
                return;
            }
        };

        let mut handle = match device.open() {
            Ok(handle) => handle,
            Err(e) => {
                error!("Could not get PCAP capture handle on [{}]: {}", device_name, e);
                return;
            }
        };

        if let Err(e) = handle.set_datalink(pcap::Linktype::IEEE802_11_RADIOTAP) {
            error!("Could not set datalink type on [{}]: {}", device_name, e);
            return;
        }

        if let Err(e) = handle.filter("", true) {
            error!("Could not set filter on [{}]: {}", device_name, e);
            return;
        }

        let stats = handle.stats();

        loop {
            let frame = match handle.next_packet() {
                Ok(packet) => packet,
                Err(e) => {
                    error!("Capture exception: {}", e);
                    continue;
                }
            };

            let captured_at = Instant::now();
            let length = frame.data.len();

            match self.metrics.lock() {
                Ok(mut metrics) => {
                    match stats {
                        Ok(stats) => {
                            metrics.increment_processed_bytes_total(length as u32);
                            metrics.update_capture(
                                device_name, true, stats.dropped, stats.if_dropped, true
                            );
                        },
                        Err(ref e) => {
                            error!("Could not fetch handle stats for capture [{}] metrics \
                                update: {}", device_name, e);
                        }
                    }
                },
                Err(e) => error!("Could not acquire metrics mutex: {}", e)
            }

            if length < 4 {
                debug!("Packet too small. Wouldn't even fit radiotap length information. \
                    Skipping.");
                continue;
            }

            let data = Dot11RawFrame {
                capture_source: Acquisition,
                interface_name: device_name.to_string(),
                data: frame.data.to_vec(),
                captured_at
            };
        
            // Write to Dot11 broker pipeline.
            match self.bus.dot11_broker.sender.lock() {
                Ok(mut sender) => { sender.send_packet(Arc::new(data), length as u32) },
                Err(e) => error!("Could not acquire 802.11 handler broker mutex: {}", e)
            }
        }
    }
}