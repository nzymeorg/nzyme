use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};
use log::{error, info};
use pcap::Device;
use pnet::datalink;
use pnet::datalink::{MacAddr, NetworkInterface};

pub fn get_first_ipv4_address_of_interface(interface_name: &str) -> Option<Ipv4Addr> {
    get_interface(interface_name)
        .and_then(|interface| {
            interface.ips
                .iter()
                .find_map(|ip_network| match ip_network.ip() {
                    IpAddr::V4(ipv4) => Some(ipv4),
                    _ => None,  // Ignore Ipv6 addresses
                })
        })
}

// A global address if there is one: a DNS server off the link cannot answer a link-local address.
pub fn get_first_ipv6_address_of_interface(interface_name: &str) -> Option<Ipv6Addr> {
    let addresses: Vec<Ipv6Addr> = get_interface(interface_name)?.ips.iter()
        .filter_map(|ip_network| match ip_network.ip() {
            IpAddr::V6(ipv6) => Some(ipv6),
            _ => None
        })
        .collect();

    addresses.iter()
        .find(|address| !address.is_unicast_link_local())
        .or_else(|| addresses.first())
        .copied()
}

pub fn get_mac_address_of_interface(interface_name: &str) -> Option<MacAddr> {
    get_interface(interface_name)
        .and_then(|interface| interface.mac)
}

fn get_interface(name: &str) -> Option<NetworkInterface> {
    datalink::interfaces()
        .into_iter()
        .find(|interface| interface.name == name)
}

pub fn print_devices() {
    match Device::list() {
        Ok(devices) => {
            for device in devices {
                let description = match device.desc {
                    Some(description) => description,
                    None => "no description".to_string()
                };

                let addresses = if device.addresses.is_empty() {
                    "no interface addresses".to_string()
                } else {
                    let mut collected = Vec::new();
                    for address in device.addresses {
                        collected.push(address.addr.to_string());
                    }

                    collected.join(", ")
                };

                info!("Available network device: {} ({}): [{}]", device.name, description, addresses);
            }
        },
        Err(e) => {
            error!("Could not get list of available network devices: {}", e);
        }
    }
}