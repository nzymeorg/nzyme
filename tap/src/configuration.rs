use std::collections::HashMap;
use std::env;
use std::fs::read_to_string;
use std::net::SocketAddr;
use std::str::FromStr;
use anyhow::{Result, bail, Error};
use cidr::{Ipv4Cidr, Ipv6Cidr};
use regex::Regex;
use reqwest::Url;
use serde::Deserialize;

#[derive(Debug, Clone, Deserialize)]
pub struct Configuration {
    pub general: General,
    pub wifi_interfaces: Option<HashMap<String, WifiInterface>>,
    pub ethernet_interfaces: Option<HashMap<String, EthernetInterface>>,
    pub rawip_interfaces: Option<HashMap<String, RawIpInterface>>,
    pub bluetooth_interfaces: Option<HashMap<String, BluetoothInterface>>,
    pub wifi_engagement_interfaces: Option<HashMap<String, WiFiEngagementInterfaceConfiguration>>,
    pub portal_integrity_checks: Option<HashMap<String, PortalIntegrityConfiguration>>,
    pub performance: Performance,
    pub protocols: Protocols,
    pub misc: Misc,
}

#[derive(Debug, Clone, Deserialize)]
pub struct General {
    pub leader_secret: String,
    pub leader_uri: String,
    pub accept_insecure_certs: bool,
    pub limina: Option<bool>
}

#[derive(Debug, Clone, Deserialize)]
pub struct EthernetInterface {
    pub active: bool,
    pub networks: Option<Vec<EthernetInterfaceNetwork>>
}

