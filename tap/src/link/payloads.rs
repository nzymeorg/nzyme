use std::collections::HashMap;
use serde::{Serialize, ser::SerializeStruct};
use chrono::{Utc, DateTime};
use sha2::{Sha256, Digest};
use crate::alerting::alert_types::{AlertAttribute, Dot11AlertType};
use crate::configuration::Configuration;

#[derive(Serialize)]
pub struct StatusReport {
    pub version: String,
    pub timestamp: DateTime<Utc>,
    pub processed_bytes: TotalWithAverage,
    pub buses: Vec<BusReport>,
    pub captures: Vec<CaptureReport>,
    pub system_metrics: SystemMetricsReport,
    pub gauges_long: HashMap<String, i128>,
    pub gauges_float: HashMap<String, f32>,
    pub timers: HashMap<String, TimerReport>,
    pub log_counts: HashMap<String, u128>,
    pub engagement_logs: Vec<EngagementLogReport>,
    pub rpi: Option<String>,
    pub configuration: ConfigurationReport
}

// Note that this is also used for the configuration print CLI flag.
#[derive(Serialize)]
pub struct ConfigurationReport {
    pub general: ConfigurationReportGeneral,
    pub performance: ConfigurationReportPerformance,
    pub misc: ConfigurationReportMisc,
    pub protocols: ConfigurationReportProtocols,
}

#[derive(Serialize)]
pub struct ConfigurationReportGeneral {
    pub leader_secret: String,
    pub leader_uri: String,
    pub accept_insecure_certs: bool,
    pub limina: Option<bool>
}

#[derive(Serialize)]
pub struct ConfigurationReportPerformance {
    pub ethernet_brokers: i32,
    pub wifi_brokers: i32,
    pub wifi_broker_buffer_capacity: usize,
    pub ethernet_broker_buffer_capacity: usize,
    pub bluetooth_devices_pipeline_size: Option<i32>,
    pub shared_protocol_processors: Option<i32>
}

#[derive(Serialize)]
pub struct ConfigurationReportMisc {
    pub training_period_minutes: i32,
    pub context_mac_ip_retention_hours: i32,
    pub context_mac_hostname_retention_hours: i32,
    pub panic_log_file_path: Option<String>
}

#[derive(Serialize)]
pub struct ConfigurationReportProtocols {
    pub wifi: ConfigurationReportProtocolsWifi,
    pub tcp: ConfigurationReportProtocolsTcp,
    pub udp: ConfigurationReportProtocolsUdp,
    pub dns: ConfigurationReportProtocolsDns,
    pub arp: ConfigurationReportProtocolsArp,
    pub ssh: ConfigurationReportProtocolsSsh,
    pub socks: ConfigurationReportProtocolsSocks,
    pub dhcpv4: ConfigurationReportProtocolsDhcpv4,
    pub uav_remote_id: ConfigurationReportProtocolsUavRemoteId,
}

#[derive(Serialize)]
pub struct ConfigurationReportProtocolsWifi {
    pub pipeline_size: i32,
    pub processors: Option<i32>
}

#[derive(Serialize)]
pub struct ConfigurationReportProtocolsTcp {
    pub pipeline_size: i32,
    pub processors: Option<i32>,
    pub reassembly_buffer_size: i32,
    pub session_timeout_seconds: i32
}

#[derive(Serialize)]
pub struct ConfigurationReportProtocolsUdp {
    pub pipeline_size: i32,
    pub processors: Option<i32>,
}

#[derive(Serialize)]
pub struct ConfigurationReportProtocolsDns {
    pub pipeline_size: i32,
    pub entropy_zscore_threshold: f32
}

#[derive(Serialize)]
pub struct ConfigurationReportProtocolsArp {
    pub pipeline_size: i32,
    pub poisoning_monitor: bool,
    pub poisoning_window_seconds: i32
}

#[derive(Serialize)]
pub struct ConfigurationReportProtocolsSsh {
    pub pipeline_size: i32
}

#[derive(Serialize)]
pub struct ConfigurationReportProtocolsSocks {
    pub pipeline_size: i32
}

#[derive(Serialize)]
pub struct ConfigurationReportProtocolsDhcpv4 {
    pub pipeline_size: i32
}

#[derive(Serialize)]
pub struct ConfigurationReportProtocolsUavRemoteId {
    pub pipeline_size: i32
}

impl TryFrom<Configuration> for ConfigurationReport {
    type Error = ();

