use std::collections::{HashMap, HashSet};
use std::mem;
use std::net::IpAddr;
use std::sync::Mutex;
use chrono::{DateTime, Utc, TimeZone};
use strum_macros::Display;
use crate::protocols::detection::l7_tagger::L7Tag;
use crate::protocols::parsers::l4_key::L4Key;
use crate::protocols::parsers::ntp_parser::NtpReferenceId;
use crate::wired::traffic_direction::TrafficDirection;

use crate::wired::types::{HardwareType, EtherType, ArpOpCode, DNSType, DNSClass, DNSDataType, Dhcpv4MessageType, Dhcpv4OpCode, Dhcp4TransactionType};

#[derive(Debug)]
pub struct EthernetData {
    pub data: Vec<u8>,
    pub timestamp: DateTime<Utc>
}

#[derive(Debug)]
pub struct EthernetPacket {
    pub source_mac: String,
    pub destination_mac: String,
    pub data: Vec<u8>,
    pub packet_type: EtherType,
    pub size: u32,
    pub timestamp: DateTime<Utc>
}

#[derive(Debug)]
pub struct ArpPacket {
    pub ethernet_source_mac: String,
    pub ethernet_destination_mac: String,
    pub hardware_type: HardwareType,
    pub protocol_type: EtherType,
    pub operation: ArpOpCode,
    pub arp_sender_mac: String,
    pub arp_sender_address: IpAddr,
    pub arp_target_mac: String,
    pub arp_target_address: IpAddr,
    pub size: u32,
    pub timestamp: DateTime<Utc>
}

#[derive(Debug)]
pub struct IPv4Packet {
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub header_length: usize,
    pub total_length: usize,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub ttl: u8,
    pub ip_tos: u8,
    pub df: bool,
    pub protocol: u8,
    pub payload: Vec<u8>,
    pub size: u32,
    pub timestamp: DateTime<Utc>
}

#[derive(Debug)]
pub struct TcpSegment {
    pub sequence_number: u32,
    pub ack_number: u32,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub source_port: u16,
    pub destination_port: u16,
    pub session_key: L4Key,
    pub flags: TcpFlags,
    pub payload: Vec<u8>,
    pub ip_ttl: u8,
    pub ip_tos: u8,
    pub ip_df: bool,
    pub window_size: u16,
    pub maximum_segment_size: Option<u16>,
    pub window_scale_multiplier: Option<u8>,
    pub options: Vec<u8>,
    pub size: u32,
    pub timestamp: DateTime<Utc>
}

impl TcpSegment {
    pub fn determine_direction(&self) -> TrafficDirection {
        if self.session_key.address_low == self.source_address
            && self.session_key.port_low == self.source_port {
            TrafficDirection::ServerToClient
        } else {
            TrafficDirection::ClientToServer
        }
    }

    pub fn get_directional_byte_counts(&self) -> (u64, u64) {
        match self.determine_direction() {
            TrafficDirection::ClientToServer => (self.size as u64, 0),
            TrafficDirection::ServerToClient => (0, self.size as u64)
        }
    }
}

#[derive(Debug)]
pub struct TcpFlags {
    pub ack: bool,
    pub reset: bool,
    pub syn: bool,
    pub fin: bool,
    pub ece: bool,
    pub cwr: bool
}

#[derive(Debug)]
pub struct Datagram {
    pub session_key: L4Key,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub source_port: u16,
    pub destination_port: u16,
    pub payload: Vec<u8>,
    pub size: u32,
    pub timestamp: DateTime<Utc>,
    pub tags: Mutex<HashSet<L7Tag>>
}

impl Datagram {
    pub fn determine_direction(&self) -> TrafficDirection {
        if self.session_key.address_low == self.source_address
            && self.session_key.port_low == self.source_port {
            TrafficDirection::ServerToClient
        } else {
            TrafficDirection::ClientToServer
        }
    }

    pub fn get_directional_byte_counts(&self) -> (u64, u64) {
        match self.determine_direction() {
            TrafficDirection::ClientToServer => (self.size as u64, 0),
            TrafficDirection::ServerToClient => (0, self.size as u64)
        }
    }
}

#[derive(Debug)]
pub struct DNSPacket {
    pub transaction_id: Option<u16>,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub source_port: u16,
    pub destination_port: u16,
    pub dns_type: DNSType,
    pub question_count: u16,
    pub answer_count: u16,
    pub queries: Option<Vec<DNSData>>,
    pub responses: Option<Vec<DNSData>>,
    pub size: u32,
    pub timestamp: DateTime<Utc>
}

