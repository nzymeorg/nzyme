use std::collections::{HashMap, HashSet};
use std::sync::MutexGuard;
use chrono::{DateTime, Utc};
use serde::{Serialize, Serializer};
use serde::ser::SerializeMap;
use crate::wired::packets::{Dhcpv4Transaction, Dhcpv6Transaction};

#[derive(Serialize)]
pub struct DhcpTransactionsReport {
    four: Vec<Dhcpv4TransactionReport>,
    six: Vec<Dhcpv6TransactionReport>
}

#[derive(Serialize)]
pub struct Dhcpv6AddressReport {
    pub address: String,
    pub preferred_lifetime: u32,
    pub valid_lifetime: u32
}

#[derive(Serialize)]
pub struct Dhcpv6PrefixReport {
    pub prefix: String,
    pub prefix_length: u8,
    pub preferred_lifetime: u32,
    pub valid_lifetime: u32
}

#[derive(Serialize)]
pub struct Dhcpv6ServerResponseReport {
    pub message_type: String,
    pub server_mac: Option<String>,
    pub server_address: String,
    pub server_duid: Option<String>,
    pub addresses: Vec<Dhcpv6AddressReport>,
    pub prefixes: Vec<Dhcpv6PrefixReport>,
    pub dns_servers: Vec<String>,
    pub domain_search_list: Vec<String>,
    pub preference: Option<u8>,
    pub status_code: Option<u16>,
    pub count: u32,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

#[derive(Serialize)]
pub struct Dhcpv6TransactionReport {
    pub transaction_type: String,
    pub transaction_id: u32,
    pub client_mac: String,
    pub additional_client_macs: HashSet<String>,
    pub server_mac: Option<String>,
    pub additional_server_macs: HashSet<String>,
    pub client_duid: Option<String>,
    pub server_duid: Option<String>,
    pub offered_ip_addresses: HashSet<String>,
    pub requested_ip_address: Option<String>,
    pub addresses: Vec<Dhcpv6AddressReport>,
    pub prefixes: Vec<Dhcpv6PrefixReport>,
    #[serde(serialize_with = "serialize_timestamps")]
    pub timestamps: HashMap<String, Vec<DateTime<Utc>>>,
    pub first_packet: DateTime<Utc>,
    pub latest_packet: DateTime<Utc>,
    pub notes: HashSet<String>,
    pub options: Vec<u16>,
    pub option_request_list: Vec<u16>,
    pub vendor_class: Option<String>,
    pub fqdn: Option<String>,
    pub server_responses: Vec<Dhcpv6ServerResponseReport>,
    pub selected_server_address: Option<String>,
    pub successful: Option<bool>,
    pub complete: bool
}

#[derive(Serialize)]
pub struct Dhcpv4TransactionReport {
    pub transaction_type: String,
    pub transaction_id: u32,
    pub client_mac: String,
    pub additional_client_macs: HashSet<String>,
    pub server_mac: Option<String>,
    pub additional_server_macs: HashSet<String>,
    pub offered_ip_addresses: HashSet<String>,
    pub requested_ip_address: Option<String>,
    #[serde(serialize_with = "serialize_timestamps")] // We need microsecond resolution.
    pub timestamps: HashMap<String, Vec<DateTime<Utc>>>,
    pub first_packet: DateTime<Utc>,
    pub latest_packet: DateTime<Utc>,
    pub notes: HashSet<String>,
    pub options: Vec<u8>,
    pub additional_options: HashSet<Vec<u8>>,
    pub vendor_class: Option<String>,
    pub additional_vendor_classes: HashSet<String>,
    pub parameter_request_list: Vec<u8>,
    pub client_identifier: Option<String>,
    pub hostname: Option<String>,
    pub fqdn: Option<String>,
    pub server_responses: Vec<Dhcpv4ServerResponseReport>,
    pub selected_server_address: Option<String>,
    pub successful: Option<bool>,
    pub complete: bool
}

#[derive(Serialize)]
pub struct Dhcpv4ServerResponseReport {
    pub message_type: String,
    pub server_mac: Option<String>,
    pub server_address: String,
    pub server_identifier: Option<String>,
    pub offered_address: Option<String>,
    pub subnet_mask: Option<String>,
    pub routers: Vec<String>,
    pub dns_servers: Vec<String>,
    pub domain_name: Option<String>,
    pub lease_time_seconds: Option<u32>,
    pub count: u32,
    pub first_seen: DateTime<Utc>,
    pub last_seen: DateTime<Utc>
}

fn serialize_timestamps<S>(
    timestamps: &HashMap<String, Vec<DateTime<Utc>>>,
    serializer: S,
) -> Result<S::Ok, S::Error>
where
    S: Serializer,
{
    let mut map = serializer.serialize_map(Some(timestamps.len()))?;
    for (key, vec) in timestamps {
        let strs: Vec<String> = vec
            .iter()
            .map(|dt| {
                let whole     = dt.format("%Y-%m-%dT%H:%M:%S");
                let frac4     = dt.timestamp_subsec_nanos() / 100_000;
                format!("{}.{:04}Z", whole, frac4)
            })
            .collect();
        map.serialize_entry(key, &strs)?;
    }
    map.end()
}

fn addresses(addresses: &[crate::wired::packets::Dhcpv6Address]) -> Vec<Dhcpv6AddressReport> {
    addresses.iter().map(|a| Dhcpv6AddressReport {
        address: a.address.to_string(),
        preferred_lifetime: a.preferred_lifetime,
        valid_lifetime: a.valid_lifetime
    }).collect()
}

fn prefixes(prefixes: &[crate::wired::packets::Dhcpv6Prefix]) -> Vec<Dhcpv6PrefixReport> {
    prefixes.iter().map(|p| Dhcpv6PrefixReport {
        prefix: p.prefix.to_string(),
        prefix_length: p.prefix_length,
        preferred_lifetime: p.preferred_lifetime,
        valid_lifetime: p.valid_lifetime
    }).collect()
}

fn generate_six(txs: &HashMap<String, Dhcpv6Transaction>) -> Vec<Dhcpv6TransactionReport> {
    txs.values().map(|tx| Dhcpv6TransactionReport {
        transaction_type: tx.transaction_type.to_string(),
        transaction_id: tx.transaction_id,
        client_mac: tx.client_mac.clone(),
        additional_client_macs: tx.additional_client_macs.clone(),
        server_mac: tx.server_mac.clone(),
        additional_server_macs: tx.additional_server_macs.clone(),
        client_duid: tx.client_duid.clone(),
        server_duid: tx.server_duid.clone(),
        offered_ip_addresses: tx.offered_ip_addresses.iter().map(|ip| ip.to_string()).collect(),
        requested_ip_address: tx.requested_ip_address.map(|ip| ip.to_string()),
        addresses: addresses(&tx.addresses),
        prefixes: prefixes(&tx.prefixes),
        timestamps: tx.timestamps.iter().map(|(k, v)| (k.to_string(), v.clone())).collect(),
        first_packet: tx.first_packet,
        latest_packet: tx.latest_packet,
        notes: tx.notes.clone(),
        options: tx.options.clone(),
        option_request_list: tx.option_request_list.clone(),
        vendor_class: tx.vendor_class.clone(),
        fqdn: tx.fqdn.clone(),
        server_responses: tx.server_responses.iter().map(|r| Dhcpv6ServerResponseReport {
            message_type: r.message_type.to_string(),
            server_mac: r.server_mac.clone(),
            server_address: r.server_address.to_string(),
            server_duid: r.server_duid.clone(),
            addresses: addresses(&r.addresses),
            prefixes: prefixes(&r.prefixes),
            dns_servers: r.dns_servers.iter().map(|ip| ip.to_string()).collect(),
            domain_search_list: r.domain_search_list.clone(),
            preference: r.preference,
            status_code: r.status_code,
            count: r.count,
            first_seen: r.first_seen,
            last_seen: r.last_seen
        }).collect(),
        selected_server_address: tx.selected_server_address.map(|ip| ip.to_string()),
        successful: tx.successful,
        complete: tx.complete
    }).collect()
}

pub fn generate(txs: &MutexGuard<HashMap<u32, Dhcpv4Transaction>>,
                txs_v6: &HashMap<String, Dhcpv6Transaction>,
                chunk_size: usize) -> Vec<DhcpTransactionsReport> {
    let mut four: Vec<Dhcpv4TransactionReport> = Vec::new();

    for tx in txs.values() {
        four.push(Dhcpv4TransactionReport {
            transaction_type: tx.transaction_type.to_string(),
            transaction_id: tx.transaction_id,
            client_mac: tx.client_mac.to_string(),
            additional_client_macs: tx.additional_client_macs.clone(),
            server_mac: tx.server_mac.clone(),
            additional_server_macs: tx.additional_server_macs.clone(),
            offered_ip_addresses: tx.offered_ip_addresses.iter().map(|ip| ip.to_string()).collect(),
            requested_ip_address: tx.requested_ip_address.map(|ip| ip.to_string()),
            timestamps: tx.timestamps.iter().map(|(k, v)| (k.to_string(), v.clone())).collect(),
            first_packet: tx.first_packet,
            latest_packet: tx.latest_packet,
            notes: tx.notes.iter().map(|note| note.to_string()).collect(),
            options: tx.options.clone(),
            additional_options: tx.additional_options.clone(),
            vendor_class: tx.vendor_class.clone(),
            additional_vendor_classes: tx.additional_vendor_classes.clone(),
            parameter_request_list: tx.parameter_request_list.clone(),
            client_identifier: tx.client_identifier.clone(),
            hostname: tx.hostname.clone(),
            fqdn: tx.fqdn.clone(),
            server_responses: tx.server_responses.iter().map(|r| Dhcpv4ServerResponseReport {
                message_type: r.message_type.to_string(),
                server_mac: r.server_mac.clone(),
                server_address: r.server_address.to_string(),
                server_identifier: r.server_identifier.map(|ip| ip.to_string()),
                offered_address: r.offered_address.map(|ip| ip.to_string()),
                subnet_mask: r.subnet_mask.map(|ip| ip.to_string()),
                routers: r.routers.iter().map(|ip| ip.to_string()).collect(),
                dns_servers: r.dns_servers.iter().map(|ip| ip.to_string()).collect(),
                domain_name: r.domain_name.clone(),
                lease_time_seconds: r.lease_time_seconds,
                count: r.count,
                first_seen: r.first_seen,
                last_seen: r.last_seen
            }).collect(),
            selected_server_address: tx.selected_server_address.map(|ip| ip.to_string()),
            successful: tx.successful,
            complete: tx.complete,
        })
    }

    let mut reports = Vec::new();
    while !four.is_empty() {
        let rest = four.split_off(four.len().min(chunk_size.max(1)));
        reports.push(DhcpTransactionsReport { four, six: Vec::new() });
        four = rest;
    }

    let mut six = generate_six(txs_v6);
    while !six.is_empty() {
        let rest = six.split_off(six.len().min(chunk_size.max(1)));
        reports.push(DhcpTransactionsReport { four: Vec::new(), six });
        six = rest;
    }

    reports
}