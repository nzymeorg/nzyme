//! Response types of the Nzyme REST API. Only the fields the utility actually displays are declared, and every
//! field is optional so that a server with a slightly different version still produces useful output.

use std::collections::BTreeMap;
use serde::Deserialize;

#[derive(Deserialize, Debug)]
pub struct NodesListResponse {
    #[serde(default)]
    pub nodes: Vec<NodeResponse>,
}

#[derive(Deserialize, Debug)]
pub struct NodeResponse {
    pub uuid: Option<String>,
    pub name: Option<String>,
    pub version: Option<String>,
    pub active: Option<bool>,
    pub deleted: Option<bool>,
    pub is_ephemeral: Option<bool>,
    pub last_seen: Option<String>,
    pub clock_drift_ms: Option<i64>,
    pub cycle: Option<i64>,
    pub cpu_system_load: Option<f64>,
    pub cpu_thread_count: Option<i32>,
    pub memory_bytes_total: Option<i64>,
    pub memory_bytes_used: Option<i64>,
    pub heap_bytes_total: Option<i64>,
    pub heap_bytes_used: Option<i64>,
    pub process_start_time: Option<String>,
    pub process_virtual_size: Option<i64>,
    pub http_listen_uri: Option<String>,
    pub http_external_uri: Option<String>,
    pub tls_cert_expiration_date: Option<String>,
    pub tls_cert_fingerprint: Option<String>,
    pub os_information: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct TapListResponse {
    #[serde(default)]
    pub taps: Vec<TapDetailsResponse>,
}

#[derive(Deserialize, Debug)]
pub struct TapDetailsResponse {
    pub uuid: Option<String>,
    pub name: Option<String>,
    pub description: Option<String>,
    pub version: Option<String>,
    pub active: Option<bool>,
    pub last_report: Option<String>,
    pub remote_address: Option<String>,
    pub location_name: Option<String>,
    pub floor_name: Option<String>,
    pub clock_drift_ms: Option<i64>,
    pub cpu_load: Option<f64>,
    pub memory_total: Option<i64>,
    pub memory_used: Option<i64>,
    pub processed_bytes: Option<TotalWithAverageResponse>,
    #[serde(default)]
    pub captures: Vec<CaptureDetailsResponse>,
    #[serde(default)]
    pub buses: Vec<BusDetailsResponse>,
}

#[derive(Deserialize, Debug)]
pub struct TotalWithAverageResponse {
    pub total: Option<i64>,
    pub average: Option<i64>,
}

#[derive(Deserialize, Debug)]
pub struct CaptureDetailsResponse {
    pub interface_name: Option<String>,
    pub capture_type: Option<String>,
    pub is_running: Option<bool>,
    pub received: Option<i64>,
    pub dropped_buffer: Option<i64>,
    pub dropped_interface: Option<i64>,
}

#[derive(Deserialize, Debug)]
pub struct BusDetailsResponse {
    pub name: Option<String>,
    #[serde(default)]
    pub channels: Vec<ChannelDetailsResponse>,
}

#[derive(Deserialize, Debug)]
pub struct ChannelDetailsResponse {
    pub name: Option<String>,
    pub capacity: Option<i64>,
    pub watermark: Option<i64>,
    pub errors: Option<TotalWithAverageResponse>,
    pub throughput_bytes: Option<TotalWithAverageResponse>,
    pub throughput_messages: Option<TotalWithAverageResponse>,
}

#[derive(Deserialize, Debug)]
pub struct TapHighLevelInformationListResponse {
    #[serde(default)]
    pub taps: Vec<TapHighLevelInformationDetailsResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct TapHighLevelInformationDetailsResponse {
    pub uuid: Option<String>,
    pub name: Option<String>,
    pub location_name: Option<String>,
    pub floor_name: Option<String>,
    pub is_online: Option<bool>,
}

#[derive(Deserialize, Debug)]
pub struct TapMetricsResponse {
    #[serde(default)]
    pub gauges: BTreeMap<String, TapMetricsGaugeResponse>,
    #[serde(default)]
    pub timers: BTreeMap<String, TapMetricsTimerResponse>,
}

#[derive(Deserialize, Debug)]
pub struct TapMetricsGaugeResponse {
    pub metric_value: Option<f64>,
    pub created_at: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct TapMetricsTimerResponse {
    pub mean: Option<f64>,
    pub p99: Option<f64>,
    pub created_at: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct OrganizationsListResponse {
    pub count: Option<i64>,
    #[serde(default)]
    pub organizations: Vec<OrganizationDetailsResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct OrganizationDetailsResponse {
    pub id: String,
    pub name: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct TenantsListResponse {
    pub count: Option<i64>,
    #[serde(default)]
    pub tenants: Vec<TenantDetailsResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct TenantDetailsResponse {
    pub id: String,
    pub name: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct BssidListResponse {
    pub total: Option<i64>,
    #[serde(default)]
    pub bssids: Vec<BssidSummaryDetailsResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BssidSummaryDetailsResponse {
    pub bssid: Option<Dot11MacAddressResponse>,
    /// The server aggregates these lists with SQL array functions and some of them can contain null entries.
    #[serde(default)]
    pub advertised_ssid_names: Vec<Option<String>>,
    #[serde(default)]
    pub security_protocols: Vec<Option<String>>,
    #[serde(default)]
    pub infrastructure_types: Vec<Option<String>>,
    pub signal_strength_average: Option<f64>,
    pub client_count: Option<i64>,
    pub has_hidden_ssid_advertisements: Option<bool>,
    pub last_seen: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct Dot11MacAddressResponse {
    pub address: Option<String>,
    pub oui: Option<String>,
    pub is_randomized: Option<bool>,
    pub context: Option<Dot11MacAddressContextResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct Dot11MacAddressContextResponse {
    pub name: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct BssidAndSsidHistogramResponse {
    #[serde(default)]
    pub values: BTreeMap<String, BssidAndSsidHistogramValueResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BssidAndSsidHistogramValueResponse {
    pub timestamp: Option<String>,
    pub bssid_count: Option<i64>,
    pub ssid_count: Option<i64>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bssid_lists_tolerate_null_entries() {
        let json = r#"{"total":1,"bssids":[{"bssid":{"address":"AA:BB:CC:DD:EE:FF","oui":null,"is_randomized":false,"context":null},
            "advertised_ssid_names":[null,"net"],"security_protocols":[],"infrastructure_types":[null],
            "signal_strength_average":-50.0,"client_count":0,"has_hidden_ssid_advertisements":true,
            "first_seen":null,"last_seen":"2026-10-07T17:00:00.000Z","fingerprints":[]}]}"#;

        let parsed: BssidListResponse = serde_json::from_str(json).unwrap();
        let b = &parsed.bssids[0];
        assert_eq!(b.advertised_ssid_names, vec![None, Some("net".to_string())]);
        assert_eq!(b.infrastructure_types, vec![None]);
    }
}

#[derive(Deserialize, Debug)]
pub struct DetectionAlertListResponse {
    pub total: Option<i64>,
    #[serde(default)]
    pub alerts: Vec<DetectionAlertDetailsResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct DetectionAlertDetailsResponse {
    pub id: Option<String>,
    pub detection_type: Option<String>,
    pub subsystem: Option<String>,
    pub details: Option<String>,
    pub is_active: Option<bool>,
    pub created_at: Option<String>,
    pub last_seen: Option<String>,
    pub tap_id: Option<String>,
    /// Attribute values are strings in practice, but accept anything the server sends.
    #[serde(default)]
    pub attributes: BTreeMap<String, serde_json::Value>,
}

#[derive(Deserialize, Debug)]
pub struct UserProfileDetailsResponse {
    pub email: Option<String>,
    pub unit_system: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct LocationSummaryResponse {
    pub id: Option<String>,
    pub name: Option<String>,
    pub description: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub timezone: Option<String>,
    pub tap_count: Option<i64>,
    #[serde(default)]
    pub taps: Vec<TapHighLevelInformationDetailsResponse>,
    #[serde(default)]
    pub floors: Vec<LocationFloorDetailsResponse>,
    /// Null when the calling user lacks the alerts_view permission.
    pub alert_count: Option<i64>,
    #[serde(default)]
    pub alerts: Vec<DetectionAlertDetailsResponse>,
    /// Null for locations without coordinates.
    pub environment: Option<LocationEnvironmentDataResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct LocationFloorDetailsResponse {
    pub name: Option<String>,
    pub number: Option<i64>,
    pub tap_count: Option<i64>,
    pub has_floor_plan: Option<bool>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct LocationEnvironmentDataResponse {
    pub station_id: Option<String>,
    pub metar: Option<String>,
    pub condition: Option<LocationEnvironmentConditionDetailsResponse>,
    /// Degrees Celsius.
    pub temperature: Option<f64>,
    /// Kilometers per hour.
    pub wind_speed: Option<f64>,
    pub wind_gust: Option<f64>,
    /// Meters.
    pub visibility: Option<f64>,
    #[serde(default)]
    pub alerts: Vec<LocationEnvironmentAlertDetailsResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct LocationEnvironmentConditionDetailsResponse {
    pub display_name: Option<String>,
    pub severity: Option<i64>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct LocationEnvironmentAlertDetailsResponse {
    pub event: Option<String>,
    pub severity: Option<String>,
    pub headline: Option<String>,
    pub expires: Option<String>,
}
