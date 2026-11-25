use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicU64, Ordering};
use chrono::{Duration, Utc};
use log::{error, warn};
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::dhcp_transactions_report;
use crate::metrics::Metrics;
use crate::tracemark;
use crate::wired::packets::{
    Dhcp6TransactionType, Dhcpv4Packet, Dhcpv4Transaction, Dhcpv4TransactionNote, Dhcpv6MessageType, Dhcpv6Packet,
    Dhcpv6Transaction
};
use crate::wired::types::{Dhcp4TransactionType, Dhcpv4MessageType};

const MAX_TRANSACTIONS: usize = 20_000;

// A transaction that is still open this long after it started is given up, even if packets keep arriving for it.
const MAX_OPEN_MINUTES: i64 = 10;

const REPORT_CHUNK: usize = 2_000;

#[derive(Default)]
pub struct Dhcpv6Transactions {
    pub transactions: HashMap<String, Dhcpv6Transaction>,
    // Client and transaction ID of every exchange, to the key of the transaction it belongs to.
    exchanges: HashMap<String, String>,
    // The key of the initial transaction of a client that its request continues.
    open_initial: HashMap<String, String>
}

impl Dhcpv6Transactions {
    fn retain_open(&mut self, cutoff: chrono::DateTime<Utc>) {
        let oldest_start = Utc::now() - Duration::minutes(MAX_OPEN_MINUTES);
        self.transactions.retain(|_, tx| {
            !tx.complete && tx.latest_packet >= cutoff && tx.first_packet >= oldest_start
        });

        let transactions = &self.transactions;
        self.exchanges.retain(|_, key| transactions.contains_key(key));
        self.open_initial.retain(|_, key| transactions.contains_key(key));
    }
}

pub struct DhcpTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    metrics: Arc<Mutex<Metrics>>,
    transactions: Mutex<HashMap<u32, Dhcpv4Transaction>>,
    transactions_v6: Mutex<Dhcpv6Transactions>,
    dropped: AtomicU64,
    dropped_v6: AtomicU64
}

