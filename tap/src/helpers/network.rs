use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};
use std::str::FromStr;
use anyhow::{bail, Error};
use cidr::{Ipv4Cidr, Ipv6Cidr};
use log::error;
use pnet::datalink::MacAddr;
use serde::Deserialize;

#[derive(Debug, Clone, Deserialize)]
pub enum Nl80211Band {
    Band2GHz,
    Band5GHz,
    Band6GHz,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Channel {
    pub channel: u16,
    pub band: Nl80211Band,
}

impl Channel {
    pub fn from_frequency(f: u32) -> Result<Channel, Error> {
        dot11_frequency_to_channel(f)
    }

    //pub fn to_frequency(self: &Self) -> Result<u16, Error> {
    //    dot11_channel_to_frequency(self.channel, self.band.clone())
    //}

    pub fn is_2g(self: &Self) -> bool {
        match self.band {
            Nl80211Band::Band2GHz => true,
            _ => false
        }
    }

    pub fn is_5g(self: &Self) -> bool {
        match self.band {
            Nl80211Band::Band5GHz => true,
            _ => false
        }
    }

    pub fn is_6g(self: &Self) -> bool {
        match self.band {
            Nl80211Band::Band6GHz => true,
            _ => false
        }
    }
}

pub fn to_mac_address_string(bytes: &[u8]) -> String {
    assert_eq!(bytes.len(), 6);

    format!("{:02X?}:{:02X?}:{:02X?}:{:02X?}:{:02X?}:{:02X?}",
        bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5])
}

pub fn mac_address_from_string(mac_str: &str) -> Option<MacAddr> {
    let parts: Vec<&str> = mac_str.split(':').collect();
    if parts.len() != 6 {
        return None;
    }
    let bytes: Vec<u8> = parts
        .into_iter()
        .filter_map(|part| u8::from_str_radix(part, 16).ok())
        .collect();

    if bytes.len() == 6 {
        Some(MacAddr::new(bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5]))
    } else {
        None
    }
}

pub fn to_ipv4_address_string(bytes: &[u8]) -> String {
    assert_eq!(bytes.len(), 4);

    format!("{}.{}.{}.{}", bytes[0], bytes[1], bytes[2], bytes[3])
}

pub fn to_ipv4_address(bytes: &[u8]) -> IpAddr {
    assert_eq!(bytes.len(), 4);

    let arr: [u8; 4] = bytes.try_into().unwrap();
    return IpAddr::from(Ipv4Addr::from(arr));
}

pub fn to_ipv6_address(bytes: &[u8]) -> IpAddr {
    assert_eq!(bytes.len(), 16);

    let arr: [u8; 16] = bytes.try_into().unwrap();

    // An IPv4-mapped address (::ffff:a.b.c.d) is reported as the IPv4 address it stands for.
    return IpAddr::from(Ipv6Addr::from(arr)).to_canonical();
}

pub fn is_mac_address_multicast(mac: &String) -> bool {
    if mac.len() != 17 {
        error!("Invalid MAC address [{}] cannot determine if it is a multicast address or not.", mac);
        return false;
    }
    
    // Parse the first octet
    let first_octet = match u8::from_str_radix(&mac[0..2], 16) {
        Ok(fo) => fo,
        Err(_) => {
            error!("Invalid MAC address [{}] cannot determine if it is a multicast address or not.", mac);
            return false;
        }
    };
    
    first_octet & 0x01 == 1
}

