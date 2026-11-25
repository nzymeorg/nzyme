use std::sync::{Arc, Mutex};
use log::error;
use crate::state::tables::ipv6_transition_table::Ipv6TransitionTable;
use crate::wired::packets::Ipv6TransitionObservation;

pub struct Ipv6TransitionProcessor {
    table: Arc<Mutex<Ipv6TransitionTable>>
}

impl Ipv6TransitionProcessor {

    pub fn new(table: Arc<Mutex<Ipv6TransitionTable>>) -> Self {
        Self { table }
    }

    pub fn process(&mut self, observation: Arc<Ipv6TransitionObservation>) {
        match self.table.lock() {
            Ok(table) => match observation.as_ref() {
                Ipv6TransitionObservation::Teredo(conversation) => table.register_teredo(conversation),
                Ipv6TransitionObservation::Ipv6InIpv4(packet) => table.register_packet(packet)
            },
            Err(e) => error!("Could not acquire IPv6 transition tunnels table mutex: {}", e)
        }
    }

}
