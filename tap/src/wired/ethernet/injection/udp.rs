use std::net::{Ipv4Addr, Ipv6Addr};
use pnet::datalink::MacAddr;
use pnet::packet::ethernet::{EtherTypes, MutableEthernetPacket};
use pnet::packet::ipv4::{checksum, MutableIpv4Packet};
use pnet::packet::ipv6::MutableIpv6Packet;
use pnet::packet::udp;
use pnet::packet::udp::MutableUdpPacket;
use rand::random;

const ETHERNET_HEADER_LEN: usize = 14;
const IPV4_HEADER_LEN: usize = 20;
const IPV6_HEADER_LEN: usize = 40;
const UDP_HEADER_LEN: usize = 8;

/*
 * I don't expect to every inject anything but UDP or even lower IP or ICMP types. Keeping this
 * not modular or composable at all until we ever inject anything else.
 */

pub fn generate_ipv4_udp_packet(source_mac: MacAddr,
                                destination_mac: MacAddr,
                                source_address: Ipv4Addr,
                                destination_address: Ipv4Addr,
                                source_port: u16,
                                destination_port: u16,
                                payload: Vec<u8>) -> Vec<u8> {
    // Total packet size.
    let packet_size = ETHERNET_HEADER_LEN + IPV4_HEADER_LEN + UDP_HEADER_LEN + payload.len();

    // Buffer to hold entire packet.
    let mut packet = vec![0u8; packet_size];

    // Split the buffer into different header sections.
    let (eth_buffer, rest) = packet.split_at_mut(ETHERNET_HEADER_LEN);
    let (ip_buffer, rest) = rest.split_at_mut(IPV4_HEADER_LEN);
    let (udp_buffer, payload_buffer) = rest.split_at_mut(UDP_HEADER_LEN);

    // Populate Ethernet header.
    let mut eth_header = MutableEthernetPacket::new(eth_buffer).unwrap();
    eth_header.set_source(source_mac);
    eth_header.set_destination(destination_mac);
    eth_header.set_ethertype(EtherTypes::Ipv4);

    // Populate IPv4 header.
    let mut ip_header = MutableIpv4Packet::new(ip_buffer).unwrap();
    ip_header.set_version(4);
    ip_header.set_header_length(5);
    ip_header.set_total_length((IPV4_HEADER_LEN + UDP_HEADER_LEN + payload.len()) as u16);
    ip_header.set_identification(random());
    ip_header.set_ttl(64);
    ip_header.set_next_level_protocol(pnet::packet::ip::IpNextHeaderProtocols::Udp);
    ip_header.set_source(source_address);
    ip_header.set_destination(destination_address);

    // Calculate the IPv4 checksum
    let checksum = checksum(&ip_header.to_immutable());
    ip_header.set_checksum(checksum);

    // Populate UDP header
    let mut udp_header = MutableUdpPacket::new(udp_buffer).unwrap();
    udp_header.set_source(source_port);
    udp_header.set_destination(destination_port);
    udp_header.set_length((UDP_HEADER_LEN + payload.len()) as u16);

    // Copy DNS payload to the UDP payload section
    payload_buffer.copy_from_slice(&payload);

    packet
}

pub fn generate_ipv6_udp_packet(source_mac: MacAddr,
                                destination_mac: MacAddr,
                                source_address: Ipv6Addr,
                                destination_address: Ipv6Addr,
                                source_port: u16,
                                destination_port: u16,
                                payload: Vec<u8>) -> Vec<u8> {
    let packet_size = ETHERNET_HEADER_LEN + IPV6_HEADER_LEN + UDP_HEADER_LEN + payload.len();
    let mut packet = vec![0u8; packet_size];

    let (eth_buffer, rest) = packet.split_at_mut(ETHERNET_HEADER_LEN);
    let (ip_buffer, udp_buffer) = rest.split_at_mut(IPV6_HEADER_LEN);

    let mut eth_header = MutableEthernetPacket::new(eth_buffer).unwrap();
    eth_header.set_source(source_mac);
    eth_header.set_destination(destination_mac);
    eth_header.set_ethertype(EtherTypes::Ipv6);

    let mut ip_header = MutableIpv6Packet::new(ip_buffer).unwrap();
    ip_header.set_version(6);
    ip_header.set_payload_length((UDP_HEADER_LEN + payload.len()) as u16);
    ip_header.set_next_header(pnet::packet::ip::IpNextHeaderProtocols::Udp);
    ip_header.set_hop_limit(64);
    ip_header.set_source(source_address);
    ip_header.set_destination(destination_address);

    let mut udp_header = MutableUdpPacket::new(udp_buffer).unwrap();
    udp_header.set_source(source_port);
    udp_header.set_destination(destination_port);
    udp_header.set_length((UDP_HEADER_LEN + payload.len()) as u16);
    udp_header.set_payload(&payload);

    // Unlike over IPv4, a receiver drops a datagram without a checksum.
    let checksum = udp::ipv6_checksum(&udp_header.to_immutable(), &source_address, &destination_address);
    udp_header.set_checksum(checksum);

    packet
}

#[cfg(test)]
mod tests {
    use pnet::packet::Packet;
    use pnet::packet::ethernet::EthernetPacket;
    use pnet::packet::ipv6::Ipv6Packet;
    use pnet::packet::udp::UdpPacket;
    use super::*;

    #[test]
    fn ipv6_udp_packet_is_well_formed() {
        let source: Ipv6Addr = "2001:db8:1200:34::20".parse().unwrap();
        let destination: Ipv6Addr = "2001:db8::53".parse().unwrap();
        let payload = vec![0xde, 0xad, 0xbe, 0xef, 0x01];

        let packet = generate_ipv6_udp_packet(
            MacAddr::new(2, 0, 0, 0, 0, 1), MacAddr::new(2, 0, 0, 0, 0, 2),
            source, destination, 40000, 53, payload.clone()
        );

        let ethernet = EthernetPacket::new(&packet).unwrap();
        assert_eq!(ethernet.get_ethertype(), EtherTypes::Ipv6);
        assert_eq!(ethernet.get_destination(), MacAddr::new(2, 0, 0, 0, 0, 2));

        let ip = Ipv6Packet::new(ethernet.payload()).unwrap();
        assert_eq!(ip.get_version(), 6);
        assert_eq!(ip.get_payload_length() as usize, 8 + payload.len());
        assert_eq!(ip.get_hop_limit(), 64);
        assert_eq!(ip.get_source(), source);
        assert_eq!(ip.get_destination(), destination);

        let udp = UdpPacket::new(ip.payload()).unwrap();
        assert_eq!(udp.get_source(), 40000);
        assert_eq!(udp.get_destination(), 53);
        assert_eq!(udp.get_length() as usize, 8 + payload.len());
        assert_eq!(udp.payload(), payload.as_slice());
        assert_ne!(udp.get_checksum(), 0);
        assert_eq!(udp.get_checksum(), udp::ipv6_checksum(&udp, &source, &destination));
    }
}
