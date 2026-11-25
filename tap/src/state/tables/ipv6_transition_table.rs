use std::collections::{BTreeMap, HashMap};
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};
use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicU64, Ordering};
use chrono::{DateTime, Duration, Utc};
use log::error;
use crate::helpers::timer::{record_timer, Timer};
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::ipv6_transition_tunnels_report;
use crate::metrics::Metrics;
use crate::protocols::parsers::l4_key::L4Key;
use crate::wired::packets::{
    GenericConnectionStatus, Ipv6InIpv4Packet, Ipv6TransitionTransport, Ipv6TransitionTunnelType, TeredoConversation,
    TeredoMessage, TeredoQualification
};

const INACTIVE_AFTER_SECONDS: i64 = 90;
const RETENTION_MINUTES: i64 = 10;

const MAX_TUNNELS: usize = 5_000;
const MAX_TUNNELED_ADDRESSES: usize = 5;

pub struct Ipv6TransitionTable {
    leaderlink: Arc<Mutex<Leaderlink>>,
    metrics: Arc<Mutex<Metrics>>,
    tunnels: Mutex<Tunnels>,
    dropped: AtomicU64
}

#[derive(Debug, Clone)]
pub struct Ipv6TransitionTunnelRecord {
    pub tunnel_type: Ipv6TransitionTunnelType,
    pub transport: Ipv6TransitionTransport,
    pub connection_status: GenericConnectionStatus,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub source_port: Option<u16>,
    pub destination_port: Option<u16>,
    pub established_at: DateTime<Utc>,
    pub terminated_at: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>,
    // A Teredo tunnel can span several UDP conversations. Each reports its own running byte count.
    completed_bytes: u64,
    current_conversation_start: DateTime<Utc>,
    current_bytes: u64,
    packets_forward: u64,
    packets_reverse: u64,
    source_side_addresses: TunneledAddresses,
    destination_side_addresses: TunneledAddresses,
    teredo_messages: Vec<TeredoMessage>,
    teredo_qualification: Option<TeredoQualification>,
    changed: bool
}

#[derive(Debug, Clone, Default)]
struct TunneledAddresses {
    addresses: Vec<Ipv6Addr>,
    more: bool
}

impl TunneledAddresses {

    fn add(&mut self, address: Ipv6Addr) {
        if self.addresses.contains(&address) {
            return;
        }

        if self.addresses.len() >= MAX_TUNNELED_ADDRESSES {
            self.more = true;
        } else {
            self.addresses.push(address);
        }
    }

    fn describe(&self) -> Option<String> {
        if self.addresses.is_empty() {
            return None;
        }

        let mut text = self.addresses.iter().map(|a| a.to_string()).collect::<Vec<String>>().join(", ");
        if self.more {
            text.push_str(&format!(" and more (the first {} are recorded)", MAX_TUNNELED_ADDRESSES));
        }

        Some(text)
    }

}

impl Ipv6TransitionTunnelRecord {

    pub fn tunneled_bytes(&self) -> u64 {
        self.completed_bytes + self.current_bytes
    }

    fn is_single_packet(&self) -> bool {
        match self.transport {
            Ipv6TransitionTransport::IP => self.packets_forward + self.packets_reverse <= 1,
            Ipv6TransitionTransport::UDP => {
                self.teredo_qualification == Some(TeredoQualification::NotObserved)
                    && self.teredo_messages == [TeredoMessage::Bubble]
            }
        }
    }

    pub fn details(&self) -> BTreeMap<String, String> {
        let mut details = BTreeMap::new();

        if self.transport == Ipv6TransitionTransport::IP {
            details.insert("Packets client to server".to_string(), self.packets_forward.to_string());
            details.insert("Packets server to client".to_string(), self.packets_reverse.to_string());
        }
        if let Some(addresses) = self.source_side_addresses.describe() {
            details.insert("Tunneled addresses on the client side".to_string(), addresses);
        }
        if let Some(addresses) = self.destination_side_addresses.describe() {
            details.insert("Tunneled addresses on the server side".to_string(), addresses);
        }
        if let Some(qualification) = self.teredo_qualification {
            details.insert("Qualification".to_string(), qualification.name().to_string());
        }
        if !self.teredo_messages.is_empty() {
            let messages: Vec<&str> = self.teredo_messages.iter().map(|m| m.name()).collect();
            details.insert("Messages".to_string(), messages.join(", "));
        }

        details
    }

}