    fn try_from(c: Configuration) -> Result<ConfigurationReport, ()> {
        Ok(ConfigurationReport {
            general: ConfigurationReportGeneral {
                leader_secret: privacy_hash(&c.general.leader_secret),
                leader_uri: privacy_hash(&c.general.leader_uri),
                accept_insecure_certs: c.general.accept_insecure_certs,
                limina: c.general.limina,
            },
            performance: ConfigurationReportPerformance {
                ethernet_brokers: c.performance.ethernet_brokers,
                wifi_brokers: c.performance.wifi_brokers,
                wifi_broker_buffer_capacity: c.performance.wifi_broker_buffer_capacity,
                ethernet_broker_buffer_capacity: c.performance.ethernet_broker_buffer_capacity,
                bluetooth_devices_pipeline_size: c.performance.bluetooth_devices_pipeline_size,
                shared_protocol_processors: c.performance.shared_protocol_processors
            },
            misc: ConfigurationReportMisc {
                training_period_minutes: c.misc.training_period_minutes,
                context_mac_ip_retention_hours: c.misc.context_mac_ip_retention_hours,
                context_mac_hostname_retention_hours: c.misc.context_mac_hostname_retention_hours,
                panic_log_file_path: c.misc.panic_log_file_path
            },
            protocols: ConfigurationReportProtocols {
                wifi: ConfigurationReportProtocolsWifi {
                    pipeline_size: c.protocols.wifi.pipeline_size,
                    processors: c.protocols.wifi.processors
                },
                tcp: ConfigurationReportProtocolsTcp {
                    pipeline_size: c.protocols.tcp.pipeline_size,
                    processors: c.protocols.tcp.processors,
                    reassembly_buffer_size: c.protocols.tcp.reassembly_buffer_size,
                    session_timeout_seconds: c.protocols.tcp.session_timeout_seconds
                },
                udp: ConfigurationReportProtocolsUdp {
                    pipeline_size: c.protocols.udp.pipeline_size,
                    processors: c.protocols.udp.processors
                },
                dns: ConfigurationReportProtocolsDns {
                    pipeline_size: c.protocols.dns.pipeline_size,
                    entropy_zscore_threshold: c.protocols.dns.entropy_zscore_threshold
                },
                arp: ConfigurationReportProtocolsArp {
                    pipeline_size: c.protocols.arp.pipeline_size,
                    poisoning_monitor: c.protocols.arp.poisoning_monitor,
                    poisoning_window_seconds: c.protocols.arp.poisoning_window_seconds
                },
                ssh: ConfigurationReportProtocolsSsh {
                    pipeline_size: c.protocols.ssh.pipeline_size
                },
                socks: ConfigurationReportProtocolsSocks {
                    pipeline_size: c.protocols.socks.pipeline_size
                },
                dhcpv4: ConfigurationReportProtocolsDhcpv4 {
                    pipeline_size: c.protocols.dhcpv4.pipeline_size
                },
                uav_remote_id: ConfigurationReportProtocolsUavRemoteId {
                    pipeline_size: c.protocols.uav_remote_id.pipeline_size
                }
            }
        })
    }
}

fn privacy_hash(value: &str) -> String {
    let mut hasher = Sha256::new();

    sha2::Digest::update(&mut hasher, value);
    let  result = hasher.finalize();

    let hex: String = result.iter().map(|b| format!("{:02x}", b)).collect();
    format!("PRIVACY_HASHED___SHA56_{}", hex)
}

#[derive(Serialize)]
pub struct BusReport {
    pub name: String,
    pub channels: Vec<ChannelReport>
}

#[derive(Serialize)]
pub struct ChannelReport {
    pub name: String,
    pub capacity: u128,
    pub watermark: u128,
    pub errors: TotalWithAverage,
    pub throughput_bytes: TotalWithAverage,
    pub throughput_messages: TotalWithAverage,
}

#[derive(Serialize)]
pub struct TotalWithAverage {
    pub total: u128,
    pub average: u128
}

#[derive(Serialize)]
pub struct SystemMetricsReport {
    pub cpu_load: f32,
    pub cpu_cores_load: HashMap<u8, f32>,
    pub memory_total: u64,
    pub memory_free: u64,
    pub rpi_temperature: Option<f32>
}

#[derive(Serialize)]
pub struct TimerReport {
    pub mean: f64,
    pub p99: f64
}

#[derive(Serialize)]
pub struct CaptureReport {
    pub capture_type: String,
    pub interface_name: String,
    pub is_running: bool,
    pub received: u128,
    pub dropped_buffer: u128,
    pub dropped_interface: u128
}

#[derive(Serialize)]
pub struct EngagementLogReport {
    pub timestamp: DateTime<Utc>,
    pub message: String
}

pub struct TablesReport {
    pub timestamp: DateTime<Utc>,
    pub arp: HashMap<String, HashMap<String, u128>>,
    pub dns: DnsTableReport,
    pub dot11: Dot11TableReport
}

#[derive(Serialize)]
pub struct DnsTableReport {
    pub ips: HashMap<String, DnsIpStatisticsReport>,
    pub entropy_log: Vec<DNSEntropyLog>,
    pub queries: Vec<DNSLogReport>,
    pub responses: Vec<DNSLogReport>
}

#[derive(Serialize)]
pub struct DnsIpStatisticsReport {
    pub request_count: u128,
    pub request_bytes: u128,
    pub response_count: u128,
    pub response_bytes: u128,
    pub nxdomain_count: u128
}

