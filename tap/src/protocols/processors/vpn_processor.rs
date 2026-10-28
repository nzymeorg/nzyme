use std::sync::{Arc, Mutex};
use log::error;
use crate::state::tables::vpn_table::VpnTable;
use crate::wired::packets::VpnTunnel;

pub struct VpnProcessor {
    table: Arc<Mutex<VpnTable>>
}

impl VpnProcessor {

    pub fn new(table: Arc<Mutex<VpnTable>>) -> Self {
        Self { table }
    }

    pub fn process(&mut self, tunnel: Arc<VpnTunnel>) {
        match self.table.lock() {
            Ok(table) => table.register_tunnel(tunnel),
            Err(e) => error!("Could not acquire VPN tunnels table mutex: {}", e)
        }
    }

}
