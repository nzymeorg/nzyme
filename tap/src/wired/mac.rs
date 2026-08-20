use anyhow::{bail, Result};
use rand::prelude::*;
use smoltcp::wire::EthernetAddress;

pub fn resolve(mac_cfg: &str) -> Result<EthernetAddress> {
    if mac_cfg.trim().eq_ignore_ascii_case("random") {
        Ok(random_local_unicast())
    } else {
        parse(mac_cfg)
    }
}

pub fn random_local_unicast() -> EthernetAddress {
    let mut bytes = [0u8; 6];
    rand::rng().fill(&mut bytes[..]);
    bytes[0] = (bytes[0] & 0xFC) | 0x02;
    EthernetAddress(bytes)
}

fn parse(s: &str) -> Result<EthernetAddress> {
    let parts: Vec<&str> = s.split(':').collect();
    if parts.len() != 6 {
        bail!("invalid MAC '{}': expected 6 colon-separated octets", s);
    }
    let mut bytes = [0u8; 6];
    for (i, p) in parts.iter().enumerate() {
        bytes[i] = u8::from_str_radix(p, 16)
            .map_err(|_| anyhow::anyhow!("invalid MAC octet '{}' in '{}'", p, s))?;
    }
    if bytes[0] & 0x01 != 0 {
        bail!("invalid MAC '{}': multicast bit set (first octet is odd)", s);
    }
    Ok(EthernetAddress(bytes))
}