#[derive(Default)]
struct Tunnels {
    tunnels: HashMap<L4Key, Ipv6TransitionTunnelRecord>
}

impl Tunnels {

    // Returns false if the table is full and the packet opens a tunnel that is not recorded.
    fn register_packet(&mut self, packet: &Ipv6InIpv4Packet) -> bool {
        let source = IpAddr::V4(packet.source_address);
        let destination = IpAddr::V4(packet.destination_address);
        let key = L4Key::new(source, 0, destination, 0);

        if !self.tunnels.contains_key(&key) {
            if packet.tunneled_addresses.is_none() {
                return true;
            }

            if self.tunnels.len() >= MAX_TUNNELS {
                return false;
            }

            self.tunnels.insert(key.clone(), Ipv6TransitionTunnelRecord {
                tunnel_type: Ipv6TransitionTunnelType::SixInFour,
                transport: Ipv6TransitionTransport::IP,
                connection_status: GenericConnectionStatus::Active,
                source_mac: packet.source_mac.clone(),
                destination_mac: packet.destination_mac.clone(),
                source_address: source,
                destination_address: destination,
                source_port: None,
                destination_port: None,
                established_at: packet.timestamp,
                terminated_at: None,
                most_recent_segment_time: packet.timestamp,
                completed_bytes: 0,
                current_conversation_start: packet.timestamp,
                current_bytes: 0,
                packets_forward: 0,
                packets_reverse: 0,
                source_side_addresses: TunneledAddresses::default(),
                destination_side_addresses: TunneledAddresses::default(),
                teredo_messages: Vec::new(),
                teredo_qualification: None,
                changed: true
            });
        }

        let Some(tunnel) = self.tunnels.get_mut(&key) else {
            return true;
        };

        let forward = source == tunnel.source_address;

        tunnel.current_bytes += packet.tunneled_bytes as u64;
        tunnel.connection_status = GenericConnectionStatus::Active;
        tunnel.terminated_at = None;
        tunnel.changed = true;
        if packet.timestamp > tunnel.most_recent_segment_time {
            tunnel.most_recent_segment_time = packet.timestamp;
        }

        if forward {
            tunnel.packets_forward += 1;
        } else {
            tunnel.packets_reverse += 1;
        }

        if let Some((tunneled_source, tunneled_destination)) = packet.tunneled_addresses {
            if forward {
                tunnel.source_side_addresses.add(tunneled_source);
                tunnel.destination_side_addresses.add(tunneled_destination);
            } else {
                tunnel.source_side_addresses.add(tunneled_destination);
                tunnel.destination_side_addresses.add(tunneled_source);
            }

            // A generic tunnel can turn out to be a more specific one, never the other way around.
            if tunnel.tunnel_type == Ipv6TransitionTunnelType::SixInFour {
                tunnel.tunnel_type = classify(
                    packet.source_address, packet.destination_address, tunneled_source, tunneled_destination
                );
            }
        }

        true
    }