impl DhcpTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        DhcpTable {
            leaderlink,
            metrics,
            transactions: Mutex::new(HashMap::new()),
            transactions_v6: Mutex::new(Dhcpv6Transactions::default()),
            dropped: AtomicU64::new(0),
            dropped_v6: AtomicU64::new(0)
        }
    }

    // A DHCPv6 client is known by its DUID, not by a transaction ID: the request that follows a solicitation has an
    // ID of its own. Server messages and the request are matched to the open transaction of that client.
    pub fn register_dhcpv6_packet(&self, dhcp: &Arc<Dhcpv6Packet>) {
        match self.transactions_v6.lock() {
            Ok(mut txs) => {
                if !Self::register_dhcpv6(&mut txs, dhcp) {
                    self.dropped_v6.fetch_add(1, Ordering::Relaxed);
                }
            },
            Err(e) => error!("Could not acquire DHCPv6 transactions table: {}", e)
        }
    }

    // False if the table is full and the transaction was not recorded.
    fn register_dhcpv6(txs: &mut Dhcpv6Transactions, dhcp: &Dhcpv6Packet) -> bool {
        let client_mac = if dhcp.relayed || !dhcp.message_type.is_from_client() {
            dhcp.client_link_layer_address.clone()
        } else {
            dhcp.source_mac.clone().or_else(|| dhcp.client_link_layer_address.clone())
        };

        let client_key = match (&dhcp.client_duid, &client_mac) {
            (Some(duid), _) => duid.clone(),
            (None, Some(mac)) => format!("mac:{}", mac),
            (None, None) => return true
        };

        let exchange = format!("{}:{}", client_key, dhcp.transaction_id);

        let key = txs.exchanges.get(&exchange).cloned().or_else(|| {
            if dhcp.message_type == Dhcpv6MessageType::Request {
                txs.open_initial.get(&client_key).cloned()
            } else {
                None
            }
        });

        let existing = key.as_ref()
            .and_then(|key| txs.transactions.get_mut(key))
            .filter(|tx| !tx.complete);

        match existing {
            Some(tx) => {
                Self::update_dhcpv6_transaction(tx, dhcp);

                if let Some(key) = key && tx.transaction_ids.contains(&dhcp.transaction_id) {
                    txs.exchanges.insert(exchange, key);
                }
            },
            None => {
                let transaction_type = match dhcp.message_type {
                    Dhcpv6MessageType::Solicit => Dhcp6TransactionType::Initial,
                    Dhcpv6MessageType::Renew => Dhcp6TransactionType::Renew,
                    Dhcpv6MessageType::Rebind => Dhcp6TransactionType::Rebind,
                    Dhcpv6MessageType::Confirm => Dhcp6TransactionType::Confirm,
                    Dhcpv6MessageType::Release => Dhcp6TransactionType::Release,
                    Dhcpv6MessageType::Decline => Dhcp6TransactionType::Decline,
                    Dhcpv6MessageType::InformationRequest => Dhcp6TransactionType::Inform,
                    _ => {
                        tracemark!("DHCPv6 message is not the start of a transaction. Skipping.");
                        return true;
                    }
                };

                let Some(client_mac) = client_mac else { return true; };

                if txs.transactions.len() >= MAX_TRANSACTIONS {
                    return false;
                }

                let mut notes = HashSet::new();
                if dhcp.relayed {
                    notes.insert("Relayed".to_string());
                }
                if dhcp.rapid_commit {
                    notes.insert("RapidCommit".to_string());
                }

                let mut timestamps = HashMap::new();
                timestamps.insert(dhcp.message_type, vec![dhcp.timestamp]);

                let tx = Dhcpv6Transaction {
                    transaction_type,
                    transaction_id: dhcp.transaction_id,
                    transaction_ids: HashSet::from([dhcp.transaction_id]),
                    client_key: client_key.clone(),
                    client_mac,
                    additional_client_macs: HashSet::new(),
                    server_mac: None,
                    additional_server_macs: HashSet::new(),
                    client_duid: dhcp.client_duid.clone(),
                    server_duid: dhcp.server_duid.clone(),
                    offered_ip_addresses: HashSet::new(),
                    requested_ip_address: dhcp.addresses.first().map(|a| a.address),
                    addresses: Vec::new(),
                    prefixes: Vec::new(),
                    timestamps,
                    first_packet: dhcp.timestamp,
                    latest_packet: dhcp.timestamp,
                    notes,
                    options: dhcp.options.clone(),
                    option_request_list: dhcp.option_request_list.clone(),
                    vendor_class: dhcp.vendor_class.clone(),
                    fqdn: dhcp.fqdn.clone(),
                    server_responses: Vec::new(),
                    selected_server_address: None,
                    successful: None,
                    complete: false
                };

                if transaction_type == Dhcp6TransactionType::Initial {
                    txs.open_initial.insert(client_key, exchange.clone());
                }

                txs.exchanges.insert(exchange.clone(), exchange.clone());
                txs.transactions.insert(exchange, tx);
            }
        }

        true
    }

    fn update_dhcpv6_transaction(tx: &mut Dhcpv6Transaction, dhcp: &Dhcpv6Packet) {
        tx.record_timestamp(dhcp.message_type, dhcp.timestamp);

        match dhcp.message_type {
            Dhcpv6MessageType::Advertise => {
                tx.record_server_mac(&dhcp.source_mac);
                tx.record_server_response(dhcp);
                for address in &dhcp.addresses {
                    if tx.offered_ip_addresses.len() >= Dhcpv6Transaction::MAX_SET_ENTRIES {
                        break;
                    }
                    tx.offered_ip_addresses.insert(address.address);
                }
            },
            Dhcpv6MessageType::Request => {
                if tx.transaction_ids.len() < Dhcpv6Transaction::MAX_SET_ENTRIES {
                    tx.transaction_ids.insert(dhcp.transaction_id);
                }
                tx.server_duid = dhcp.server_duid.clone();
                if let Some(address) = dhcp.addresses.first() {
                    tx.requested_ip_address = Some(address.address);
                }

                // The request names the chosen server by its DUID. Its address is the one that advertised with it.
                tx.selected_server_address = tx.server_responses.iter()
                    .find(|r| r.server_duid.is_some() && r.server_duid == dhcp.server_duid)
                    .map(|r| r.server_address);
            },
            Dhcpv6MessageType::Reply => {
                // A reply to a solicitation only ends the transaction if the server took the rapid commit.
                if tx.transaction_type == Dhcp6TransactionType::Initial
                    && !tx.timestamps.contains_key(&Dhcpv6MessageType::Request)
                    && !dhcp.rapid_commit {
                    return;
                }

                tx.record_server_mac(&dhcp.source_mac);
                tx.record_server_response(dhcp);
                tx.addresses = dhcp.addresses.clone();
                tx.prefixes = dhcp.prefixes.clone();

                if tx.server_duid.is_none() {
                    tx.server_duid = dhcp.server_duid.clone();
                }
                if tx.selected_server_address.is_none() {
                    tx.selected_server_address = Some(dhcp.source_address);
                }

                let assigns = matches!(tx.transaction_type,
                    Dhcp6TransactionType::Initial | Dhcp6TransactionType::Renew | Dhcp6TransactionType::Rebind);
                let status_ok = dhcp.status_code.unwrap_or(0) == 0;

                if let Some(code) = dhcp.status_code.filter(|code| *code != 0) {
                    tx.notes.insert(format!("StatusCode{}", code));
                }

                tx.successful = Some(status_ok
                    && (!assigns || !dhcp.addresses.is_empty() || !dhcp.prefixes.is_empty()));
                tx.complete = true;
            },
            // A retransmission of the message that started the transaction.
            _ => {}
        }
    }

    pub fn register_dhcpv4_packet(&self, dhcp: Arc<Dhcpv4Packet>) {
        match self.transactions.lock() {
            Ok(mut txs) => {
                if !Self::register_dhcpv4(&mut txs, &dhcp) {
                    self.dropped.fetch_add(1, Ordering::Relaxed);
                }
            },
            Err(e) => error!("Could not acquire DHCPv4 transactions table: {}", e)
        }
    }

    // False if the table is full and the transaction was not recorded.
    fn register_dhcpv4(txs: &mut HashMap<u32, Dhcpv4Transaction>, dhcp: &Dhcpv4Packet) -> bool {
        match txs.get_mut(&dhcp.transaction_id) {
            Some(tx) => {
                // Existing transaction. Update.
                tracemark!("New packet for existing DHCP transaction <{}>: {:?}",
                    dhcp.transaction_id, tx);

                match tx.transaction_type {
                    Dhcp4TransactionType::Initial |
                    Dhcp4TransactionType::Renew |
                    Dhcp4TransactionType::Reboot |
                    Dhcp4TransactionType::Rebind => {
                        match dhcp.message_type {
                            Dhcpv4MessageType::Offer => {
                                // DHCP server responded with an offer.
                                tx.latest_packet = dhcp.timestamp;
                                tx.record_timestamp(Dhcpv4MessageType::Offer, dhcp.timestamp);
                                tx.record_server_mac(dhcp.source_mac.clone());
                                tx.record_server_response(&dhcp);

                                match dhcp.assigned_address {
                                    Some(address) => {
                                        if tx.offered_ip_addresses.len() < Dhcpv4Transaction::MAX_SET_ENTRIES {
                                            tx.offered_ip_addresses.insert(address);
                                        }
                                    },
                                    None => {
                                        warn!("DHCP offer in transaction <{}> includes no \
                                        offered IP address.", dhcp.transaction_id);
                                        tx.notes.insert(Dhcpv4TransactionNote::OfferNoYiaddr);
                                    }
                                }
                            }
                            Dhcpv4MessageType::Request => {
                                // Client requests IP based on previous offer.
                                tx.latest_packet = dhcp.timestamp;
                                tx.record_timestamp(Dhcpv4MessageType::Request, dhcp.timestamp);
                                tx.record_options(dhcp.options.clone());
                                tx.record_client_identity(&dhcp);

                                let requested_ip_address = match (dhcp.dhcp_client_address,
                                                                  dhcp.requested_ip_address) {
                                    (Some(_), Some(_)) => dhcp.requested_ip_address,
                                    (Some(_), None) => dhcp.dhcp_client_address,
                                    (None, Some(_)) => dhcp.requested_ip_address,
                                    (None, None) => None
                                };

                                tx.requested_ip_address = requested_ip_address;

                                // The server whose offer the client accepts.
                                if dhcp.server_identifier.is_some() {
                                    tx.selected_server_address = dhcp.server_identifier;
                                }

                                tx.record_client_mac(dhcp.source_mac.clone());
                                tx.record_server_mac(dhcp.destination_mac.clone()); }
                            Dhcpv4MessageType::Ack => {
                                // Server confirmed the lease. Transaction complete.
                                tx.latest_packet = dhcp.timestamp;
                                tx.record_timestamp(Dhcpv4MessageType::Ack, dhcp.timestamp);
                                tx.record_server_mac(dhcp.source_mac.clone());
                                tx.record_server_response(&dhcp);
                                tx.successful = Some(true);
                                tx.complete = true;
                            }
                            Dhcpv4MessageType::Nack => {
                                // Server declined the lease. Transaction complete.
                                tx.latest_packet = dhcp.timestamp;
                                tx.record_timestamp(Dhcpv4MessageType::Nack, dhcp.timestamp);
                                tx.record_server_mac(dhcp.source_mac.clone());
                                tx.successful = Some(false);
                                tx.complete = true;
                            }
                            Dhcpv4MessageType::Decline => {
                                // Client declined the lease. Transaction complete.
                                tx.latest_packet = dhcp.timestamp;
                                tx.record_timestamp(Dhcpv4MessageType::Decline, dhcp.timestamp);
                                tx.record_client_mac(dhcp.source_mac.clone());
                                tx.record_server_mac(dhcp.destination_mac.clone());
                                tx.successful = Some(false);
                                tx.complete = true;
                            }
                            _ => {
                                warn!("Unexpected DHCP packet for existing transaction: \
                                    {:?} (TX: {:?})", dhcp, tx)
                            }
                        }
                    }
                    _ => {
                        // We ignore some types that would never make it here.
                    }
                }
            },
            None => {
                // We have not seen this transaction before.

                /*
                 * Only create a new transaction if this packet is an initial packet.
                 * We prefer missing a transaction over an incomplete one. Incomplete
                 * transactions could happen if the tap starts recording in the middle
                 * of an ongoing transaction or if we have packet loss or some sort
                 * of capture sampling enabled on another layer.
                 */

                let broadcast_mac = "FF:FF:FF:FF:FF:FF";

                let (tx_type, server_mac, requested_ip_address, options) = match dhcp.message_type {
                    Dhcpv4MessageType::Discover => (Dhcp4TransactionType::Initial, None, None, None),
                    Dhcpv4MessageType::Release  => (Dhcp4TransactionType::Release, None, None, None),
                    Dhcpv4MessageType::Inform   => (Dhcp4TransactionType::Inform, None, None, None),

                    Dhcpv4MessageType::Request => {
                        // Match ciaddr (None == 0.0.0.0) and destination_mac (Option<String>)
                        match (dhcp.dhcp_client_address, dhcp.destination_mac.as_deref()) {
                            // INIT-REBOOT: no ciaddr, broadcast, with requested IP.
                            (None, Some(dest)) if dest.eq(broadcast_mac)
                                && dhcp.requested_ip_address.is_some() =>
                                (Dhcp4TransactionType::Reboot, None, dhcp.requested_ip_address, Some(dhcp.options.clone())),

                            // RENEW: has ciaddr, unicast (dest != broadcast).
                            (Some(_), Some(dest)) if !dest.eq(broadcast_mac) =>
                                (Dhcp4TransactionType::Renew, Some(dest.to_string()), dhcp.dhcp_client_address, Some(dhcp.options.clone())),

                            // REBIND: has ciaddr, broadcast.
                            (Some(_), Some(dest)) if dest.eq(broadcast_mac) =>
                                (Dhcp4TransactionType::Rebind, None,  dhcp.dhcp_client_address, Some(dhcp.options.clone())),

                            // Any other combination is not a recognized new-txn packet.
                            _ => (Dhcp4TransactionType::Unknown, None, None, None)
                        }
                    }

                    _ => (Dhcp4TransactionType::Unknown, None, None, None)
                };

                if tx_type == Dhcp4TransactionType::Unknown {
                    tracemark!("DHCP transaction <{}> is not a new transaction. Skipping.",
                        dhcp.transaction_id);
                    return true;
                }

                if txs.len() >= MAX_TRANSACTIONS {
                    return false;
                }

                // Release is terminal with no more messages in transaction.
                let complete = tx_type == Dhcp4TransactionType::Release;
                let successful = match tx_type == Dhcp4TransactionType::Release {
                    true => Some(true),
                    false => None
                };

                let mut timestamps = HashMap::new();
                timestamps.insert(dhcp.message_type.clone(), vec![dhcp.timestamp]);

                let mut tx = Dhcpv4Transaction {
                    transaction_type: tx_type,
                    transaction_id: dhcp.transaction_id,
                    client_mac: dhcp.client_mac_address.clone(),
                    additional_client_macs: HashSet::new(),
                    server_mac,
                    additional_server_macs: HashSet::new(),
                    offered_ip_addresses: HashSet::new(),
                    requested_ip_address,
                    timestamps,
                    first_packet: dhcp.timestamp,
                    latest_packet: dhcp.timestamp,
                    notes: HashSet::new(),
                    options: options.unwrap_or_else(Vec::new),
                    additional_options: HashSet::new(),
                    vendor_class: dhcp.vendor_class.clone(),
                    additional_vendor_classes: HashSet::new(),
                    parameter_request_list: Vec::new(),
                    client_identifier: None,
                    hostname: None,
                    fqdn: None,
                    server_responses: Vec::new(),
                    selected_server_address: dhcp.server_identifier,
                    successful,
                    complete
                };

                // All new-transaction packets are client messages.
                tx.record_client_identity(&dhcp);

                tracemark!("New DHCP transaction started: {:?}", tx);

                txs.insert(dhcp.transaction_id, tx);
            }
        }

        true
    }

    pub fn process_report(&self) {
        let incomplete_cutoff = Utc::now() - Duration::minutes(1);
        match self.transactions.lock() {
            Ok(mut txs) => {
                // Generate JSON.
                let mut timer = Timer::new();
                let mut txs_v6 = match self.transactions_v6.lock() {
                    Ok(txs_v6) => txs_v6,
                    Err(e) => {
                        error!("Could not acquire DHCPv6 transactions table: {}", e);
                        return;
                    }
                };

                // DHCPv4 and DHCPv6 go in separate reports, in chunks: a flood of made-up clients of one
                // must not make the report too large for the node and take the other down with it.
                let mut reports = Vec::new();
                for report in dhcp_transactions_report::generate(&txs, &txs_v6.transactions, REPORT_CHUNK) {
                    match serde_json::to_string(&report) {
                        Ok(report) => reports.push(report),
                        Err(e) => error!("Could not serialize DHCP transactions report: {}", e)
                    }
                }
                timer.stop();
                record_timer(
                    timer.elapsed_microseconds(),
                    "tables.dhcp.timer.report_generation",
                    &self.metrics
                );

                match self.leaderlink.lock() {
                    Ok(link) => {
                        for report in reports {
                            if let Err(e) = link.send_report("dhcp/transactions", report) {
                                error!("Could not submit DHCP transactions report: {}", e);
                            }
                        }
                    },
                    Err(e) => error!("Could not acquire leader link lock for DHCP transactions \
                        report submission: {}", e)
                }

                // Clean up completed or expired transactions.
                let oldest_start = Utc::now() - Duration::minutes(MAX_OPEN_MINUTES);
                txs.retain(|_, tx| {
                    !tx.complete && tx.latest_packet >= incomplete_cutoff && tx.first_packet >= oldest_start
                });
                txs_v6.retain_open(incomplete_cutoff);
            },
            Err(e) => error!("Could not acquire DHCP table lock: {}", e)
        }
    }

    pub fn calculate_metrics(&self) {
        let table_size: i128 = match self.transactions.lock() {
            Ok(d) => d.len() as i128,
            Err(e) => {
                error!("Could not acquire mutex to calculate DHCP transactions table size: {}", e);

                -1
            }
        };

        let table_size_v6: i128 = self.transactions_v6.lock().map(|txs| txs.transactions.len() as i128).unwrap_or(-1);

        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("tables.dhcp.transactions.size", table_size);
                metrics.set_gauge("tables.dhcp.transactions.dropped", self.dropped.load(Ordering::Relaxed) as i128);
                metrics.set_gauge("tables.dhcp.transactions_v6.size", table_size_v6);
                metrics.set_gauge("tables.dhcp.transactions_v6.dropped",
                    self.dropped_v6.load(Ordering::Relaxed) as i128);
            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

}

