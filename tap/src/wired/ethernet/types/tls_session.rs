use std::mem;
use std::net::IpAddr;
use chrono::{DateTime, Utc};
use crate::protocols::detection::taggers::network_protocols::tls_tagger::TlsHandshake;
use crate::protocols::parsers::l4_key::L4Key;
use crate::protocols::parsers::tcp::tcp_tools::determine_tcp_session_state;
use crate::state::tables::tcp_table::TcpSession;
use crate::wired::packets::GenericConnectionStatus;

#[derive(Debug, Clone)]
pub struct TlsSession {
    pub handshake: TlsHandshake,
    pub connection_status: GenericConnectionStatus,
    pub tcp_session_key: L4Key,
    pub source_mac: Option<String>,
    pub destination_mac: Option<String>,
    pub source_address: IpAddr,
    pub destination_address: IpAddr,
    pub source_port: u16,
    pub destination_port: u16,
    pub bytes_count: u64,
    pub established_at: DateTime<Utc>,
    pub terminated_at: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>
}

impl TlsSession {

    pub fn build(handshake: TlsHandshake, session: &TcpSession) -> Self {
        let (connection_status, terminated_at) = determine_tcp_session_state(session);

        Self {
            handshake,
            connection_status,
            tcp_session_key: session.session_key.clone(),
            source_mac: session.source_mac.clone(),
            destination_mac: session.destination_mac.clone(),
            source_address: session.source_address,
            destination_address: session.destination_address,
            source_port: session.source_port,
            destination_port: session.destination_port,
            bytes_count: session.bytes_count(),
            established_at: session.start_time,
            terminated_at,
            most_recent_segment_time: session.most_recent_segment_time
        }
    }

    pub fn estimate_struct_size(&self) -> u32 {
        let mut size = mem::size_of::<Self>() as u32;

        if let Some(mac) = &self.source_mac {
            size += mac.len() as u32;
        }
        if let Some(mac) = &self.destination_mac {
            size += mac.len() as u32;
        }

        let handshake = &self.handshake;

        if let Some(ch) = &handshake.client_hello {
            size += ch.server_name.as_ref().map(|s| s.len()).unwrap_or(0) as u32;
            size += ((ch.supported_versions.len() + ch.ec_point_formats.len()) * 2) as u32;
            size += ((ch.cipher_suites.len() + ch.extensions.len() + ch.supported_groups.len()
                + ch.signature_algorithms.len()) * mem::size_of::<u16>()) as u32;
            size += ch.alpn.iter().map(|a| a.len() as u32).sum::<u32>();
        }

        if let Some(sh) = &handshake.server_hello {
            size += (sh.extensions.len() * mem::size_of::<u16>()) as u32;
            size += sh.alpn.as_ref().map(|s| s.len()).unwrap_or(0) as u32;
        }

        for cert in handshake.server_certificates.iter().chain(handshake.client_certificates.iter()) {
            size += cert.der.len() as u32;
            size += (cert.fingerprint_sha256.len() + cert.subject.len() + cert.issuer.len()
                + cert.serial.len() + cert.public_key_algorithm.len()
                + cert.signature_algorithm.len()) as u32;
            size += cert.subject_alternative_names.iter().map(|s| s.len() as u32).sum::<u32>();
        }

        size += (handshake.alerts.len() * mem::size_of::<u8>() * 2) as u32;

        size
    }

}

#[cfg(test)]
pub mod tests {
    use std::collections::{BTreeMap, HashSet};
    use chrono::TimeZone;
    use crate::protocols::detection::taggers::network_protocols::tls_tagger::{ClientHello, ServerHello, TlsAlert, TlsDirection};
    use crate::state::tables::tcp_table::TcpSessionState;
    use crate::wired::tls::certificate::TlsCertificate;
    use crate::wired::tls::version::TlsVersion;
    use super::*;