    fn register_teredo(&mut self, conversation: &TeredoConversation) -> bool {
        let (connection_status, terminated_at) = match conversation.end_time {
            Some(end_time) => (GenericConnectionStatus::Inactive, Some(end_time)),
            None => (GenericConnectionStatus::Active, None)
        };

        match self.tunnels.get_mut(&conversation.session_key) {
            Some(tunnel) => {
                for message in &conversation.messages {
                    if !tunnel.teredo_messages.contains(message) {
                        tunnel.teredo_messages.push(*message);
                    }
                }

                if tunnel.teredo_qualification.is_none_or(|q| conversation.qualification > q) {
                    tunnel.teredo_qualification = Some(conversation.qualification);
                }

                if tunnel.source_mac.is_none() {
                    tunnel.source_mac = conversation.source_mac.clone();
                }
                if tunnel.destination_mac.is_none() {
                    tunnel.destination_mac = conversation.destination_mac.clone();
                }

                tunnel.connection_status = connection_status;
                tunnel.terminated_at = terminated_at;

                if conversation.start_time < tunnel.established_at {
                    tunnel.established_at = conversation.start_time;
                }
                if conversation.most_recent_segment_time > tunnel.most_recent_segment_time {
                    tunnel.most_recent_segment_time = conversation.most_recent_segment_time;
                }

                if conversation.start_time == tunnel.current_conversation_start {
                    tunnel.current_bytes = conversation.bytes;
                } else {
                    tunnel.completed_bytes += tunnel.current_bytes;
                    tunnel.current_conversation_start = conversation.start_time;
                    tunnel.current_bytes = conversation.bytes;
                }

                tunnel.changed = true;
            },
            None => {
                if self.tunnels.len() >= MAX_TUNNELS {
                    return false;
                }

                self.tunnels.insert(conversation.session_key.clone(), Ipv6TransitionTunnelRecord {
                    tunnel_type: Ipv6TransitionTunnelType::Teredo,
                    transport: Ipv6TransitionTransport::UDP,
                    connection_status,
                    source_mac: conversation.source_mac.clone(),
                    destination_mac: conversation.destination_mac.clone(),
                    source_address: conversation.source_address,
                    destination_address: conversation.destination_address,
                    source_port: Some(conversation.source_port),
                    destination_port: Some(conversation.destination_port),
                    established_at: conversation.start_time,
                    terminated_at,
                    most_recent_segment_time: conversation.most_recent_segment_time,
                    completed_bytes: 0,
                    current_conversation_start: conversation.start_time,
                    current_bytes: conversation.bytes,
                    packets_forward: 0,
                    packets_reverse: 0,
                    source_side_addresses: TunneledAddresses::default(),
                    destination_side_addresses: TunneledAddresses::default(),
                    teredo_messages: conversation.messages.clone(),
                    teredo_qualification: Some(conversation.qualification),
                    changed: true
                });
            }
        }

        true
    }

    fn mark_inactive(&mut self, now: DateTime<Utc>) {
        let cutoff = now - Duration::seconds(INACTIVE_AFTER_SECONDS);

        for tunnel in self.tunnels.values_mut() {
            if matches!(tunnel.connection_status, GenericConnectionStatus::Active)
                && tunnel.most_recent_segment_time < cutoff {
                tunnel.connection_status = GenericConnectionStatus::Inactive;
                tunnel.terminated_at = Some(tunnel.most_recent_segment_time);
                tunnel.changed = true;
            }
        }
    }

    fn changed(&self) -> Vec<&Ipv6TransitionTunnelRecord> {
        self.tunnels.values().filter(|t| t.changed).collect()
    }

    fn reported(&mut self, now: DateTime<Utc>) {
        let cutoff = now - Duration::minutes(RETENTION_MINUTES);

        self.tunnels.retain(|_, t| t.most_recent_segment_time >= cutoff);

        // Close to the limit, tunnels that never got past their first packet make room, so that a flood of single
        // packets cannot keep a real tunnel out. The node has them from this report.
        if self.tunnels.len() >= MAX_TUNNELS / 10 * 9 {
            self.tunnels.retain(|_, t| !t.is_single_packet());
        }
        for tunnel in self.tunnels.values_mut() {
            tunnel.changed = false;
        }
    }

}

