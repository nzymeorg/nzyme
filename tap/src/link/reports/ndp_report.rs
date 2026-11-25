use chrono::{DateTime, Utc};
use serde::Serialize;
use crate::state::tables::ndp_table::{NdpMessageKey, NdpRouterAdvertisementKey, NdpSummary};

#[derive(Serialize)]
pub struct NdpReport {
    pub messages: Vec<NdpMessageReport>,
    pub router_advertisements: Vec<NdpRouterAdvertisementReport>
}

#[derive(Serialize)]
pub struct NdpMessageReport {
    pub message_type: String,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: String,
    pub destination_address: String,
    pub hop_limit: u8,
    pub target_address: Option<String>,
    pub source_link_layer_address: Option<String>,
    pub target_link_layer_address: Option<String>,
    pub router: Option<bool>,
    pub solicited: Option<bool>,
    pub overrides: Option<bool>,
    pub packet_count: u64,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

#[derive(Serialize)]
pub struct NdpPrefixReport {
    pub prefix: String,
    pub prefix_length: u8,
    pub on_link: bool,
    pub autonomous: bool,
    pub valid_lifetime: u32,
    pub preferred_lifetime: u32
}

#[derive(Serialize)]
pub struct NdpRouteReport {
    pub prefix: String,
    pub prefix_length: u8,
    pub preference: String,
    pub lifetime: u32
}

#[derive(Serialize)]
pub struct NdpRouterAdvertisementReport {
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: String,
    pub destination_address: String,
    pub hop_limit: u8,
    pub source_link_layer_address: Option<String>,
    pub current_hop_limit: u8,
    pub managed: bool,
    pub other_configuration: bool,
    pub preference: String,
    pub router_lifetime: u16,
    pub reachable_time: u32,
    pub retransmit_timer: u32,
    pub mtu: Option<u32>,
    pub prefixes: Vec<NdpPrefixReport>,
    pub routes: Vec<NdpRouteReport>,
    pub dns_servers: Vec<String>,
    pub dns_servers_lifetime: Option<u32>,
    pub dns_search_list: Vec<String>,
    pub packet_count: u64,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

pub fn generate(messages: &[(NdpMessageKey, NdpSummary)],
                router_advertisements: &[(NdpRouterAdvertisementKey, NdpSummary)]) -> NdpReport {
    let messages = messages.iter().map(|(key, summary)| NdpMessageReport {
        message_type: key.message_type.to_string(),
        source_mac: key.source_mac.clone(),
        destination_mac: key.destination_mac.clone(),
        source_address: key.source_address.to_string(),
        destination_address: key.destination_address.to_string(),
        hop_limit: key.hop_limit,
        target_address: key.target_address.map(|a| a.to_string()),
        source_link_layer_address: key.source_link_layer_address.clone(),
        target_link_layer_address: key.target_link_layer_address.clone(),
        router: key.router,
        solicited: key.solicited,
        overrides: key.overrides,
        packet_count: summary.packet_count,
        first_seen: summary.first_seen,
        last_seen: summary.last_seen
    }).collect();

    let router_advertisements = router_advertisements.iter().map(|(key, summary)| {
        let ra = &key.advertisement;

        NdpRouterAdvertisementReport {
            source_mac: key.source_mac.clone(),
            destination_mac: key.destination_mac.clone(),
            source_address: key.source_address.to_string(),
            destination_address: key.destination_address.to_string(),
            hop_limit: key.hop_limit,
            source_link_layer_address: key.source_link_layer_address.clone(),
            current_hop_limit: ra.current_hop_limit,
            managed: ra.managed,
            other_configuration: ra.other_configuration,
            preference: ra.preference.clone(),
            router_lifetime: ra.router_lifetime,
            reachable_time: ra.reachable_time,
            retransmit_timer: ra.retransmit_timer,
            mtu: ra.mtu,
            prefixes: ra.prefixes.iter().map(|p| NdpPrefixReport {
                prefix: p.prefix.to_string(),
                prefix_length: p.prefix_length,
                on_link: p.on_link,
                autonomous: p.autonomous,
                valid_lifetime: p.valid_lifetime,
                preferred_lifetime: p.preferred_lifetime
            }).collect(),
            routes: ra.routes.iter().map(|r| NdpRouteReport {
                prefix: r.prefix.to_string(),
                prefix_length: r.prefix_length,
                preference: r.preference.clone(),
                lifetime: r.lifetime
            }).collect(),
            dns_servers: ra.dns_servers.iter().map(|a| a.to_string()).collect(),
            dns_servers_lifetime: ra.dns_servers_lifetime,
            dns_search_list: ra.dns_search_list.clone(),
            packet_count: summary.packet_count,
            first_seen: summary.first_seen,
            last_seen: summary.last_seen
        }
    }).collect();

    NdpReport { messages, router_advertisements }
}