#[derive(Debug)]
pub struct DNSData {
    pub name: String,
    pub name_etld: Option<String>,
    pub dns_type: DNSDataType,
    pub class: DNSClass,
    pub value: Option<String>,
    pub value_etld: Option<String>,
    pub ttl: Option<u32>,
    pub entropy: Option<f32>
}

#[derive(Debug)]
pub struct IPv6Packet { }

#[derive(Debug, Display, Clone)]
pub enum SocksType {
    Socks4,
    Socks4A,
    Socks5
}

#[derive(Debug, PartialEq, Eq, Display, Clone)]
pub enum SocksAuthenticationResult {
    Success, Failure, Unknown
}

#[derive(Debug, PartialEq, Eq, Display, Clone)]
pub enum SocksConnectionHandshakeStatus {
    Granted,
    Rejected,
    FailedIdentdUnreachable,
    FailedIdentdAuth,
    Invalid,
    GeneralFailure,
    ConnectionNotAllowedByRuleset,
    NetworkUnreachable,
    HostUnreachable,
    ConnectionRefusedByDestination,
    Ttl,
    UnsupportedCommand,
    UnsupportedAddressType,
    NotReached
}

#[derive(Debug, Display, Clone)]
pub enum GenericConnectionStatus {
    Active, Inactive, InactiveTimeout
}

#[derive(Debug, Display, PartialEq, Eq)]
pub enum SocksAuthenticationMethod {
    None,
    NoneAcceptable,
    Gssapi,
    UsernamePassword,
    ChallengeHandshake,
    ChallengeResponse,
    Ssl,
    Nds,
    MultiAuthenticationFramework,
    JsonParameterBlock,
    Unknown
}

#[derive(Debug, Clone)]
pub struct SocksTunnel {
    pub socks_type: SocksType,
    pub authentication_status: SocksAuthenticationResult,
    pub handshake_status: SocksConnectionHandshakeStatus,
    pub connection_status: GenericConnectionStatus,
    pub username: Option<String>,
    pub tunneled_bytes: u64,
    pub tunneled_destination_address: Option<IpAddr>,
    pub tunneled_destination_host: Option<String>,
    pub tunneled_destination_port: u16,
    pub tcp_session_key: L4Key,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub source_port: u16,
    pub destination_port: u16,
    pub established_at: DateTime<Utc>,
    pub terminated_at: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>
}

impl SocksTunnel {

    pub fn estimate_struct_size(&self) -> u32 {
        // Fixed size types
        let mut size = mem::size_of::<SocksType>() as u32             // socks_type
            + mem::size_of::<SocksAuthenticationResult>() as u32      // authentication_status
            + mem::size_of::<SocksConnectionHandshakeStatus>() as u32 // handshake_status
            + mem::size_of::<GenericConnectionStatus>() as u32        // connection_status
            + mem::size_of::<u64>() as u32                            // tunneled_bytes
            + mem::size_of::<u16>() as u32 * 3                        // tunneled_destination_port, source_port, destination_port
            + mem::size_of::<L4Key>() as u32                  // tcp_session_key
            + mem::size_of::<IpAddr>() as u32 * 2                     // source_address, destination_address
            + mem::size_of::<DateTime<Utc>>() as u32 * 2;             // established_at, most_recent_segment_time

        // Add size for terminated_at if it's present
        size += mem::size_of::<DateTime<Utc>>() as u32 * self.terminated_at.is_some() as u32;

        // Option<IpAddr> field (tunneled_destination_address)
        size += mem::size_of::<IpAddr>() as u32 * self.tunneled_destination_address.is_some() as u32;

        // Option<String> fields (username, tunneled_destination_host)
        if let Some(ref username) = self.username {
            size += username.len() as u32;
        }
        if let Some(ref tunneled_destination_host) = self.tunneled_destination_host {
            size += tunneled_destination_host.len() as u32;
        }

        // Strings (source_mac, destination_mac)
        if let Some(source_mac) = &self.source_mac {
            size += source_mac.len() as u32
        }
        if let Some(destination_mac) = &self.destination_mac {
            size += destination_mac.len() as u32
        }

        size
    }

}

#[derive(Debug, Clone)]
pub struct SshVersion {
    pub version: String,
    pub software: String,
    pub comments: Option<String>
}