    pub fn test_tcp_session() -> TcpSession {
        let start = Utc.with_ymd_and_hms(2026, 10, 16, 12, 0, 0).unwrap();
        let end = Utc.with_ymd_and_hms(2026, 10, 16, 12, 0, 5).unwrap();
        let source: IpAddr = "10.0.0.10".parse().unwrap();
        let destination: IpAddr = "93.184.216.34".parse().unwrap();

        TcpSession {
            session_key: L4Key::new(source, 51000, destination, 443),
            state: TcpSessionState::ClosedFin,
            source_mac: Some("aa:bb:cc:dd:ee:01".to_string()),
            destination_mac: Some("aa:bb:cc:dd:ee:02".to_string()),
            source_address: source,
            source_port: 51000,
            destination_address: destination,
            destination_port: 443,
            start_time: start,
            end_time: Some(end),
            most_recent_segment_time: end,
            segments_count: 12,
            segments_count_incremental: 0,
            bytes_count_rx: 4000,
            bytes_count_tx: 1500,
            bytes_count_rx_incremental: 0,
            bytes_count_tx_incremental: 0,
            segments_client_to_server: BTreeMap::new(),
            segments_server_to_client: BTreeMap::new(),
            syn_ip_ttl: 64,
            syn_ip_tos: 0,
            syn_ip_df: true,
            syn_cwr: false,
            syn_ece: false,
            syn_window_size: 65535,
            syn_maximum_segment_size: Some(1460),
            syn_window_scale_multiplier: Some(7),
            syn_options: vec![],
            syn_options_truncated: false,
            syn_ip_id: 0,
            syn_timestamp_value: None,
            syn_timestamp_echo: None,
            latest_client_timestamp_value: None,
            latest_client_timestamp_time: None,
            latest_client_ip_id: None,
            segments_count_client_to_server: 6,
            syn_ack: None,
            tags: HashSet::new(),
            tagging_fingerprint: None
        }
    }

    #[test]
    fn builds_from_tcp_session() {
        let tcp = test_tcp_session();
        let tls = TlsSession::build(TlsHandshake::default(), &tcp);

        assert!(matches!(tls.connection_status, GenericConnectionStatus::Inactive));
        assert_eq!(tls.tcp_session_key, tcp.session_key);
        assert_eq!(tls.source_mac, tcp.source_mac);
        assert_eq!(tls.destination_mac, tcp.destination_mac);
        assert_eq!(tls.source_address, tcp.source_address);
        assert_eq!(tls.destination_address, tcp.destination_address);
        assert_eq!(tls.source_port, 51000);
        assert_eq!(tls.destination_port, 443);
        assert_eq!(tls.bytes_count, 5500);
        assert_eq!(tls.established_at, tcp.start_time);
        assert_eq!(tls.terminated_at, tcp.end_time);
        assert_eq!(tls.most_recent_segment_time, tcp.most_recent_segment_time);
    }

    // A TLS 1.2 session with everything filled in that the parser can produce.
    pub fn test_tls_session() -> TlsSession {
        let handshake = TlsHandshake {
            client_hello: Some(ClientHello {
                record_version: TlsVersion::Tls10,
                legacy_version: TlsVersion::Tls12,
                supported_versions: vec![TlsVersion::Tls13, TlsVersion::Tls12],
                server_name: Some("example.com".to_string()),
                cipher_suites: vec![0x1301, 0x1302, 0x1303],
                extensions: vec![0, 10, 13, 16, 43],
                supported_groups: vec![29, 23],
                ec_point_formats: vec![0],
                signature_algorithms: vec![0x0403, 0x0804],
                alpn: vec!["h2".to_string(), "http/1.1".to_string()],
                session_id_present: true,
                offers_session_ticket: false,
                offers_pre_shared_key: false,
                encrypted_client_hello: false
            }),
            server_hello: Some(ServerHello {
                record_version: TlsVersion::Tls12,
                legacy_version: TlsVersion::Tls12,
                version: TlsVersion::Tls12,
                cipher_suite: 0xC02F,
                compression_method: 0,
                extensions: vec![0, 11, 16, 23],
                alpn: Some("h2".to_string()),
                session_resumed: false
            }),
            hello_retry_request: false,
            server_certificates: vec![
                TlsCertificate::from_der(crate::wired::tls::certificate::testdata::LEAF_DER),
                TlsCertificate::from_der(crate::wired::tls::certificate::testdata::CA_DER)
            ],
            client_certificates: vec![],
            client_certificate_requested: false,
            alerts: vec![TlsAlert {
                direction: TlsDirection::ServerToClient,
                level: 1,
                description: 0,
                description_name: "close_notify",
                encrypted: false
            }],
            client_change_cipher_spec: true,
            server_change_cipher_spec: true,
            client_application_data_records: 3,
            client_application_data_bytes: 420,
            server_application_data_records: 5,
            server_application_data_bytes: 3900
        };

        TlsSession::build(handshake, &test_tcp_session())
    }

    #[test]
    fn size_estimate_grows_with_content() {
        let empty = TlsSession::build(TlsHandshake::default(), &test_tcp_session());
        let base = empty.estimate_struct_size();
        assert!(base >= mem::size_of::<TlsSession>() as u32);
        assert!(test_tls_session().estimate_struct_size() > base);
    }
}