#[derive(Serialize)]
pub struct DNSEntropyLog {
    pub transaction_id: u16,
    pub entropy: f32,
    pub zscore: f32,
    pub entropy_mean: f32,
    pub timestamp: DateTime<Utc>
}

#[derive(Serialize)]
pub struct DNSLogReport {
    pub transaction_id: Option<u16>,
    pub client_address: String,
    pub server_address: String,
    pub client_mac: Option<String>,
    pub server_mac: Option<String>,
    pub client_port: u16,
    pub server_port: u16,
    pub data_value: String,
    pub data_value_etld: Option<String>,
    pub data_type: String,
    pub timestamp: DateTime<Utc>
}

#[derive(Serialize)]
pub struct Dot11TableReport {
    pub bssids: HashMap<String, BssidReport>,
    pub clients: HashMap<String, Dot11ClientReport>,
    pub disco: Dot11DiscoReport,
    pub alerts: Vec<Dot11AlertReport>
}

#[derive(Serialize)]
pub struct BssidReport {
    pub advertised_networks: HashMap<String, AdvertisedNetworkReport>,
    pub clients: HashMap<String, Dot11ClientStatisticsReport>,
    pub hidden_ssid_frames: u128,
    pub signal_strength: SignalStrengthReport,
    pub fingerprints: Vec<String>,
}

#[derive(Serialize)]
pub struct Dot11ClientReport {
    pub probe_request_ssids: HashMap<String, u128>,
    pub wildcard_probe_requests: u128,
    pub signal_strength: SignalStrengthReport
}

#[derive(Serialize)]
pub struct Dot11AlertReport {
    pub alert_type: Dot11AlertType,
    pub signal_strength: i8,
    pub attributes: HashMap<String, AlertAttribute>
}

#[derive(Serialize)]
pub struct Dot11DiscoReport {
    pub deauth: HashMap<String, DiscoTransmitterReport>,
    pub disassoc: HashMap<String, DiscoTransmitterReport>
}

#[derive(Serialize)]
pub struct DiscoTransmitterReport {
    pub bssid: String,
    pub sent_frames: u128,
    pub receivers: HashMap<String, u128>,
    pub signal_strength_average: f64,
    pub signal_strength_min: i32,
    pub signal_strength_max: i32
}

#[derive(Serialize)]
pub struct AdvertisedNetworkReport {
    pub security: Vec<SecurityInformationReport>,
    pub fingerprints: Vec<String>,
    pub beacon_advertisements: u128,
    pub proberesp_advertisements: u128,
    pub rates: Vec<f32>,
    pub wps: Vec<bool>,
    pub signal_strength: SignalStrengthReport,
    pub signal_histogram: HashMap<u16, HashMap<i8, u128>>,
    pub infrastructure_types: Vec<String>,
    pub channel_statistics: HashMap<u16, HashMap<String, Dot11ChannelStatisticsReport>>
}

#[derive(Serialize)]
pub struct SignalStrengthReport {
    pub min: i8,
    pub max: i8,
    pub average: f32
}

#[derive(Serialize)]
pub struct SecurityInformationReport {
    pub protocols: Vec<String>,
    pub suites: Dot11CipherSuites,
    pub pmf: PmfReport
}

#[derive(Serialize)]
pub struct Dot11CipherSuites {
    pub group_cipher: String,
    pub pairwise_ciphers: Vec<String>,
    pub key_management_modes: Vec<String>
}

#[derive(Serialize)]
pub enum PmfReport {
    Required,
    Optional,
    Disabled,
    Unavailable
}

#[derive(Serialize)]
pub struct Dot11ChannelStatisticsReport {
    pub frames: u128,
    pub bytes: u128
}

#[derive(Serialize)]
pub struct Dot11ClientStatisticsReport {
    pub tx_frames: u128,
    pub tx_bytes: u128,
    pub rx_frames: u128,
    pub rx_bytes: u128,
    pub signal_strength: SignalStrengthReport
}

#[derive(Serialize)]
pub struct NodeHelloReport {
    pub wifi_device_assignments: HashMap<String, Vec<WiFiSupportedFrequencyReport>>,
    pub wifi_device_cycle_times: HashMap<String, u64>
}

#[derive(Serialize)]
pub struct WiFiSupportedFrequencyReport {
    pub frequency: u32,
    pub channel_widths: Vec<String>
}

impl TotalWithAverage {

    pub fn from_metric(m: &crate::metrics::TotalWithAverage) -> Self {
        TotalWithAverage {
            total: m.total,
            average: m.avg
        }
    }

}

impl Serialize for TablesReport {

    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where S: serde::Serializer {
        let mut state = serializer.serialize_struct("TablesReport", 3)?;
        state.serialize_field("timestamp", &self.timestamp.to_rfc3339())?;
        state.serialize_field("arp", &self.arp)?;
        state.serialize_field("dns", &self.dns)?;
        state.serialize_field("remoteid", &self.dot11)?;
        state.end()
    }

}