#[derive(Debug, Clone)]
pub struct SshSession {
    pub client_version: SshVersion,
    pub server_version: SshVersion,
    pub connection_status: GenericConnectionStatus,
    pub tunneled_bytes: u64,
    pub tcp_session_key: L4Key,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub source_port: u16,
    pub destination_port: u16,
    pub established_at: DateTime<Utc>,
    pub terminated_at: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>
}

impl SshSession {

    pub fn estimate_struct_size(&self) -> u32 {
        let mut size = mem::size_of::<SshVersion>() as u32 * 2
            + mem::size_of::<GenericConnectionStatus>() as u32
            + mem::size_of::<u64>() as u32
            + mem::size_of::<L4Key>() as u32
            + mem::size_of::<u16>() as u32 * 2
            + mem::size_of::<IpAddr>() as u32 * 2
            + mem::size_of::<DateTime<Utc>>() as u32 * 2;

        // Add size for terminated_at if it's present
        size += mem::size_of::<DateTime<Utc>>() as u32 * self.terminated_at.is_some() as u32;

        // Strings (source_mac, destination_mac)
        if let Some(source_mac) = &self.source_mac {
            size += source_mac.len() as u32
        }
        if let Some(destination_mac) = &self.destination_mac {
            size += destination_mac.len() as u32
        }

        size
    }

}

#[derive(Debug, Display, Clone, Copy, PartialEq, Eq)]
pub enum RtspState {
    Probing,
    Describing,
    Establishing,
    Started
}

#[derive(Debug, Clone)]
pub enum RtspMediaLocator {
    Interleaved,

    Udp {
        client_rtp_port: u16,
        client_rtcp_port: Option<u16>,
        server_rtp_port: Option<u16>,
        server_rtcp_port: Option<u16>,
        redirect_destination: Option<IpAddr>,
    },

    Multicast { group: IpAddr, port: u16 }
}

#[derive(Debug, Display, Clone, Copy, PartialEq, Eq)]
pub enum RtspAuthPosture {
    Unknown,
    None,
    Basic,
    Digest
}

#[derive(Debug, Clone, Default)]
pub struct RtspMediaDescription {
    pub has_video: bool,
    pub has_audio: bool,
    pub video_codec: Option<String>,
    pub audio_codec: Option<String>,
    pub resolution: Option<String>
}

#[derive(Debug, Display, Clone, Copy, PartialEq, Eq)]
pub enum RtspFlag {
    UnauthenticatedStream,
    BasicAuthCleartext,
    AuthFailures,
    MediaRedirect,
    PublishAttempt,
    UnusualTransport
}

#[derive(Debug, Clone)]
pub struct RtspStream {
    pub setup_tcp_session_key: L4Key,
    pub setup_source_address: IpAddr,
    pub setup_source_port: u16,
    pub setup_destination_address: IpAddr,
    pub setup_destination_port: u16,
    pub setup_connection_status: GenericConnectionStatus,
    pub setup_established_at: DateTime<Utc>,
    pub setup_terminated_at: Option<DateTime<Utc>>,
    pub setup_most_recent_segment_time: DateTime<Utc>,
    pub state: RtspState,
    pub media: Option<RtspMediaLocator>,
    pub request_uri: Option<String>,
    pub client_agent: Option<String>,
    pub server_info: Option<String>,
    pub auth: RtspAuthPosture,
    pub media_desc: Option<RtspMediaDescription>,
    pub flags: Vec<RtspFlag>
}

impl RtspStream {

    pub fn estimate_struct_size(&self) -> u32 {
        let mut size = mem::size_of::<Self>() as u32;

        if let Some(uri) = &self.request_uri {
            size += uri.len() as u32;
        }
        if let Some(agent) = &self.client_agent {
            size += agent.len() as u32;
        }
        if let Some(server) = &self.server_info {
            size += server.len() as u32;
        }

        if let Some(desc) = &self.media_desc {
            if let Some(c) = &desc.video_codec { size += c.len() as u32; }
            if let Some(c) = &desc.audio_codec { size += c.len() as u32; }
            if let Some(r) = &desc.resolution  { size += r.len() as u32; }
        }

        size += self.flags.len() as u32 * mem::size_of::<RtspFlag>() as u32;

        size
    }

}

#[derive(Debug)]
pub struct Dhcpv4Packet {
    pub timestamp: DateTime<Utc>,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub source_port: u16,
    pub destination_port: u16,

