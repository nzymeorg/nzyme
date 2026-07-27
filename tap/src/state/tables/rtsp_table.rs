use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use log::error;
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::rtsp_streams_report;
use crate::metrics::Metrics;
use crate::protocols::parsers::l4_key::L4Key;
use crate::state::tables::table_helpers::clear_mutex_hashmap;
use crate::wired::packets::RtspStream;

pub struct RtspTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    metrics: Arc<Mutex<Metrics>>,
    streams: Mutex<HashMap<L4Key, RtspStream>>
}

impl RtspTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        Self {
            leaderlink,
            metrics,
            streams: Mutex::new(HashMap::new())
        }
    }

    pub fn register_stream(&self, stream_ref: Arc<RtspStream>) {
        let stream = (*stream_ref).clone(); // Escape Arc.

        match self.streams.lock() {
            Ok(mut streams) => {
                /*
                 * We insert new stream or overwrite existing one. The tagger always returns a
                 * fully up-to-date representation of the stream with all members accurate.
                 */
                streams.insert(stream.setup_tcp_session_key.clone(), stream);
            }
            Err(e) => error!("Could not acquired RTSP streams table mutex: {}", e)
        }
    }

    pub fn process_report(&self) {
        match self.streams.lock() {
            Ok(streams) => {
                // Generate JSON.
                let mut timer = Timer::new();
                let report = match serde_json::to_string(&rtsp_streams_report::generate(&streams)) {
                    Ok(report) => report,
                    Err(e) => {
                        error!("Could not serialize RTSP streams report: {}", e);
                        return;
                    }
                };
                timer.stop();
                record_timer(
                    timer.elapsed_microseconds(),
                    "tables.rtsp.timer.report_generation",
                    &self.metrics
                );

                // Send report.
                match self.leaderlink.lock() {
                    Ok(link) => {
                        if let Err(e) = link.send_report("rtsp/streams", report) {
                            error!("Could not submit RTSP streams report: {}", e);
                        }
                    },
                    Err(e) => error!("Could not acquire leader link lock for RTSP streams \
                                        report submission: {}", e)
                }
            }
            Err(e) => {
                error!("Could not acquire RTSP streams table mutex for report generation: {}", e);
            }
        }

        // Clean up.
        clear_mutex_hashmap(&self.streams);
    }

    pub fn calculate_metrics(&self) {
        let streams_size: i128 = match self.streams.lock() {
            Ok(s) => s.len() as i128,
            Err(e) => {
                error!("Could not acquire mutex to calculate RTSP streams table size: {}", e);

                -1
            }
        };

        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("tables.rtsp.streams.size", streams_size);
            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

}