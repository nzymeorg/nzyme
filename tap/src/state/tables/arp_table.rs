use std::sync::{Arc, Mutex};
use log::error;
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::arp_packets_report;
use crate::metrics::Metrics;
use crate::state::tables::table_helpers::clear_mutex_vector;
use crate::wired::packets::ArpPacket;
use crate::wired::types::ArpOpCode;

pub struct ArpTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    metrics: Arc<Mutex<Metrics>>,
    requests: Mutex<Vec<Arc<ArpPacket>>>,
    replies: Mutex<Vec<Arc<ArpPacket>>>
}

impl ArpTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        ArpTable {
            leaderlink,
            metrics,
            requests: Mutex::new(Vec::new()),
            replies: Mutex::new(Vec::new())
        }
    }

    pub fn register_request(&self, packet: Arc<ArpPacket>) {
        if !packet.operation.eq(&ArpOpCode::Request) {
            error!("Attempt to register ARP request packet with wrong OpCode.");
            return
        }

        match self.requests.lock() {
            Ok(mut requests) => {
                requests.push(packet.clone());
            },
            Err(e) => error!("Could not acquire ARP request table mutex: {}", e)
        }
    }

    pub fn register_reply(&self, packet: Arc<ArpPacket>) {
        if !packet.operation.eq(&ArpOpCode::Reply) {
            error!("Attempt to register ARP reply packet with wrong OpCode");
            return
        }

        match self.replies.lock() {
            Ok(mut replies) => {
                replies.push(packet.clone());
            },
            Err(e) => error!("Could not acquire ARP replies table mutex: {}", e)
        }
    }

    pub fn process_report(&self) {
        let mut timer = Timer::new();

        // Combine replies and responses into single report and transmit.
        let mut merged_packets: Vec<Arc<ArpPacket>> = Vec::new();

        match self.requests.lock() {
            Ok(r) => merged_packets.append(&mut r.clone()),
            Err(e) => {
                error!("Could not acquire requests mutex to build ARP report: {}", e);
            }
        }

        match self.replies.lock() {
            Ok(r) => merged_packets.append(&mut r.clone()),
            Err(e) => {
                error!("Could not acquire replies mutex to build ARP report: {}", e);
            }
        }

        // Generate JSON.
        let report = match serde_json::to_string(&arp_packets_report::generate(&merged_packets)) {
            Ok(report) => report,
            Err(e) => {
                error!("Could not serialize ARP packets report: {}", e);
                return;
            }
        };
        timer.stop();
        record_timer(
            timer.elapsed_microseconds(),
            "tables.arp.timer.report_generation",
            &self.metrics
        );

        // Send report.
        match self.leaderlink.lock() {
            Ok(link) => {
                if let Err(e) = link.send_report("arp/packets", report) {
                    error!("Could not submit ARP packets report: {}", e);
                }
            },
            Err(e) => error!("Could not acquire leader link lock for ARP packets \
                        report submission: {}", e)
        }

        clear_mutex_vector(&self.requests);
        clear_mutex_vector(&self.replies);
    }

    pub fn calculate_metrics(&self) {
        let requests_table_size: i128 = match self.requests.lock() {
            Ok(r) => r.len() as i128,
            Err(e) => {
                error!("Could not acquire mutex to calculate ARP requests table size: {}", e);

                -1
            }
        };

        let replies_table_size: i128 = match self.replies.lock() {
            Ok(r) => r.len() as i128,
            Err(e) => {
                error!("Could not acquire mutex to calculate ARP replies table size: {}", e);

                -1
            }
       };

        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("tables.arp.requests.size", requests_table_size);
                metrics.set_gauge("tables.arp.replies.size", replies_table_size);
            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }


}