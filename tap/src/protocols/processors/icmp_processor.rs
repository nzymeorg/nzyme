use std::sync::{Arc, Mutex};
use log::error;
use crate::state::tables::icmp_table::IcmpTable;
use crate::wired::packets::IcmpPacket;

pub struct IcmpProcessor {
    table: Arc<Mutex<IcmpTable>>
}

impl IcmpProcessor {

    pub fn new(table: Arc<Mutex<IcmpTable>>) -> Self {
        Self { table }
    }

    pub fn process(&mut self, packet: Arc<IcmpPacket>) {
        match self.table.lock() {
            Ok(table) => table.register_packet(packet),
            Err(e) => error!("Could not acquire ICMP table mutex: {}", e)
        }
    }

}
