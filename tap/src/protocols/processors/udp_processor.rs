use std::sync::{Arc, Mutex};
use log::{error, info};
use crate::context::context_engine::ContextEngine;
use crate::context::context_source::ContextSource;
use crate::to_pipeline;
use crate::protocols::detection::l7_tagger::L7Tag;
use crate::protocols::detection::l7_tagger::L7Tag::{Unencrypted, DHCP4, DNS, NTP};
use crate::protocols::parsers::{dhcpv4_parser, dns_parser, nbtns_parser, ntp_parser};
use crate::state::tables::name_resolution_table::NameResolutionTable;
use crate::state::tables::udp_table::UdpTable;
use crate::helpers::timer::{record_timer, Timer};
use crate::messagebus::bus::Bus;
use crate::messagebus::channel_names::WiredChannelName;
use crate::metrics::Metrics;
use crate::wired::packets::{Datagram, NameResolutionPacket};
use crate::wired::types::NameResolutionProtocol;

const MDNS_PORT: u16 = 5353;
const LLMNR_PORT: u16 = 5355;

pub struct UDPProcessor {
    bus: Arc<Bus>,
    metrics: Arc<Mutex<Metrics>>,
    udp_table: Arc<Mutex<UdpTable>>,
    nameres_table: Arc<Mutex<NameResolutionTable>>,
    context: Arc<ContextEngine>
}

impl UDPProcessor {

    pub fn new(bus: Arc<Bus>, 
               metrics: Arc<Mutex<Metrics>>, 
               udp_table: Arc<Mutex<UdpTable>>, 
               nameres_table: Arc<Mutex<NameResolutionTable>>,
               context: Arc<ContextEngine>) -> Self {
        Self { bus, metrics, udp_table, nameres_table, context }
    }

    pub fn process(&mut self, datagram: Arc<Datagram>) {
        let mut timer = Timer::new();
        let tags = self.tag_and_route(&datagram);
        timer.stop();
        record_timer(
            timer.elapsed_microseconds(),
            "processors.udp.timer.tag_and_route",
            &self.metrics
        );

        match datagram.tags.lock() {
            Ok(mut t) => t.extend(tags),
            Err(e) => error!("Could not extend tags of datagram: {}", e)
        }

        // Register in UDP table.
        match self.udp_table.lock() {
            Ok(mut table) => table.register_datagram(datagram.clone()),
            Err(e) => {
                error!("Could not acquire UDP table mutex: {}", e);
            }
        }

        if let Some(source_mac) = &datagram.source_mac {
            self.context.register_mac_address_ip(
                source_mac.clone(),
                datagram.source_address,
                ContextSource::Udp
            );
        }
    }

    fn tag_and_route(&mut self, datagram: &Arc<Datagram>) -> Vec<L7Tag> {
        let mut tags = vec![];

        if let Some(dns) = dns_parser::parse(datagram) {
            tags.push(DNS);
            tags.push(Unencrypted);

            // LLMNR and mDNS share the DNS wire format.
            if let Some(protocol) = Self::name_resolution_protocol(datagram) {
                self.register_name_resolution(&NameResolutionPacket::from_dns(&dns, protocol));
            }
            
            // To DNS pipeline.
            let size = dns.size;
            to_pipeline!(
                WiredChannelName::DnsPipeline,
                self.bus.dns_pipeline.sender,
                Arc::new(dns),
                size
            );
        }

        if let Some(dhcp) = dhcpv4_parser::parse(datagram) {
            tags.push(DHCP4);
            tags.push(Unencrypted);

            // To DHCPv4 pipeline.
            let size = dhcp.estimate_struct_size();
            to_pipeline!(
                WiredChannelName::Dhcpv4Pipeline,
                self.bus.dhcpv4_pipeline.sender,
                Arc::new(dhcp),
                size
            );
        }

        if let Some(nbtns) = nbtns_parser::parse(datagram) {
            self.register_name_resolution(&nbtns);
        }

        if let Some(ntp) = ntp_parser::parse(datagram) {
            tags.push(NTP);
            tags.push(Unencrypted);

            let size = ntp.estimate_struct_size();
            to_pipeline!(
                WiredChannelName::NtpPipeline,
                self.bus.ntp_pipeline.sender,
                Arc::new(ntp),
                size
            );
        }

        tags
    }

    fn name_resolution_protocol(datagram: &Arc<Datagram>) -> Option<NameResolutionProtocol> {
        if datagram.source_port == MDNS_PORT || datagram.destination_port == MDNS_PORT {
            Some(NameResolutionProtocol::Mdns)
        } else if datagram.source_port == LLMNR_PORT || datagram.destination_port == LLMNR_PORT {
            Some(NameResolutionProtocol::Llmnr)
        } else {
            None
        }
    }

    fn register_name_resolution(&self, packet: &NameResolutionPacket) {
        match self.nameres_table.lock() {
            Ok(table) => table.register(packet),
            Err(e) => error!("Could not acquire name resolution table mutex: {}", e)
        }
    }

}