fn classify(outer_source: Ipv4Addr,
            outer_destination: Ipv4Addr,
            tunneled_source: Ipv6Addr,
            tunneled_destination: Ipv6Addr) -> Ipv6TransitionTunnelType {
    if is_isatap_address_of(tunneled_source, outer_source)
        || is_isatap_address_of(tunneled_destination, outer_destination) {
        Ipv6TransitionTunnelType::Isatap
    } else if is_6to4_address_of(tunneled_source, outer_source)
        || is_6to4_address_of(tunneled_destination, outer_destination) {
        Ipv6TransitionTunnelType::SixToFour
    } else {
        Ipv6TransitionTunnelType::SixInFour
    }
}

// RFC 5214: the interface identifier is 0000:5efe or 0200:5efe followed by the IPv4 address of the node.
fn is_isatap_address_of(address: Ipv6Addr, node: Ipv4Addr) -> bool {
    let octets = address.octets();

    (octets[8] == 0x00 || octets[8] == 0x02)
        && octets[9] == 0x00
        && octets[10] == 0x5e
        && octets[11] == 0xfe
        && octets[12..16] == node.octets()
}

// RFC 3056: 2002::/16 followed by the IPv4 address of the 6to4 router.
fn is_6to4_address_of(address: Ipv6Addr, router: Ipv4Addr) -> bool {
    let octets = address.octets();

    octets[0] == 0x20 && octets[1] == 0x02 && octets[2..6] == router.octets()
}

impl Ipv6TransitionTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>, metrics: Arc<Mutex<Metrics>>) -> Self {
        Self {
            leaderlink,
            metrics,
            tunnels: Mutex::new(Tunnels::default()),
            dropped: AtomicU64::new(0)
        }
    }

    pub fn register_packet(&self, packet: &Ipv6InIpv4Packet) {
        match self.tunnels.lock() {
            Ok(mut tunnels) => {
                if !tunnels.register_packet(packet) {
                    self.dropped.fetch_add(1, Ordering::Relaxed);
                }
            },
            Err(e) => error!("Could not acquire IPv6 transition tunnels table mutex: {}", e)
        }
    }

    pub fn register_teredo(&self, conversation: &TeredoConversation) {
        match self.tunnels.lock() {
            Ok(mut tunnels) => {
                if !tunnels.register_teredo(conversation) {
                    self.dropped.fetch_add(1, Ordering::Relaxed);
                }
            },
            Err(e) => error!("Could not acquire IPv6 transition tunnels table mutex: {}", e)
        }
    }

    pub fn process_report(&self) {
        match self.tunnels.lock() {
            Ok(mut tunnels) => {
                let now = Utc::now();
                tunnels.mark_inactive(now);

                let changed = tunnels.changed();
                if !changed.is_empty() {
                    let mut timer = Timer::new();
                    let report = match serde_json::to_string(&ipv6_transition_tunnels_report::generate(&changed)) {
                        Ok(report) => report,
                        Err(e) => {
                            error!("Could not serialize IPv6 transition tunnels report: {}", e);
                            return;
                        }
                    };
                    timer.stop();
                    record_timer(
                        timer.elapsed_microseconds(),
                        "tables.ipv6_transition.timer.report_generation",
                        &self.metrics
                    );

                    match self.leaderlink.lock() {
                        Ok(link) => {
                            if let Err(e) = link.send_report("ipv6transition/tunnels", report) {
                                error!("Could not submit IPv6 transition tunnels report: {}", e);
                            }
                        },
                        Err(e) => error!("Could not acquire leader link lock for IPv6 transition tunnels \
                                            report submission: {}", e)
                    }
                }

                tunnels.reported(now);
            },
            Err(e) => {
                error!("Could not acquire IPv6 transition tunnels table mutex for report generation: {}", e);
            }
        }
    }

    pub fn calculate_metrics(&self) {
        let tunnels_size: i128 = match self.tunnels.lock() {
            Ok(t) => t.tunnels.len() as i128,
            Err(e) => {
                error!("Could not acquire mutex to calculate IPv6 transition tunnels table size: {}", e);

                -1
            }
        };

        match self.metrics.lock() {
            Ok(mut metrics) => {
                metrics.set_gauge("tables.ipv6_transition.tunnels.size", tunnels_size);
                metrics.set_gauge("tables.ipv6_transition.dropped", self.dropped.load(Ordering::Relaxed) as i128);
            },
            Err(e) => error!("Could not acquire metrics mutex: {}", e)
        }
    }

}