// this will handle supported frequencies on bands 2, 5 and 6
pub fn dot11_frequency_to_channel(f: u32) -> Result<Channel, Error> {
    // sanity check
    if f < 2412 {
        bail!("Invalid frequency <{}>", f);
    }
    
    if f == 2484 {
        Ok(Channel {
            channel: 14,
            band: Nl80211Band::Band2GHz,
        })
    } else if f == 5935 {
        /* see 802.11ax D6.1 27.3.23.2 and Annex E */
        Ok(Channel {
            channel: 2,
            band: Nl80211Band::Band6GHz,
        })
    } else if f < 2484 {
        Ok(Channel {
            channel: ((f - 2407) / 5) as u16,
            band: Nl80211Band::Band2GHz,
        })
    } else if f >= 4910 && f <= 4980 {
        Ok(Channel {
            channel: ((f - 4000) / 5) as u16,
            band: Nl80211Band::Band5GHz,
        })
    } else if f >= 5035 && f < 5950 {
        Ok(Channel {
            channel: ((f - 5000) / 5) as u16,
            band: Nl80211Band::Band5GHz,
        })
    } else if f >= 5950 && f <= 7115 {
        Ok(Channel {
            channel: ((f - 5950) / 5) as u16,
            band: Nl80211Band::Band6GHz,
        })
    } else {
        bail!("Unknown frequency <{}>", f);
    }
}

pub fn dot11_channel_to_frequency(f: u16, band: Nl80211Band) -> Result<u16, Error> {

    /* see 802.11 17.3.8.3.2 and Annex J
	 * there are overlapping channel numbers in 5GHz and 2GHz bands */
    if f == 0 {
        bail!("Invalid channel <{}>", f);
    }
    match band {
        Nl80211Band::Band2GHz => {
            if f > 14 {
                bail!("Invalid channel <{}>", f);
            }
            if f == 14 {
                return Ok(2484);
            } else if f < 14 {
                return Ok(2407 + f * 5);
            }
        },
        Nl80211Band::Band5GHz => {
            if f < 15 || f > 177 {
                bail!("Invalid channel <{}>", f);
            }
            return if f >= 182 && f <= 196 {
                Ok(4000 + f * 5)
            } else {
                Ok(5000 + f * 5)
            }
        },
        Nl80211Band::Band6GHz => {
            if f < 1 || f > 253 {
                bail!("Invalid channel <{}>", f);
            }
            if f == 2 {
                return Ok(5935);
            } else if f <= 253 {
                return Ok(5950 + f * 5);
            }
        }

    }

    bail!("Unsupported channel <{}>", f);
}

pub fn is_ipv4_address(s: &str) -> bool {
    s.parse::<Ipv4Addr>().is_ok()
}

pub fn is_ipv6_address(s: &str) -> bool {
    s.parse::<Ipv6Addr>().is_ok()
}

pub fn is_ip_address(s: &str) -> bool {
    is_ipv4_address(s) || is_ipv6_address(s)
}

pub fn string_up_to_null_byte(b: &[u8]) -> Option<String> {
    b.iter()
        .position(|&x| x == 0x00)
        .and_then(|index| {
            if index == 0 { 
                None
            } else {
                std::str::from_utf8(&b[..index])
                    .map(|s| s.to_string())
                    .ok()
            }
        })
}

pub fn ip_in_cidr(ip: IpAddr, cidr_str: &str) -> bool {
    match ip {
        IpAddr::V4(ipv4) => {
            if let Ok(cidr) = Ipv4Cidr::from_str(cidr_str) {
                cidr.contains(&ipv4)
            } else {
                false
            }
        }
        IpAddr::V6(ipv6) => {
            if let Ok(cidr) = Ipv6Cidr::from_str(cidr_str) {
                cidr.contains(&ipv6)
            } else {
                false
            }
        }
    }
}

// Uncompressed DNS names, one after the other, padded with zeros.
pub fn parse_domain_names(data: &[u8]) -> Vec<String> {
    let mut names = Vec::new();
    let mut labels: Vec<String> = Vec::new();
    let mut cursor = 0;

    while cursor < data.len() {
        let length = data[cursor] as usize;
        cursor += 1;

        if length == 0 {
            if !labels.is_empty() {
                names.push(labels.join("."));
                labels.clear();
            }
            continue;
        }

        if length > 63 || cursor + length > data.len() {
            break;
        }

        let label = &data[cursor..cursor + length];
        if !label.iter().all(|b| b.is_ascii_alphanumeric() || *b == b'-' || *b == b'_') {
            break;
        }

        labels.push(String::from_utf8_lossy(label).to_string());
        cursor += length;
    }

    names
}
