use std::net::IpAddr;
use std::sync::Arc;
use log::{debug, error};
use crate::messagebus::bus::Bus;
use crate::messagebus::channel_names::WiredChannelName;
use crate::protocols::parsers::tcp::tcp_parser;
use crate::protocols::parsers::udp_parser;
use crate::to_pipeline;
use crate::wired::packets::IPv4Packet;
use crate::wired::types;

pub fn route_ipv4_packet(packet: IPv4Packet, bus: &Arc<Bus>) {
    match types::ProtocolType::try_from(packet.protocol) {
        Ok(protocol_type) => {
            match protocol_type {
                types::ProtocolType::Tcp  => {
                    let tcp = Arc::new(tcp_parser::parse(packet).unwrap()); // TODO unwrap
                    let size = tcp.size;
                    // To TCP pipeline.
                    to_pipeline!(
                                WiredChannelName::TcpPipeline,
                                bus.tcp_pipeline.sender,
                                tcp,
                                size
                    );
                },
                types::ProtocolType::Udp  => {
                    let udp = Arc::new(udp_parser::parse(packet).unwrap()); // TODO unwrap
                    let size = udp.size;

                    // To UDP pipeline.
                    to_pipeline!(
                                WiredChannelName::UdpPipeline,
                                bus.udp_pipeline.sender,
                                udp,
                                size
                    );
                }
            }
        },
        Err(..) => {
            debug!("IPv4 type not implemented: {}", packet.protocol);
        }
    }
}

pub fn is_site_local(addr: IpAddr) -> bool {
    match addr {
        IpAddr::V4(v4) => v4.is_private(),
        IpAddr::V6(v6) => (v6.segments()[0] & 0xfe00) == 0xfc00,
    }
}