#[cfg(test)]
mod tests {
    use crate::protocols::parsers::ipv6_in_ipv4_parser;
    use crate::protocols::parsers::ipv6_in_ipv4_parser::tests::{at, inner, outer};
    use super::*;

    fn packet(source: &str, destination: &str, payload: Vec<u8>, fragment_offset: u16, seconds: i64) -> Ipv6InIpv4Packet {
        ipv6_in_ipv4_parser::parse(outer(source, destination, payload, fragment_offset, seconds)).unwrap()
    }

    fn only(tunnels: &Tunnels) -> &Ipv6TransitionTunnelRecord {
        assert_eq!(tunnels.tunnels.len(), 1);
        tunnels.tunnels.values().next().unwrap()
    }

    fn teredo(start: i64, most_recent: i64, bytes: u64, messages: Vec<TeredoMessage>, qualification: TeredoQualification)
        -> TeredoConversation {
        let source: IpAddr = "10.0.0.5".parse().unwrap();
        let destination: IpAddr = "198.51.100.9".parse().unwrap();

        TeredoConversation {
            session_key: L4Key::new(source, 50000, destination, 3544),
            source_mac: Some("AA:AA:AA:AA:AA:01".to_string()),
            destination_mac: Some("AA:AA:AA:AA:AA:09".to_string()),
            source_address: source,
            destination_address: destination,
            source_port: 50000,
            destination_port: 3544,
            messages,
            qualification,
            start_time: at(start),
            end_time: None,
            most_recent_segment_time: at(most_recent),
            bytes
        }
    }