#[cfg(test)]
mod tests {
    use std::net::IpAddr;
    use super::*;
    use crate::wired::packets::Dhcpv6Address;
    use crate::wired::types::{Dhcpv4OpCode, HardwareType};

    const CLIENT: &str = "AA:AA:AA:AA:AA:01";
    const SERVER: &str = "BB:BB:BB:BB:BB:02";

    fn packet(message_type: Dhcpv6MessageType, transaction_id: u32) -> Dhcpv6Packet {
        let from_client = message_type.is_from_client();

        Dhcpv6Packet {
            source_mac: Some(if from_client { CLIENT } else { SERVER }.to_string()),
            destination_mac: None,
            source_address: if from_client { "fe80::5" } else { "fe80::1" }.parse().unwrap(),
            destination_address: "ff02::1:2".parse().unwrap(),
            message_type,
            transaction_id,
            relayed: false,
            client_duid: Some("0001aa".to_string()),
            server_duid: if from_client { None } else { Some("0003bb".to_string()) },
            client_link_layer_address: None,
            addresses: Vec::new(),
            prefixes: Vec::new(),
            options: vec![1, 6, 3],
            option_request_list: vec![23, 24],
            vendor_class: None,
            fqdn: None,
            dns_servers: Vec::new(),
            domain_search_list: Vec::new(),
            preference: None,
            rapid_commit: false,
            status_code: None,
            size: 120,
            timestamp: Utc::now()
        }
    }

