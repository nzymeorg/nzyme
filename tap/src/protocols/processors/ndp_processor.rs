use std::net::IpAddr;
use std::sync::{Arc, Mutex};
use log::error;
use crate::context::context_engine::ContextEngine;
use crate::context::context_source::ContextSource;
use crate::state::state::State;
use crate::state::tables::ndp_table::NdpTable;
use crate::wired::packets::{NdpMessageType, NdpPacket};

pub struct NdpProcessor {
    table: Arc<Mutex<NdpTable>>,
    state: Arc<State>,
    context: Arc<ContextEngine>
}

impl NdpProcessor {

    pub fn new(table: Arc<Mutex<NdpTable>>, state: Arc<State>, context: Arc<ContextEngine>) -> Self {
        Self { table, state, context }
    }

    pub fn process(&mut self, packet: Arc<NdpPacket>) {
        match self.table.lock() {
            Ok(table) => table.register_packet(&packet),
            Err(e) => error!("Could not acquire NDP table mutex: {}", e)
        }

        if let Some((mac, address)) = Self::claim(&packet) {
            self.context.register_mac_address_ip(mac.clone(), address, ContextSource::Ndp);

            match self.state.arp.lock() {
                Ok(mut state) => state.update(mac, address),
                Err(e) => error!("Could not acquire ARP state mutex: {}", e)
            }
        }
    }

    // An advertisement speaks for its target, everything else for its sender. A solicitation from the unspecified
    // address is duplicate address detection: the sender does not have the address yet. Messages that did not arrive
    // with a hop limit of 255 came from off the link and claim nothing.
    fn claim(packet: &NdpPacket) -> Option<(String, IpAddr)> {
        if packet.hop_limit != 255 {
            return None;
        }

        match packet.message_type {
            NdpMessageType::NeighborAdvertisement => {
                let mac = packet.target_link_layer_address.clone().or_else(|| packet.source_mac.clone())?;

                Some((mac, packet.target_address?))
            },
            _ => {
                if packet.source_address.is_unspecified() {
                    return None;
                }

                let mac = packet.source_link_layer_address.clone().or_else(|| packet.source_mac.clone())?;

                Some((mac, packet.source_address))
            }
        }
    }

}

#[cfg(test)]
mod tests {
    use chrono::Utc;
    use super::*;

    fn packet(message_type: NdpMessageType, source: &str, target: Option<&str>) -> NdpPacket {
        NdpPacket {
            source_mac: Some("AA:AA:AA:AA:AA:01".to_string()),
            destination_mac: Some("33:33:00:00:00:01".to_string()),
            source_address: source.parse().unwrap(),
            destination_address: "ff02::1".parse().unwrap(),
            hop_limit: 255,
            message_type,
            target_address: target.map(|t| t.parse().unwrap()),
            source_link_layer_address: None,
            target_link_layer_address: None,
            router: None,
            solicited: None,
            overrides: None,
            router_advertisement: None,
            size: 86,
            timestamp: Utc::now()
        }
    }

    #[test]
    fn advertisement_claims_its_target() {
        let mut p = packet(NdpMessageType::NeighborAdvertisement, "fe80::1", Some("2001:db8::5"));
        assert_eq!(NdpProcessor::claim(&p), Some(("AA:AA:AA:AA:AA:01".to_string(), "2001:db8::5".parse().unwrap())));

        p.target_link_layer_address = Some("BB:BB:BB:BB:BB:05".to_string());
        assert_eq!(NdpProcessor::claim(&p), Some(("BB:BB:BB:BB:BB:05".to_string(), "2001:db8::5".parse().unwrap())));
    }

    #[test]
    fn solicitation_claims_its_sender_but_not_during_duplicate_address_detection() {
        let p = packet(NdpMessageType::NeighborSolicitation, "fe80::1", Some("fe80::9"));
        assert_eq!(NdpProcessor::claim(&p), Some(("AA:AA:AA:AA:AA:01".to_string(), "fe80::1".parse().unwrap())));

        let p = packet(NdpMessageType::NeighborSolicitation, "::", Some("fe80::9"));
        assert_eq!(NdpProcessor::claim(&p), None);
    }

    #[test]
    fn messages_from_off_the_link_claim_nothing() {
        let mut p = packet(NdpMessageType::NeighborAdvertisement, "fe80::1", Some("2001:db8::5"));
        p.hop_limit = 64;
        assert_eq!(NdpProcessor::claim(&p), None);
    }

    #[test]
    fn messages_without_a_mac_address_claim_nothing() {
        let mut p = packet(NdpMessageType::RouterSolicitation, "fe80::1", None);
        p.source_mac = None;
        assert_eq!(NdpProcessor::claim(&p), None);
    }
}