    #[test]
    fn both_directions_are_one_tunnel() {
        let mut tunnels = Tunnels::default();
        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 60), 0, 0));
        tunnels.register_packet(&packet("198.51.100.1", "192.0.2.10", inner("2001:db8:ffff::1", "2001:db8:1::10", 20), 0, 5));

        let tunnel = only(&tunnels);
        assert_eq!(tunnel.tunnel_type, Ipv6TransitionTunnelType::SixInFour);
        assert_eq!(tunnel.transport, Ipv6TransitionTransport::IP);
        assert_eq!(tunnel.source_address.to_string(), "192.0.2.10");
        assert_eq!(tunnel.destination_address.to_string(), "198.51.100.1");
        assert_eq!(tunnel.source_mac.as_deref(), Some("mac-of-192.0.2.10"));
        assert_eq!((tunnel.source_port, tunnel.destination_port), (None, None));
        assert_eq!(tunnel.tunneled_bytes(), 100 + 60);
        assert_eq!(tunnel.established_at, at(0));
        assert_eq!(tunnel.most_recent_segment_time, at(5));

        let details = tunnel.details();
        assert_eq!(details["Packets client to server"], "1");
        assert_eq!(details["Packets server to client"], "1");
        assert_eq!(details["Tunneled addresses on the client side"], "2001:db8:1::10");
        assert_eq!(details["Tunneled addresses on the server side"], "2001:db8:ffff::1");
        assert!(!details.contains_key("Qualification"));
    }

    #[test]
    fn only_changed_tunnels_are_reported_and_idle_ones_are_forgotten() {
        let mut tunnels = Tunnels::default();
        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 0));

        assert_eq!(tunnels.changed().len(), 1);
        tunnels.reported(at(1));
        assert_eq!(tunnels.changed().len(), 0);
        assert_eq!(tunnels.tunnels.len(), 1);

        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 30));
        assert_eq!(tunnels.changed().len(), 1);
        assert_eq!(only(&tunnels).tunneled_bytes(), 80);
        assert_eq!(only(&tunnels).established_at, at(0));

        tunnels.reported(at(30 + RETENTION_MINUTES * 60 + 1));
        assert!(tunnels.tunnels.is_empty());
    }

    #[test]
    fn a_silent_tunnel_becomes_inactive_and_is_reported_once_more() {
        let mut tunnels = Tunnels::default();
        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 0));
        tunnels.reported(at(1));

        tunnels.mark_inactive(at(INACTIVE_AFTER_SECONDS - 1));
        assert_eq!(tunnels.changed().len(), 0);

        tunnels.mark_inactive(at(INACTIVE_AFTER_SECONDS + 1));
        assert_eq!(tunnels.changed().len(), 1);
        assert!(matches!(only(&tunnels).connection_status, GenericConnectionStatus::Inactive));
        assert_eq!(only(&tunnels).terminated_at, Some(at(0)));

        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 200));
        assert!(matches!(only(&tunnels).connection_status, GenericConnectionStatus::Active));
        assert_eq!(only(&tunnels).terminated_at, None);
    }

    #[test]
    fn isatap_is_recognized_from_the_embedded_address() {
        let mut tunnels = Tunnels::default();
        tunnels.register_packet(&packet("10.1.2.3", "10.9.9.9", inner("fe80::5efe:a01:203", "ff02::2", 8), 0, 0));
        assert_eq!(only(&tunnels).tunnel_type, Ipv6TransitionTunnelType::Isatap);

        let mut tunnels = Tunnels::default();
        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8::200:5efe:c000:20a", "2001:db8::1", 8), 0, 0));
        assert_eq!(only(&tunnels).tunnel_type, Ipv6TransitionTunnelType::Isatap);

        let mut tunnels = Tunnels::default();
        tunnels.register_packet(&packet("10.1.2.4", "10.9.9.9", inner("fe80::5efe:a01:203", "ff02::2", 8), 0, 0));
        assert_eq!(only(&tunnels).tunnel_type, Ipv6TransitionTunnelType::SixInFour);
    }

    #[test]
    fn six_to_four_is_recognized_from_the_prefix() {
        let mut tunnels = Tunnels::default();
        tunnels.register_packet(&packet("192.0.2.10", "192.88.99.1", inner("2002:c000:20a::1", "2001:db8::1", 8), 0, 0));
        assert_eq!(only(&tunnels).tunnel_type, Ipv6TransitionTunnelType::SixToFour);

        let mut tunnels = Tunnels::default();
        tunnels.register_packet(&packet("192.88.99.1", "192.0.2.10", inner("2001:db8::1", "2002:c000:20a::1", 8), 0, 0));
        assert_eq!(only(&tunnels).tunnel_type, Ipv6TransitionTunnelType::SixToFour);
    }

    #[test]
    fn a_generic_tunnel_can_become_a_specific_one_but_not_the_reverse() {
        let mut tunnels = Tunnels::default();
        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 0));
        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2002:c000:20a::1", "2001:db8:ffff::1", 0), 0, 1));
        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 2));

        assert_eq!(only(&tunnels).tunnel_type, Ipv6TransitionTunnelType::SixToFour);
    }

    #[test]
    fn later_fragments_count_for_known_tunnels_only() {
        let mut tunnels = Tunnels::default();
        assert!(tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", vec![0xab; 100], 185, 0)));
        assert!(tunnels.tunnels.is_empty());

        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 1));
        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", vec![0xab; 100], 185, 2));

        assert_eq!(only(&tunnels).tunneled_bytes(), 140);
        assert_eq!(only(&tunnels).details()["Packets client to server"], "2");
    }

    #[test]
    fn tunneled_addresses_are_capped() {
        let mut tunnels = Tunnels::default();
        for i in 0..8 {
            let source = format!("2001:db8:1::{:x}", i + 1);
            tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner(&source, "2001:db8:ffff::1", 0), 0, i));
        }

        let details = only(&tunnels).details();
        assert_eq!(
            details["Tunneled addresses on the client side"],
            "2001:db8:1::1, 2001:db8:1::2, 2001:db8:1::3, 2001:db8:1::4, 2001:db8:1::5 and more (the first 5 are recorded)"
        );
        assert_eq!(details["Tunneled addresses on the server side"], "2001:db8:ffff::1");
    }

    #[test]
    fn the_number_of_tunnels_is_capped() {
        let mut tunnels = Tunnels::default();
        let mut refused = 0;
        for i in 0..(MAX_TUNNELS as u32 + 10) {
            let source = Ipv4Addr::from(0x0a00_0000 + i).to_string();
            if !tunnels.register_packet(&packet(&source, "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 0)) {
                refused += 1;
            }
        }

        assert_eq!(tunnels.tunnels.len(), MAX_TUNNELS);
        assert_eq!(refused, 10);
    }

    #[test]
    fn teredo_conversations_of_one_tunnel_add_up() {
        let mut tunnels = Tunnels::default();
        tunnels.register_teredo(&teredo(0, 2, 300, vec![TeredoMessage::RouterSolicitation], TeredoQualification::Partial));
        tunnels.register_teredo(&teredo(0, 5, 900, vec![TeredoMessage::RouterSolicitation, TeredoMessage::RouterAdvertisement],
                                       TeredoQualification::Complete));

        let tunnel = only(&tunnels);
        assert_eq!(tunnel.tunnel_type, Ipv6TransitionTunnelType::Teredo);
        assert_eq!(tunnel.transport, Ipv6TransitionTransport::UDP);
        assert_eq!((tunnel.source_port, tunnel.destination_port), (Some(50000), Some(3544)));
        assert_eq!(tunnel.tunneled_bytes(), 900);
        assert_eq!(tunnel.details()["Qualification"], "Complete");
        assert_eq!(tunnel.details()["Messages"], "Router solicitation, Router advertisement");
        assert!(!tunnel.details().contains_key("Packets client to server"));

        tunnels.register_teredo(&teredo(60, 70, 400, vec![TeredoMessage::Data], TeredoQualification::NotObserved));

        let tunnel = only(&tunnels);
        assert_eq!(tunnel.tunneled_bytes(), 1300);
        assert_eq!(tunnel.established_at, at(0));
        assert_eq!(tunnel.most_recent_segment_time, at(70));
        assert_eq!(tunnel.details()["Qualification"], "Complete");
        assert_eq!(tunnel.details()["Messages"], "Router solicitation, Router advertisement, Data");
    }

    #[test]
    fn an_ended_teredo_conversation_ends_the_tunnel() {
        let mut tunnels = Tunnels::default();
        let mut conversation = teredo(0, 5, 300, vec![TeredoMessage::Bubble], TeredoQualification::NotObserved);
        conversation.end_time = Some(at(5));
        tunnels.register_teredo(&conversation);

        assert!(matches!(only(&tunnels).connection_status, GenericConnectionStatus::Inactive));
        assert_eq!(only(&tunnels).terminated_at, Some(at(5)));
    }

    #[test]
    fn a_flood_of_single_packets_makes_room_for_real_tunnels() {
        let mut tunnels = Tunnels::default();
        for i in 0..(MAX_TUNNELS as u32) {
            let source = Ipv4Addr::from(0x0a00_0000 + i).to_string();
            tunnels.register_packet(&packet(&source, "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 0));
        }

        tunnels.register_packet(&packet("198.51.100.1", "10.0.0.0", inner("2001:db8:ffff::1", "2001:db8:1::10", 0), 0, 1));
        assert!(!tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 1)));

        tunnels.reported(at(2));
        assert_eq!(tunnels.tunnels.len(), 1);
        assert_eq!(only(&tunnels).source_address.to_string(), "10.0.0.0");

        assert!(tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 3)));
    }

    #[test]
    fn single_packets_stay_while_there_is_room() {
        let mut tunnels = Tunnels::default();
        tunnels.register_packet(&packet("192.0.2.10", "198.51.100.1", inner("2001:db8:1::10", "2001:db8:ffff::1", 0), 0, 0));
        tunnels.reported(at(1));

        assert_eq!(tunnels.tunnels.len(), 1);
    }
}