    fn with_address(mut packet: Dhcpv6Packet) -> Dhcpv6Packet {
        packet.addresses = vec![Dhcpv6Address {
            address: "2001:db8::100".parse().unwrap(),
            preferred_lifetime: 3600,
            valid_lifetime: 7200
        }];
        packet
    }

    fn only(txs: &Dhcpv6Transactions) -> &Dhcpv6Transaction {
        assert_eq!(txs.transactions.len(), 1);
        txs.transactions.values().next().unwrap()
    }

    #[test]
    fn four_message_exchange_is_one_transaction() {
        let mut txs = Dhcpv6Transactions::default();

        DhcpTable::register_dhcpv6(&mut txs, &packet(Dhcpv6MessageType::Solicit, 1));
        DhcpTable::register_dhcpv6(&mut txs, &with_address(packet(Dhcpv6MessageType::Advertise, 1)));
        assert!(!only(&txs).complete);

        let mut request = with_address(packet(Dhcpv6MessageType::Request, 2));
        request.server_duid = Some("0003bb".to_string());
        DhcpTable::register_dhcpv6(&mut txs, &request);
        DhcpTable::register_dhcpv6(&mut txs, &with_address(packet(Dhcpv6MessageType::Reply, 2)));

        let tx = only(&txs);
        assert_eq!(tx.transaction_type, Dhcp6TransactionType::Initial);
        assert_eq!(tx.transaction_id, 1);
        assert_eq!(tx.client_mac, CLIENT);
        assert_eq!(tx.server_mac, Some(SERVER.to_string()));
        assert_eq!(tx.server_duid, Some("0003bb".to_string()));
        assert_eq!(tx.selected_server_address, Some("fe80::1".parse().unwrap()));
        assert_eq!(tx.requested_ip_address, Some("2001:db8::100".parse::<IpAddr>().unwrap()));
        assert_eq!(tx.offered_ip_addresses.len(), 1);
        assert_eq!(tx.addresses.len(), 1);
        assert_eq!(tx.server_responses.len(), 2);
        assert_eq!(tx.successful, Some(true));
        assert!(tx.complete);
    }

