use std::sync::{Arc, Mutex};
use log::error;
use crate::metrics::Metrics;
use crate::state::tables::rtsp_table::RtspTable;
use crate::wired::packets::RtspStream;

pub struct RtspProcessor {
    table: Arc<Mutex<RtspTable>>,
}

impl RtspProcessor {

    pub fn new(table: Arc<Mutex<RtspTable>>) -> Self {
        Self { table }
    }

    pub fn process(&mut self, session: Arc<RtspStream>) {
        match self.table.lock() {
            Ok(table) => table.register_stream(session),
            Err(e) => error!("Could not acquire RTSP session table mutex: {}", e)
        }
    }

}
