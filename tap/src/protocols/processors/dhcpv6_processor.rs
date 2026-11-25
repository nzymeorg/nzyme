use std::sync::{Arc, Mutex};
use log::error;
use crate::context::context_engine::ContextEngine;
use crate::context::context_source::ContextSource;
use crate::state::tables::dhcp_table::DhcpTable;
use crate::wired::packets::{Dhcpv6MessageType, Dhcpv6Packet};

pub struct Dhcpv6Processor {
    table: Arc<Mutex<DhcpTable>>,
    context: Arc<ContextEngine>
}

impl Dhcpv6Processor {

    pub fn new(table: Arc<Mutex<DhcpTable>>, context: Arc<ContextEngine>) -> Self {
        Self { table, context }
    }

    pub fn process(&mut self, packet: Arc<Dhcpv6Packet>) {
        match self.table.lock() {
            Ok(table) => table.register_dhcpv6_packet(&packet),
            Err(e) => error!("Could not acquire DHCP table lock: {}", e)
        }

        // Behind a relay the frame is not the client's.
        if packet.message_type == Dhcpv6MessageType::Request && !packet.relayed {
            if let (Some(mac), Some(fqdn)) = (&packet.source_mac, &packet.fqdn) {
                self.context.register_mac_address_hostname(mac.clone(), fqdn.clone(), None, ContextSource::Dhcp);
            }
        }
    }

}
