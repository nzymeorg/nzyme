use std::collections::{HashMap, HashSet};
use std::sync::MutexGuard;
use chrono::{DateTime, Utc};
use serde::{Serialize, Serializer};
use serde::ser::SerializeMap;
use crate::wired::packets::Dhcpv4Transaction;

#[derive(Serialize)]
pub struct DhcpTransactionsReport {
    four: Vec<Dhcpv4TransactionReport>
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

pub fn generate(txs: &MutexGuard<HashMap<u32, Dhcpv4Transaction>>) -> DhcpTransactionsReport {
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

    DhcpTransactionsReport { four }
}