#[derive(Debug, Clone, Deserialize)]
pub struct RawIpInterface {
    pub active: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct EthernetInterfaceNetwork {
    pub cidr: String,
    pub dns_servers: Vec<SocketAddr>,
    pub injection_interface: Option<String>,

    /*
     * The IP address and MAC address will be automatically determined if not set. In almost
     * all cases, it should not have to be set manually. That's why it's not in the example
     * config and in such a flat format. The user should usually not have to worry about it
     * and this is purely a fallback for edge cases where it needs to be set manually.
     */
    pub injection_interface_ip_address: Option<String>,
    pub injection_interface_mac_address: Option<String>
}

#[derive(Debug, Clone, Deserialize)]
pub struct WifiInterface {
    pub active: bool,
    pub channel_width_hopping_mode: Option<ChannelWidthHoppingMode>,
    pub disable_hopper: Option<bool>,
    pub channels_2g: Option<Vec<u16>>,
    pub channels_5g: Option<Vec<u16>>,
    pub channels_6g: Option<Vec<u16>>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct WiFiEngagementInterfaceConfiguration {
    pub active: bool,
    pub engage: Vec<EngagementTarget>,
    pub supported_channels_2g: Vec<u16>,
    pub supported_channels_5g: Vec<u16>,
    pub supported_channels_6g: Vec<u16>
}

#[derive(Debug, Clone, Eq, PartialEq, Deserialize)]
pub enum EngagementTarget {
    UAV
}

#[allow(non_camel_case_types)]
#[derive(Debug, Clone, Deserialize, Eq, PartialEq)]
pub enum ChannelWidthHoppingMode {
    full, limited
}

#[derive(Debug, Clone, Deserialize)]
pub struct BluetoothInterface {
    pub active: bool,
    pub bt_classic_enabled: bool,
    pub bt_le_enabled: bool,
    pub discovery_period_seconds: i32,
    pub dbus_method_call_timeout_seconds: i32
}

#[derive(Debug, Clone, Deserialize)]
pub struct PortalIntegrityConfiguration {
    pub active: bool,
    pub interface: String,
    pub mac: String,
    pub interval_minutes: u64,
    pub control_urls: Vec<String>,
    pub max_redirects: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct Performance {
    pub ethernet_brokers: i32,
    pub wifi_brokers: i32,
    pub wifi_broker_buffer_capacity: usize,
    pub ethernet_broker_buffer_capacity: usize,
    pub bluetooth_devices_pipeline_size: Option<i32>,
    pub shared_protocol_processors: Option<i32>
}

#[derive(Debug, Clone, Deserialize)]
pub struct Misc {
    pub training_period_minutes: i32,
    pub context_mac_ip_retention_hours: i32,
    pub context_mac_hostname_retention_hours: i32,
    pub panic_log_file_path: Option<String>
}

#[derive(Debug, Clone, Deserialize)]
pub struct Protocols {
    pub wifi: ProtocolsWiFi,
    pub tcp: ProtocolsTcp,
    pub udp: ProtocolsUdp,
    pub dns: ProtocolsDns,
    pub arp: ProtocolsArp,
    pub ssh: ProtocolsSsh,
    pub socks: ProtocolsSocks,
    pub dhcpv4: ProtocolsDhcpv4,
    pub ntp: ProtocolsNtp,
    pub rtsp: ProtocolsRtsp,
    pub stun: ProtocolsStun,
    pub uav_remote_id: ProtocolsUavRemoteId,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsWiFi {
    pub pipeline_size: i32,
    pub processors: Option<i32>
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsTcp {
    pub pipeline_size: i32,
    pub processors: Option<i32>,
    pub reassembly_buffer_size: i32,
    pub session_timeout_seconds: i32
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsUdp {
    pub pipeline_size: i32,
    pub processors: Option<i32>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsDns {
    pub pipeline_size: i32,
    pub entropy_zscore_threshold: f32
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsArp {
    pub pipeline_size: i32,
    pub poisoning_monitor: bool,
    pub poisoning_window_seconds: i32
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsSsh {
    pub pipeline_size: i32
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsSocks {
    pub pipeline_size: i32
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsDhcpv4 {
    pub pipeline_size: i32
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsNtp {
    pub pipeline_size: i32
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsRtsp {
    pub pipeline_size: i32
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsStun {
    pub pipeline_size: i32
}

#[derive(Debug, Clone, Deserialize)]
pub struct ProtocolsUavRemoteId {
    pub pipeline_size: i32
}

pub fn load(path: String) -> Result<Configuration, Error> {
    let config_str = match read_to_string(path) {
        Ok(c) => c,
        Err(e) => bail!("Could not read configuration file. {}", e),
    };

    let substituted_config_str = match substitute_environment_variables(&config_str) {
        Ok(scs) => scs,
        Err(e) => bail!("Could not substitute environment variables in configuration file: {}", e)
    };

    let doc: Configuration = match toml::from_str(&substituted_config_str) {
        Ok(d) => d,
        Err(e) => bail!("Could not parse configuration. {}", e),
    };

    // TODO: The validations here suck and are repetitive. Refactor this.

    if doc.general.leader_secret.len() < 64 {
        bail!("Configuration variable `leader_secret` must be at least 64 characters long.");
    }

    if doc.misc.training_period_minutes < 0 {
        bail!("Configuration variable `training_period_minutes` must be set to a value greater \
            or equal to 0.");
    }

    if doc.misc.context_mac_ip_retention_hours < 0 {
        bail!("Configuration variable `context_mac_ip_retention_hours` must be set to a value \
            greater or equal to 0.");
    }

    if doc.misc.context_mac_hostname_retention_hours < 0 {
        bail!("Configuration variable `context_mac_hostname_retention_hours` must be set to a \
            value greater or equal to 0.");
    }

    if doc.performance.ethernet_brokers <= 0 {
        bail!("Configuration variable `ethernet_brokers` must be set to a value greater than 0.");
    }

    if doc.performance.wifi_brokers <= 0 {
        bail!("Configuration variable `wifi_brokers` must be set to a value greater than 0.");
    }

    if doc.performance.wifi_broker_buffer_capacity == 0 {
        bail!("Configuration variable `wifi_broker_buffer_capacity` must be set to a value \
            greater than 0.");
    }

    if doc.performance.ethernet_broker_buffer_capacity == 0 {
        bail!("Configuration variable `ethernet_broker_buffer_capacity` must be set to a value \
            greater than 0.");
    }

    if let Some(size) = doc.performance.bluetooth_devices_pipeline_size {
        if size <= 0 {
            bail!("Configuration variable `bluetooth_devices_pipeline_size` must be set to a \
                value greater than 0.");
        }
    }

    // Ethernet captures.

    // Ensure CIDRs are valid.
    if let Some(ethernet_interfaces) = doc.clone().ethernet_interfaces {
        for (interface_name, interface) in ethernet_interfaces {
            if let Some(networks) = &interface.networks {
                for network in networks {
                    if !is_valid_cidr(&network.cidr) {
                        bail!("CIDR [{}] of ethernet interface [{}] is invalid.",
                            network.cidr, interface_name);
                    }
                }
            }
        }
    }

    // Engagement interfaces.
    // WiFi.
    if doc.wifi_interfaces.is_none() && doc.wifi_engagement_interfaces.is_some() {
        bail!("You must have at least one WiFi monitoring interface before configuring a WiFi \
            engagement interface.");
    }

    if let Some(engagement_interfaces) = doc.wifi_engagement_interfaces.clone() {
        for engagement_interface_name in engagement_interfaces.keys() {
            if doc.wifi_interfaces.clone().unwrap().contains_key(engagement_interface_name) {
                bail!("Engagement interface [{}] already configured as monitoring interface.",
                    engagement_interface_name);
            }
        }
    }

    // Protocols.

    // WiFi.
    if doc.protocols.wifi.pipeline_size <= 0 {
        bail!("Configuration variable `protocols.wifi.pipeline_size` must be set to a value \
            greater than 0.");
    }
    if doc.protocols.wifi.processors.is_some() && doc.protocols.wifi.processors.unwrap() <= 0 {
        bail!("Configuration variable `protocols.wifi.processors` must be set to a value greater \
            than 0.");
    }

    // TCP.
    if doc.protocols.tcp.pipeline_size <= 0 {
        bail!("Configuration variable `protocols.tcp.pipeline_size` must be set to a value \
            greater than 0.");
    }
    if doc.protocols.tcp.session_timeout_seconds <= 0 {
        bail!("Configuration variable `protocols.tcp.session_timeout_seconds` must be set to a \
            value greater than 0.");
    }
    if doc.protocols.tcp.reassembly_buffer_size <= 0 {
        bail!("Configuration variable `protocols.tcp.reassembly_buffer_size` must be set to a \
            value greater than 0.");
    }
    if doc.protocols.tcp.processors.is_some() && doc.protocols.tcp.processors.unwrap() <= 0 {
        bail!("Configuration variable `protocols.tcp.processors` must be set to a value greater \
            than 0.");
    }

    // UDP.
    if doc.protocols.udp.pipeline_size <= 0 {
        bail!("Configuration variable `protocols.udp.pipeline_size` must be set to a value \
            greater than 0.");
    }
    if doc.protocols.udp.processors.is_some() && doc.protocols.udp.processors.unwrap() <= 0 {
        bail!("Configuration variable `protocols.tcp.processors` must be set to a value greater \
            than 0.");
    }

    // DNS.
    if doc.protocols.dns.pipeline_size <= 0 {
        bail!("Configuration variable `protocols.dns.pipeline_size` must be set to a value \
            greater than 0.");
    }
    if doc.protocols.dns.entropy_zscore_threshold <= 0.0 {
        bail!("Configuration variable `protocols.dns.entropy_zscore_threshold` must be set to a \
            value greater than 0.0.");
    }

    // ARP.
    if doc.protocols.arp.pipeline_size <= 0 {
        bail!("Configuration variable `protocols.arp.pipeline_size` must be set to a value \
            greater than 0.");
    }

    if doc.protocols.arp.poisoning_window_seconds <= 0 {
        bail!("Configuration variable `protocols.arp.poisoning_window_seconds` must be set to a \
            value greater than 0.");
    }

    // SSH.
    if doc.protocols.ssh.pipeline_size <= 0 {
        bail!("Configuration variable `protocols.ssh.pipeline_size` must be set to a value \
            greater than 0.");
    }

    // SOCKS.
    if doc.protocols.socks.pipeline_size <= 0 {
        bail!("Configuration variable `protocols.socks.pipeline_size` must be set to a value \
            greater than 0.");
    }

    // DHCPv4.
    if doc.protocols.dhcpv4.pipeline_size <= 0 {
        bail!("Configuration variable `protocols.dhcpv4.pipeline_size` must be set to a value \
            greater than 0.");
    }
    
    // NTP.
    if doc.protocols.ntp.pipeline_size <= 0 {
        bail!("Configuration variable `protocols.ntp.pipeline_size` must be set to a value \
            greater than 0.");
    }

    // RTSP.
    if doc.protocols.rtsp.pipeline_size <= 0 {
        bail!("Configuration variable `protocols.rtsp.pipeline_size` must be set to a value \
            greater than 0.");
    }

    // UAV Remote ID.
    if doc.protocols.uav_remote_id.pipeline_size <= 0 {
        bail!("Configuration variable `protocols.uav_remote_id.pipeline_size` must be set to a \
            value greater than 0.");
    }

    // Validate WiFi interfaces configuration
    if let Some(wifi_interfaces) = &doc.wifi_interfaces {
        let mut assigned_channels_2g: Vec<u16> = Vec::new();
        let mut assigned_channels_5g: Vec<u16> = Vec::new();
        let mut assigned_channels_6g: Vec<u16> = Vec::new();
        for interface in wifi_interfaces.values() {
            if !interface.active {
                continue;
            }

            // Make sure every channel is only assigned once.
            if let Some(channels) = &interface.channels_2g {
                for channel in channels {
                    if assigned_channels_2g.contains(&channel) {
                        bail!("WiFi channel <{}> on 2G band already assigned to another interface. \
                        Channels can only be assigned once.", channel);
                    } else {
                        assigned_channels_2g.push(*channel);
                    }
                }
            }

            if let Some(channels) = &interface.channels_5g {
                for channel in channels {
                    if assigned_channels_5g.contains(&channel) {
                        bail!("WiFi channel <{}> on 5G band already assigned to another interface. \
                        Channels can only be assigned once.", channel);
                    } else {
                        assigned_channels_5g.push(*channel);
                    }
                }
            }

            if let Some(channels) = &interface.channels_6g {
                for channel in channels {
                    if assigned_channels_6g.contains(channel) {
                        bail!("WiFi channel <{}> on 6G band already assigned to another interface. \
                        Channels can only be assigned once.", channel);
                    } else {
                        assigned_channels_6g.push(*channel);
                    }
                }
            }
        }
    }

    // Validate Bluetooth interfaces configuration.
    if let Some(bluetooth_interfaces) = &doc.bluetooth_interfaces {
        for interface in bluetooth_interfaces.values() {
            if interface.discovery_period_seconds <= 0 {
                bail!("Configuration variable `bluetooth_interfaces.*.discovery_period_seconds` \
                    must be set to a value greater than 0.");
            }

            if interface.dbus_method_call_timeout_seconds <= 0 {
                bail!("Configuration variable \
                    `bluetooth_interfaces.*.dbus_method_call_timeout_seconds` must be set to a \
                        value greater than 0.");
            }

            if !interface.bt_classic_enabled && !interface.bt_le_enabled {
                bail!("Bluetooth interface cannot have both bluetooth classic and LE disabled.")
            }
        }
    }

    if let Some(portal_checks) = &doc.portal_integrity_checks {
        for check in portal_checks.values() {
            if check.interface.trim().is_empty() {
                bail!("portal_integrity.interface must not be empty");
            }
            if check.mac.trim().is_empty() {
                bail!("portal_integrity.mac must not be empty");
            }
            if check.control_urls.is_empty() {
                bail!("portal_integrity.control_urls must contain at least one URL");
            }
            if check.interval_minutes == 0 {
                bail!("portal_integrity.interval_minutes must be >= 1");
            }

        }
    }

    // Test if URL can be parsed.
    match Url::parse(&doc.general.leader_uri) {
        Ok(..) => {},
        Err(e) => {
            bail!("Could not parse configuration variable `leader_uri` into URL. {}", e);
        }
    };

    Ok(doc)
}

fn substitute_environment_variables(config_str: &String) -> Result<String, Error> {
    // Extract all environment variables to substitute.
    let regex = Regex::new(r"\$\{(.+)\}");
    let mut requested_env_names: Vec<String> = Vec::new();
    for (_, [id]) in regex.unwrap().captures_iter(config_str).map(|c| c. extract()) {
        requested_env_names.push(id.to_string());
    }

    let mut substituted_string: String = config_str.clone();
    for requested in requested_env_names {
        // Check if requested environment variable exists.
        let env_value = match env::var(requested.clone()) {
            Ok(v) => v,
            Err(e) => bail!("Cannot substitute environment variable [{}]: {}", requested, e)
        };

        // Avoid endless recursion if someone passes a ENV variable replacement string as replacement.
        if env_value.contains('$') {
            bail!("Invalid character [$] in value of environment variable [{}]",  requested);
        }

        let mut search_var = "${".to_string();
        search_var.push_str(&requested);
        search_var.push('}');

        substituted_string = substituted_string.replace(search_var.as_str(), &env_value);
    }

    Ok(substituted_string.clone())
}


fn is_valid_cidr(cidr_str: &str) -> bool {
    if !cidr_str.contains('/') {
        return false;
    }

    // Try parsing as IPv4 CIDR
    if Ipv4Cidr::from_str(cidr_str).is_ok() {
        return true;
    }

    // Try parsing as IPv6 CIDR
    if Ipv6Cidr::from_str(cidr_str).is_ok() {
        return true;
    }

    // If neither succeeded, it's not a valid CIDR
    false
}