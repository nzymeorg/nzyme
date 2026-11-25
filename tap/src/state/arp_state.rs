use std::collections::HashMap;
use std::net::IpAddr;
use std::sync::{Arc, Mutex};
use chrono::{DateTime, Duration, Utc};
use log::error;
use crate::metrics::Metrics;

const MAX_MACS: usize = 50_000;
const MAX_ADDRESSES_PER_MAC: usize = 64;

pub struct ArpState {
    mapping: HashMap<String, Vec<ArpIpAddr>>,
    dropped: u64,
    metrics: Arc<Mutex<Metrics>>
}

#[derive(Debug, Clone)]
pub struct ArpIpAddr {
    pub addr: IpAddr,
    pub last_seen: DateTime<Utc>
}

impl ArpState {

    pub fn new(metrics: Arc<Mutex<Metrics>>) -> Self {
        Self {
            mapping: HashMap::new(),
            dropped: 0,
            metrics
        }
    }

    pub fn update(&mut self, mac: String, ip: IpAddr) {
        self.dropped += claim(&mut self.mapping, mac, ip);
    }

    pub fn get_mapping(&self) -> HashMap<String, Vec<ArpIpAddr>> {
        self.mapping.clone()
    }

    pub fn mac_address_of_ip_address(&self, address: IpAddr) -> Option<String> {
        for (mac, vec) in &self.mapping {
            if vec.iter().any(|arp_ip| arp_ip.addr == address) {
                return Some(mac.clone());
            }
        }

        None
    }

    pub fn calculate_metrics(&self) {
        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("state.arp.macs.size", self.mapping.len() as i128);
                metrics.set_gauge(
                    "state.arp.ips.size",
                    self.mapping.values().map(|vec| vec.len() as i128).sum()
                );
                metrics.set_gauge("state.arp.dropped", self.dropped as i128);

            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

    pub fn retention_clean(&mut self) {
        let expiration_time = Utc::now() - Duration::try_seconds(300).unwrap();

        self.mapping.retain(|_, vec| {
            vec.retain(|arp_ip| arp_ip.last_seen > expiration_time);

            // Return true to keep the entry in the map if it still has any ArpIpAddr items.
            !vec.is_empty()
        });
    }

}

// Returns how many claims had to give way: the new one if the table is full, or the oldest of a full MAC address.
fn claim(mapping: &mut HashMap<String, Vec<ArpIpAddr>>, mac: String, ip: IpAddr) -> u64 {
    if !mapping.contains_key(&mac) && mapping.len() >= MAX_MACS {
        return 1;
    }

    let entry = mapping.entry(mac).or_default();

    if let Some(known) = entry.iter_mut().find(|known| known.addr == ip) {
        known.last_seen = Utc::now();
        return 0;
    }

    let mut dropped = 0;
    if entry.len() >= MAX_ADDRESSES_PER_MAC {
        if let Some(oldest) = entry.iter().enumerate().min_by_key(|(_, a)| a.last_seen).map(|(i, _)| i) {
            entry.swap_remove(oldest);
        }
        dropped = 1;
    }

    entry.push(ArpIpAddr { addr: ip, last_seen: Utc::now() });

    dropped
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_mac_address_keeps_a_bounded_number_of_addresses() {
        let mut mapping = HashMap::new();
        let mut dropped = 0;
        for i in 0..(MAX_ADDRESSES_PER_MAC as u32 + 10) {
            dropped += claim(&mut mapping, "AA:AA:AA:AA:AA:01".to_string(), format!("2001:db8::{:x}", i + 1).parse().unwrap());
        }

        assert_eq!(mapping["AA:AA:AA:AA:AA:01"].len(), MAX_ADDRESSES_PER_MAC);
        assert_eq!(dropped, 10);

        let newest: IpAddr = format!("2001:db8::{:x}", MAX_ADDRESSES_PER_MAC + 10).parse().unwrap();
        let oldest: IpAddr = "2001:db8::1".parse().unwrap();
        assert!(mapping["AA:AA:AA:AA:AA:01"].iter().any(|a| a.addr == newest));
        assert!(!mapping["AA:AA:AA:AA:AA:01"].iter().any(|a| a.addr == oldest));
    }

    #[test]
    fn the_number_of_mac_addresses_is_bounded() {
        let mut mapping = HashMap::new();
        let mut dropped = 0;
        for i in 0..(MAX_MACS as u32 + 5) {
            let mac = format!("02:00:{:02X}:{:02X}:{:02X}:{:02X}", i >> 24, (i >> 16) & 0xff, (i >> 8) & 0xff, i & 0xff);
            dropped += claim(&mut mapping, mac, "10.0.0.1".parse().unwrap());
        }

        assert_eq!(mapping.len(), MAX_MACS);
        assert_eq!(dropped, 5);

        assert_eq!(claim(&mut mapping, "02:00:00:00:00:00".to_string(), "10.0.0.2".parse().unwrap()), 0);
        assert_eq!(mapping["02:00:00:00:00:00"].len(), 2);
    }
}