    #[test]
    fn reply_to_a_solicitation_only_completes_with_rapid_commit() {
        let mut txs = Dhcpv6Transactions::default();
        DhcpTable::register_dhcpv6(&mut txs, &packet(Dhcpv6MessageType::Solicit, 1));
        DhcpTable::register_dhcpv6(&mut txs, &with_address(packet(Dhcpv6MessageType::Reply, 1)));
        assert!(!only(&txs).complete);

        let mut txs = Dhcpv6Transactions::default();
        let mut solicit = packet(Dhcpv6MessageType::Solicit, 1);
        solicit.rapid_commit = true;
        let mut reply = with_address(packet(Dhcpv6MessageType::Reply, 1));
        reply.rapid_commit = true;
        DhcpTable::register_dhcpv6(&mut txs, &solicit);
        DhcpTable::register_dhcpv6(&mut txs, &reply);

        let tx = only(&txs);
        assert!(tx.complete);
        assert_eq!(tx.successful, Some(true));
        assert!(tx.notes.contains("RapidCommit"));
    }

    #[test]
    fn server_without_addresses_fails_the_transaction() {
        let mut txs = Dhcpv6Transactions::default();
        DhcpTable::register_dhcpv6(&mut txs, &packet(Dhcpv6MessageType::Renew, 7));

        let mut reply = packet(Dhcpv6MessageType::Reply, 7);
        reply.status_code = Some(2);
        DhcpTable::register_dhcpv6(&mut txs, &reply);

        let tx = only(&txs);
        assert_eq!(tx.transaction_type, Dhcp6TransactionType::Renew);
        assert_eq!(tx.successful, Some(false));
        assert!(tx.notes.contains("StatusCode2"));
    }

