use std::net::IpAddr;
use std::sync::Arc;
use byteorder::{BigEndian, ByteOrder};
use serde::__private228::from_utf8_lossy;
use crate::wired::packets::{Dhcpv4Packet, Datagram};
use crate::wired::types::{Dhcpv4MessageType, Dhcpv4OpCode, HardwareType};
use crate::helpers::network::{to_ipv4_address, to_mac_address_string};
use crate::tracemark;

pub fn parse(udp: &Arc<Datagram>) -> Option<Dhcpv4Packet> {
    if udp.payload.len() < 240 {
        tracemark!("DHCP");
        return None;
    }

    // BOOTP op code.
    let op_code = match udp.payload[0] {
        1 => Dhcpv4OpCode::Request,
        2 => Dhcpv4OpCode::Reply,
        _ => {
            // Invalid OP code.
            tracemark!("DHCP");
            return None
        }
    };

    // Hardware type.
    let hardware_type = match udp.payload[1] {
        1 => HardwareType::Ethernet,
        _ => {
            // We only consider Ethernet.
            tracemark!("DHCP");
            return None;
        }
    };

    // Hardware type length.
    if udp.payload[2] != 6 {
        // Hardware type "Ethernet" length is 6.
        tracemark!("DHCP");
        return None;
    }

    // Skipping hops field. (1 byte)

    let transaction_id = BigEndian::read_u32(&udp.payload[4..8]);
    let seconds_elapsed = BigEndian::read_u16(&udp.payload[8..10]);

    // Skipping BOOTP flags (2 bytes)

    /*
     * Client address. Set when the client already has an IP address and is renewing
     * rebinding their address. 0.0.0.0 when undefined.
     */
    let dhcp_client_address = match to_ipv4_address(&udp.payload[12..16]) {
        addr if addr.is_unspecified() => None,
        addr => Some(addr)
    };

    /*
     * Assigned address (called "your address" in DHCP) in OFFER or ACK messages.
     * Always set by server. Client assigns this address. 0.0.0.0 when undefined.
     */
    let assigned_address = match to_ipv4_address(&udp.payload[16..20]) {
        addr if addr.is_unspecified() => None,
        addr => Some(addr)
    };

    // Skipping "next server" and "relay agent" IP addresses.

    // Client MAC address.
    let client_mac_address = to_mac_address_string(&udp.payload[28..34]);

    // Skipping padding, server hostname, BOOTP file name, magic cookie.

    // Iterate over options.
    let mut message_type = Dhcpv4MessageType::Unknown;
    let mut requested_ip_address = None;
    let mut hostname = None;
    let mut parameter_request_list = Vec::new();
    let mut client_identifier = None;
    let mut fqdn = None;
    let mut options: Vec<u8> = Vec::new();
    let mut vendor_class = None;
    let mut server_identifier = None;
    let mut subnet_mask = None;
    let mut routers = Vec::new();
    let mut dns_servers = Vec::new();
    let mut domain_name = None;
    let mut lease_time_seconds = None;

    let mut cursor: usize = 240;
    loop {
        if udp.payload.len() <= cursor {
            tracemark!("DHCP");
            break;
        }

        let option_id = &udp.payload[cursor];
        options.push(*option_id);

        cursor += 1;

        if udp.payload.len() <= cursor {
            tracemark!("DHCP");
            break;
        }

        let length: usize = udp.payload[cursor] as usize;
        cursor += 1;

        if length == 0 {
            tracemark!("DHCP");
            break;
        }

        if udp.payload.len() <= cursor+length {
            tracemark!("DHCP");
            break;
        }

        match option_id {
            1 => {
                // Subnet mask.
                if length == 4 {
                    subnet_mask = Some(to_ipv4_address(&udp.payload[cursor..cursor+4]));
                }
            },
            3 => {
                // Routers. (list of IPv4 addresses)
                routers = parse_address_list(&udp.payload[cursor..cursor+length]);
            },
            6 => {
                // DNS servers. (list of IPv4 addresses)
                dns_servers = parse_address_list(&udp.payload[cursor..cursor+length]);
            },
            12 => {
                // Client hostname. (variable length)
                hostname = Some(
                    from_utf8_lossy(&udp.payload[cursor..cursor+length]).to_string()
                );
            },
            15 => {
                // Domain name. (variable length)
                domain_name = Some(
                    from_utf8_lossy(&udp.payload[cursor..cursor+length]).trim_end_matches('\0').to_string()
                );
            },
            50 => {
                // Requested IP address.
                if length != 4 {
                    tracemark!("DHCP");
                    break;
                }

                requested_ip_address = Some(to_ipv4_address(&udp.payload[cursor..cursor+4]));
            },
            53 => {
                // Message type.
                if length != 1 {
                    tracemark!("DHCP");
                    break;
                }

                message_type = match Dhcpv4MessageType::try_from(udp.payload[cursor]) {
                    Ok(t) => t,
                    Err(_) => {
                        tracemark!("DHCP");
                        break;
                    }
                };
            },
            51 => {
                // Lease time in seconds.
                if length == 4 {
                    lease_time_seconds = Some(BigEndian::read_u32(&udp.payload[cursor..cursor+4]));
                }
            },
            54 => {
                // Server identifier.
                if length == 4 {
                    server_identifier = Some(to_ipv4_address(&udp.payload[cursor..cursor+4]));
                }
            },
            55 => {
                // Parameter request list.
                let mut plist_cursor = 0;
                while plist_cursor < length {
                    parameter_request_list.push(udp.payload[cursor+plist_cursor]);
                    plist_cursor += 1;
                }
            },
            60 => {
                // Vendor class identifier. (variable length)
                vendor_class = Some(
                    from_utf8_lossy(&udp.payload[cursor..cursor+length]).to_string()
                );
            },
            61 => {
                client_identifier = Some(to_hex_string(&udp.payload[cursor..cursor+length]));
            },
            81 => {
                fqdn = parse_fqdn(&udp.payload[cursor..cursor+length]);
            },
            255 => break, // End tag.
            _ => { /* NOOP */}
        }

        cursor += length;
    }

    Some(Dhcpv4Packet { 
        timestamp: udp.timestamp,
        source_mac: udp.source_mac.clone(),
        destination_mac: udp.destination_mac.clone(),
        source_address: udp.source_address,
        destination_address: udp.destination_address,
        source_port: udp.source_port,
        destination_port: udp.destination_port,
        options,
        vendor_class,
        op_code,
        hardware_type,
        transaction_id,
        seconds_elapsed,
        dhcp_client_address,
        assigned_address,
        client_mac_address,
        message_type,
        requested_ip_address,
        hostname,
        parameter_request_list,
        client_identifier,
        fqdn,
        server_identifier,
        subnet_mask,
        routers,
        dns_servers,
        domain_name,
        lease_time_seconds
    })
}