    pub op_code: Dhcpv4OpCode,
    pub hardware_type: HardwareType,
    pub transaction_id: u32,
    pub seconds_elapsed: u16,
    pub dhcp_client_address: Option<IpAddr>,
    pub assigned_address: Option<IpAddr>,
    pub client_mac_address: String,

    pub options: Vec<u8>,
    pub vendor_class: Option<String>,

    // From DHCP options.
    pub message_type: Dhcpv4MessageType,
    pub requested_ip_address: Option<IpAddr>,
    pub hostname: Option<String>,
    pub parameter_request_list: Vec<u8>
}

impl Dhcpv4Packet {
    
    pub fn estimate_struct_size(&self) -> u32 {
        // Fixed size types
        let mut size = mem::size_of::<u16>() as u32 * 3 // source_port, destination_port, seconds_elapsed
            + mem::size_of::<u32>() as u32              // transaction_id
            + mem::size_of::<Dhcpv4OpCode>() as u32     // op_code
            + mem::size_of::<HardwareType>() as u32     // hardware_type
            + mem::size_of::<Dhcpv4MessageType>() as u32 // message_type
            + mem::size_of::<IpAddr>() as u32 * 2;      // source_address, destination_address

        // Strings (including client_mac_address)
        size += self.client_mac_address.len() as u32;
        if let Some(source_mac) = &self.source_mac {
            size += source_mac.len() as u32
        }
        if let Some(destination_mac) = &self.destination_mac {
            size += destination_mac.len() as u32
        }

        // Option<IpAddr> fields (dhcp_client_address, assigned_address, requested_ip_address)
        size += mem::size_of::<IpAddr>() as u32 * self.dhcp_client_address.is_some() as u32;
        size += mem::size_of::<IpAddr>() as u32 * self.assigned_address.is_some() as u32;
        size += mem::size_of::<IpAddr>() as u32 * self.requested_ip_address.is_some() as u32;

        // Option<String> fields (hostname)
        if let Some(ref hostname) = self.hostname {
            size += hostname.len() as u32;
        }

        size
    }
}

#[derive(Debug)]
pub struct Dhcpv4Transaction {
    pub transaction_type: Dhcp4TransactionType,
    pub transaction_id: u32,
    pub client_mac: String,
    pub additional_client_macs: HashSet<String>,
    pub server_mac: Option<String>,
    pub additional_server_macs: HashSet<String>,
    pub offered_ip_addresses: HashSet<IpAddr>,
    pub requested_ip_address: Option<IpAddr>,
    pub timestamps: HashMap<Dhcpv4MessageType, Vec<DateTime<Utc>>>,
    pub first_packet: DateTime<Utc>,
    pub latest_packet: DateTime<Utc>,
    pub notes: HashSet<Dhcpv4TransactionNote>,
    pub options: Vec<u8>,
    pub additional_options: HashSet<Vec<u8>>,
    pub vendor_class: Option<String>,
    pub additional_vendor_classes: HashSet<String>,
    pub successful: Option<bool>,
    pub complete: bool
}

impl Dhcpv4Transaction {
    // Handles `server_mac` and `additional_server_macs`.
    pub fn record_server_mac(&mut self, new_mac: Option<String>) {
        if new_mac == Some("FF:FF:FF:FF:FF:FF".to_string()) {
            // Ignore broadcast packets.
            return;
        }
        
        match (&self.server_mac, new_mac) {
            // No server yet, take the first one we see.
            (None, Some(mac)) => {
                self.server_mac = Some(mac);
            }

            // We already have one, and we got a new, different one.
            (Some(existing), Some(mac)) if existing != &mac => {
                // Avoid duplicates in the “additional” list
                if !self.additional_server_macs.iter().any(|m| m == &mac) {
                    self.additional_server_macs.insert(mac);
                }

                // Note that there are multiple server MACs.
                self.notes.insert(Dhcpv4TransactionNote::ServerMacChanged);
            }

            // Either new_mac is None, or it’s the same as what we already have—do nothing.
            _ => {}
        }
    }

    // Handles `client_mac` and `additional_client_macs`.
    pub fn record_client_mac(&mut self, new_mac: Option<String>) {
        if new_mac.is_none() {
            return;
        }

        if !self.client_mac.eq(new_mac.as_ref().unwrap()) {
            self.additional_client_macs.insert(new_mac.unwrap());
            self.notes.insert(Dhcpv4TransactionNote::ClientMacChanged);
        } else {
            self.client_mac = new_mac.unwrap();
        }
    }