    #[test]
    fn information_request_needs_no_address_to_succeed() {
        let mut txs = Dhcpv6Transactions::default();
        let mut request = packet(Dhcpv6MessageType::InformationRequest, 9);
        request.client_duid = None;
        let mut reply = packet(Dhcpv6MessageType::Reply, 9);
        reply.client_duid = None;
        reply.client_link_layer_address = Some(CLIENT.to_string());

        DhcpTable::register_dhcpv6(&mut txs, &request);
        DhcpTable::register_dhcpv6(&mut txs, &reply);

        let tx = only(&txs);
        assert_eq!(tx.transaction_type, Dhcp6TransactionType::Inform);
        assert_eq!(tx.successful, Some(true));
    }

    #[test]
    fn messages_without_the_start_of_their_transaction_are_not_recorded() {
        let mut txs = Dhcpv6Transactions::default();
        DhcpTable::register_dhcpv6(&mut txs, &with_address(packet(Dhcpv6MessageType::Request, 2)));
        DhcpTable::register_dhcpv6(&mut txs, &with_address(packet(Dhcpv6MessageType::Reply, 2)));
        DhcpTable::register_dhcpv6(&mut txs, &with_address(packet(Dhcpv6MessageType::Advertise, 3)));
        assert!(txs.transactions.is_empty());
    }