// Trailing bytes that do not form a full address are ignored.
fn parse_address_list(bytes: &[u8]) -> Vec<IpAddr> {
    bytes.chunks_exact(4).map(to_ipv4_address).collect()
}

fn to_hex_string(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{:02x}", b)).collect::<Vec<String>>().join(":")
}

// RFC 4702: flags, two RCODE bytes, then the name. The E flag (0x04) means DNS wire format, otherwise ASCII.
fn parse_fqdn(bytes: &[u8]) -> Option<String> {
    if bytes.len() < 4 {
        return None;
    }

    let flags = bytes[0];
    let name = &bytes[3..];

    let decoded = if flags & 0x04 != 0 {
        let mut labels: Vec<String> = Vec::new();
        let mut cursor = 0;
        while cursor < name.len() {
            let label_length = name[cursor] as usize;
            cursor += 1;

            if label_length == 0 || cursor + label_length > name.len() {
                break;
            }

            labels.push(from_utf8_lossy(&name[cursor..cursor+label_length]).to_string());
            cursor += label_length;
        }

        labels.join(".")
    } else {
        from_utf8_lossy(name).trim_end_matches('\0').trim_end_matches('.').to_string()
    };

    match decoded.trim().is_empty() {
        true => None,
        false => Some(decoded)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_wire_format_fqdn() {
        let bytes = [0x05, 0x00, 0x00, 0x04, b'h', b'o', b's', b't', 0x03, b'l', b'a', b'n', 0x00];
        assert_eq!(parse_fqdn(&bytes), Some("host.lan".to_string()));
    }

    #[test]
    fn parses_ascii_fqdn() {
        let bytes = [0x01, 0x00, 0x00, b'h', b'o', b's', b't', b'.', b'l', b'a', b'n', b'.'];
        assert_eq!(parse_fqdn(&bytes), Some("host.lan".to_string()));
    }

    #[test]
    fn rejects_empty_fqdn() {
        assert_eq!(parse_fqdn(&[0x00, 0x00, 0x00]), None);
        assert_eq!(parse_fqdn(&[0x00, 0x00, 0x00, 0x00]), None);
    }

    #[test]
    fn formats_client_identifier() {
        assert_eq!(to_hex_string(&[0x01, 0xaa, 0x0b]), "01:aa:0b");
    }
}