    // Handles `options` and `additional_client_options`.
    pub fn record_options(&mut self, new_options: Vec<u8>) {
        if new_options.is_empty() {
            return;
        }

        match &self.options.is_empty() {
            true => {
                // First time seeing the options. Record.
                self.options = new_options;
            },
            false => {
                if !self.options.eq(&new_options) {
                    // Options changed.
                    self.additional_options.insert(new_options.clone());
                    self.notes.insert(Dhcpv4TransactionNote::OptionsChanged);
                }
            },
        }
    }

    pub fn record_timestamp(&mut self, msg_type: Dhcpv4MessageType, time: DateTime<Utc>) {
        self.timestamps
            .entry(msg_type)
            .or_default()
            .push(time);
    }

}

#[derive(Debug, Display, Hash, Eq, PartialEq)]
pub enum Dhcpv4TransactionNote {
    OfferNoYiaddr,
    ClientMacChanged,
    ServerMacChanged,
    OptionsChanged,
    VendorClassChanged
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum NtpPacketType {
    Client,
    Server
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct NtpTimestamp {
    pub seconds: u32,
    pub fraction: u32,
}

impl NtpTimestamp {
    pub fn is_zero(&self) -> bool {
        self.seconds == 0 && self.fraction == 0
    }

    pub fn to_datetime_utc(&self) -> Option<DateTime<Utc>> {
        if self.is_zero() {
            return None;
        }

        // NTP epoch (1900-01-01) to Unix epoch (1970-01-01) delta.
        const NTP_UNIX_EPOCH_DELTA: i64 = 2_208_988_800;
        let unix_secs = (self.seconds as i64).checked_sub(NTP_UNIX_EPOCH_DELTA)?;

        // Fraction is / 2^32 seconds. Convert to nanoseconds.
        // nanos = fraction * 1e9 / 2^32
        let nanos = ((self.fraction as u64) * 1_000_000_000u64) >> 32;

        // Chrono expects nanos < 1e9
        if nanos >= 1_000_000_000 {
            return None;
        }

        Some(Utc.timestamp_opt(unix_secs, nanos as u32).single()?)
    }
}

#[derive(Debug, Clone)]
pub struct NtpPacket {
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub source_port: u16,
    pub destination_port: u16,
    pub size: u32,
    pub timestamp: DateTime<Utc>, // Packet/Receive timestamp
    pub ntp_type: NtpPacketType,
    pub leap_indicator: u8,
    pub version: u8,
    pub mode: u8,
    pub stratum: u8,
    pub poll: i8,
    pub precision: i8,
    pub root_delay_seconds: f64,
    pub root_dispersion_seconds: f64,
    pub reference_id: NtpReferenceId,
    pub reference_timestamp: Option<NtpTimestamp>,
    pub origin_timestamp: Option<NtpTimestamp>,
    pub receive_timestamp: Option<NtpTimestamp>,
    pub transmit_timestamp: Option<NtpTimestamp>,
    pub transmit_time_utc: Option<DateTime<Utc>>,
}

impl NtpPacket {
    pub fn estimate_struct_size(&self) -> u32 {
        let mut size: u32 = 0;

        size += size_of::<IpAddr>() as u32 * 2;
        size += size_of::<u16>() as u32 * 2;
        size += size_of::<u32>() as u32 * 2;

        size += size_of::<NtpPacketType>() as u32;

        size += size_of::<u8>() as u32 * 4;
        size += size_of::<i8>() as u32 * 2;

        size += size_of::<f64>() as u32 * 2;

        size += size_of::<DateTime<Utc>>() as u32;

        const STRING_OVERHEAD: u32 = 0;

        if let Some(s) = &self.source_mac {
            size += STRING_OVERHEAD + s.len() as u32;
        }
        if let Some(s) = &self.destination_mac {
            size += STRING_OVERHEAD + s.len() as u32;
        }

        if self.reference_timestamp.is_some() {
            size += size_of::<NtpTimestamp>() as u32;
        }
        if self.origin_timestamp.is_some() {
            size += size_of::<NtpTimestamp>() as u32;
        }
        if self.receive_timestamp.is_some() {
            size += size_of::<NtpTimestamp>() as u32;
        }
        if self.transmit_timestamp.is_some() {
            size += size_of::<NtpTimestamp>() as u32;
        }

        if self.transmit_time_utc.is_some() {
            size += size_of::<DateTime<Utc>>() as u32;
        }

        size
    }
}