    #[test]
    fn retransmissions_and_other_clients() {
        let mut txs = Dhcpv6Transactions::default();
        DhcpTable::register_dhcpv6(&mut txs, &packet(Dhcpv6MessageType::Solicit, 1));
        DhcpTable::register_dhcpv6(&mut txs, &packet(Dhcpv6MessageType::Solicit, 1));
        assert_eq!(only(&txs).timestamps[&Dhcpv6MessageType::Solicit].len(), 2);

        let mut other = packet(Dhcpv6MessageType::Solicit, 1);
        other.client_duid = Some("0001cc".to_string());
        DhcpTable::register_dhcpv6(&mut txs, &other);
        assert_eq!(txs.transactions.len(), 2);

        let mut advertise = with_address(packet(Dhcpv6MessageType::Advertise, 1));
        advertise.client_duid = Some("0001cc".to_string());
        DhcpTable::register_dhcpv6(&mut txs, &advertise);
        assert_eq!(txs.transactions.values().filter(|tx| !tx.server_responses.is_empty()).count(), 1);
    }

    #[test]
    fn relayed_message_needs_a_known_client() {
        let mut txs = Dhcpv6Transactions::default();
        let mut solicit = packet(Dhcpv6MessageType::Solicit, 1);
        solicit.relayed = true;
        DhcpTable::register_dhcpv6(&mut txs, &solicit);
        assert!(txs.transactions.is_empty());

        solicit.client_link_layer_address = Some("CC:CC:CC:CC:CC:03".to_string());
        DhcpTable::register_dhcpv6(&mut txs, &solicit);
        let tx = only(&txs);
        assert_eq!(tx.client_mac, "CC:CC:CC:CC:CC:03");
        assert!(tx.notes.contains("Relayed"));
    }

    fn packet_v4(message_type: Dhcpv4MessageType, transaction_id: u32) -> Dhcpv4Packet {
        let from_client = matches!(message_type, Dhcpv4MessageType::Discover | Dhcpv4MessageType::Request);

        Dhcpv4Packet {
            timestamp: Utc::now(),
            source_mac: Some(if from_client { CLIENT } else { SERVER }.to_string()),
            destination_mac: Some("FF:FF:FF:FF:FF:FF".to_string()),
            source_address: if from_client { "0.0.0.0" } else { "10.0.0.1" }.parse().unwrap(),
            destination_address: "255.255.255.255".parse().unwrap(),
            source_port: if from_client { 68 } else { 67 },
            destination_port: if from_client { 67 } else { 68 },
            op_code: if from_client { Dhcpv4OpCode::Request } else { Dhcpv4OpCode::Reply },
            hardware_type: HardwareType::Ethernet,
            transaction_id,
            seconds_elapsed: 0,
            dhcp_client_address: None,
            assigned_address: if from_client { None } else { Some("10.0.0.50".parse().unwrap()) },
            client_mac_address: CLIENT.to_string(),
            options: Vec::new(),
            vendor_class: None,
            message_type,
            requested_ip_address: None,
            hostname: None,
            parameter_request_list: Vec::new(),
            client_identifier: None,
            fqdn: None,
            server_identifier: None,
            subnet_mask: None,
            routers: Vec::new(),
            dns_servers: Vec::new(),
            domain_name: None,
            lease_time_seconds: None
        }
    }

    #[test]
    fn full_v4_table_drops_new_transactions() {
        let mut txs = HashMap::new();
        for i in 0..MAX_TRANSACTIONS as u32 {
            assert!(DhcpTable::register_dhcpv4(&mut txs, &packet_v4(Dhcpv4MessageType::Discover, i)));
        }

        assert!(!DhcpTable::register_dhcpv4(&mut txs, &packet_v4(Dhcpv4MessageType::Discover, u32::MAX)));
        assert_eq!(txs.len(), MAX_TRANSACTIONS);

        assert!(DhcpTable::register_dhcpv4(&mut txs, &packet_v4(Dhcpv4MessageType::Offer, 7)));
        assert_eq!(txs[&7].server_responses.len(), 1);
    }

    #[test]
    fn a_v4_transaction_answered_by_a_flood_of_servers_stays_bounded() {
        let mut txs = HashMap::new();
        assert!(DhcpTable::register_dhcpv4(&mut txs, &packet_v4(Dhcpv4MessageType::Discover, 1)));

        for i in 0..1_000u32 {
            let mut offer = packet_v4(Dhcpv4MessageType::Offer, 1);
            offer.source_mac = Some(format!("02:00:00:00:{:02X}:{:02X}", i / 256, i % 256));
            offer.source_address = format!("10.9.{}.{}", i / 256, i % 256).parse().unwrap();
            offer.assigned_address = Some(format!("10.8.{}.{}", i / 256, i % 256).parse().unwrap());
            assert!(DhcpTable::register_dhcpv4(&mut txs, &offer));
        }

        let tx = &txs[&1];
        assert_eq!(tx.server_responses.len(), Dhcpv4Transaction::MAX_SET_ENTRIES);
        assert_eq!(tx.offered_ip_addresses.len(), Dhcpv4Transaction::MAX_SET_ENTRIES);
        assert_eq!(tx.additional_server_macs.len(), Dhcpv4Transaction::MAX_SET_ENTRIES);
        assert_eq!(tx.timestamps[&Dhcpv4MessageType::Offer].len(), Dhcpv6Transaction::MAX_TIMESTAMPS);

        let mut offer = packet_v4(Dhcpv4MessageType::Offer, 1);
        offer.source_mac = Some("02:00:00:00:00:00".to_string());
        offer.source_address = "10.9.0.0".parse().unwrap();
        offer.assigned_address = Some("10.8.0.0".parse().unwrap());
        assert!(DhcpTable::register_dhcpv4(&mut txs, &offer));
        assert_eq!(txs[&1].server_responses[0].count, 2);
    }

