use std::sync::{Arc, Mutex};
use log::error;
use crate::metrics::Metrics;
use crate::protocols::parsers::stun_tagger::StunFlow;
use crate::state::tables::rtsp_table::RtspTable;
use crate::state::tables::stun_table::StunTable;
use crate::wired::packets::RtspStream;

pub struct StunProcessor {
    table: Arc<Mutex<StunTable>>,
}

impl StunProcessor {

    pub fn new(table: Arc<Mutex<StunTable>>) -> Self {
        Self { table }
    }

    pub fn process(&mut self, session: Arc<StunFlow>) {
        match self.table.lock() {
            Ok(table) => table.register_flow(session),
            Err(e) => error!("Could not acquire STUN negotiations table mutex: {}", e)
        }
    }

}
