use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex};
use chrono::{Duration, Utc};
use log::{error, warn};
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::dhcp_transactions_report;
use crate::metrics::Metrics;
use crate::tracemark;
use crate::wired::packets::{Dhcpv4Packet, Dhcpv4Transaction, Dhcpv4TransactionNote};
use crate::wired::types::{Dhcp4TransactionType, Dhcpv4MessageType};

pub struct DhcpTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    metrics: Arc<Mutex<Metrics>>,
    transactions: Mutex<HashMap<u32, Dhcpv4Transaction>>
}

impl DhcpTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        DhcpTable {
            leaderlink,
            metrics,
            transactions: Mutex::new(HashMap::new())
        }
    }

    pub fn register_dhcpv4_packet(&self, dhcp: Arc<Dhcpv4Packet>) {
        match self.transactions.lock() {
            Ok(mut txs) => {
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
                                                tx.offered_ip_addresses.insert(address);
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
                            return;
                        }

                        // Release is terminal with no more messages in transaction.
                        let complete = tx_type == Dhcp4TransactionType::Release;
                        let successful = match tx_type == Dhcp4TransactionType::Release {
                            true => Some(true),
                            false => None
                        };

                        let mut timestamps = HashMap::new();
                        timestamps.insert(dhcp.message_type.clone(), vec![dhcp.timestamp]);

                        let tx = Dhcpv4Transaction {
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
                            server_responses: Vec::new(),
                            selected_server_address: dhcp.server_identifier,
                            successful,
                            complete
                        };

                        tracemark!("New DHCP transaction started: {:?}", tx);

                        txs.insert(dhcp.transaction_id, tx);
                    }
                }
            },
            Err(e) => error!("Could not acquire DHCPv4 transactions table: {}", e)
        }
    }

    pub fn process_report(&self) {
        let incomplete_cutoff = Utc::now() - Duration::minutes(1);
        match self.transactions.lock() {
            Ok(mut txs) => {
                // Generate JSON.
                let mut timer = Timer::new();
                let report = match serde_json::to_string(&dhcp_transactions_report::generate(&txs)) {
                    Ok(report) => report,
                    Err(e) => {
                        error!("Could not serialize DHCP transactions report: {}", e);
                        return;
                    }
                };
                timer.stop();
                record_timer(
                    timer.elapsed_microseconds(),
                    "tables.dhcp.timer.report_generation",
                    &self.metrics
                );

                // Send report.
                match self.leaderlink.lock() {
                    Ok(link) => {
                        if let Err(e) = link.send_report("dhcp/transactions", report) {
                            error!("Could not submit DHCP transactions report: {}", e);
                        }
                    },
                    Err(e) => error!("Could not acquire leader link lock for DHCP transactions \
                        report submission: {}", e)
                }

                // Clean up completed or expired transactions.
                txs.retain(|_, tx| !tx.complete && tx.latest_packet >= incomplete_cutoff);
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

        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("tables.dhcp.transactions.size", table_size);
            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

}