    #[test]
    fn full_table_drops_new_transactions() {
        let mut txs = Dhcpv6Transactions::default();
        for i in 0..MAX_TRANSACTIONS as u32 {
            let mut solicit = packet(Dhcpv6MessageType::Solicit, 1);
            solicit.client_duid = Some(format!("{:08x}", i));
            assert!(DhcpTable::register_dhcpv6(&mut txs, &solicit));
        }

        let mut solicit = packet(Dhcpv6MessageType::Solicit, 1);
        solicit.client_duid = Some("ffffffffff".to_string());
        assert!(!DhcpTable::register_dhcpv6(&mut txs, &solicit));
        assert_eq!(txs.transactions.len(), MAX_TRANSACTIONS);
    }

    #[test]
    fn a_finished_transaction_is_not_continued_and_indexes_are_cleaned() {
        let mut txs = Dhcpv6Transactions::default();
        DhcpTable::register_dhcpv6(&mut txs, &packet(Dhcpv6MessageType::Renew, 7));
        DhcpTable::register_dhcpv6(&mut txs, &with_address(packet(Dhcpv6MessageType::Reply, 7)));
        assert!(only(&txs).complete);

        DhcpTable::register_dhcpv6(&mut txs, &packet(Dhcpv6MessageType::Renew, 8));
        assert_eq!(txs.transactions.len(), 2);

        txs.retain_open(Utc::now() - Duration::minutes(1));
        assert_eq!(txs.transactions.len(), 1);
        assert_eq!(txs.exchanges.len(), 1);

        txs.retain_open(Utc::now() + Duration::minutes(1));
        assert!(txs.transactions.is_empty());
        assert!(txs.exchanges.is_empty());
        assert!(txs.open_initial.is_empty());
    }

    #[test]
    fn a_flood_of_answers_does_not_grow_a_transaction() {
        let mut txs = Dhcpv6Transactions::default();
        DhcpTable::register_dhcpv6(&mut txs, &packet(Dhcpv6MessageType::Solicit, 1));

        for i in 0..1000u32 {
            let mut advertise = with_address(packet(Dhcpv6MessageType::Advertise, 1));
            advertise.addresses[0].address = format!("2001:db8::{:x}", i + 1).parse().unwrap();
            advertise.source_mac = Some(format!("BB:BB:BB:BB:{:02X}:{:02X}", i / 256, i % 256));
            DhcpTable::register_dhcpv6(&mut txs, &advertise);

            DhcpTable::register_dhcpv6(&mut txs, &packet(Dhcpv6MessageType::Request, 100 + i));
        }

        let tx = only(&txs);
        assert_eq!(tx.offered_ip_addresses.len(), Dhcpv6Transaction::MAX_SET_ENTRIES);
        assert_eq!(tx.additional_server_macs.len(), Dhcpv6Transaction::MAX_SET_ENTRIES);
        assert_eq!(tx.transaction_ids.len(), Dhcpv6Transaction::MAX_SET_ENTRIES);
        assert_eq!(txs.exchanges.len(), Dhcpv6Transaction::MAX_SET_ENTRIES);
    }

    #[test]
    fn a_transaction_that_stays_open_too_long_is_given_up() {
        let mut txs = Dhcpv6Transactions::default();
        let mut solicit = packet(Dhcpv6MessageType::Solicit, 1);
        solicit.timestamp = Utc::now() - Duration::minutes(MAX_OPEN_MINUTES + 1);
        DhcpTable::register_dhcpv6(&mut txs, &solicit);

        DhcpTable::register_dhcpv6(&mut txs, &packet(Dhcpv6MessageType::Advertise, 1));

        txs.retain_open(Utc::now() - Duration::minutes(1));
        assert!(txs.transactions.is_empty());
        assert!(txs.exchanges.is_empty());
    }
}
