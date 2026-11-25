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

/// Accepts `null` where a list or map is expected. The server serializes some optional collections as `null`
/// instead of leaving them out, and `#[serde(default)]` alone only covers missing fields.
fn null_as_default<'de, D, T>(deserializer: D) -> Result<T, D::Error>
    where D: serde::Deserializer<'de>, T: Default + Deserialize<'de> {
    Ok(Option::<T>::deserialize(deserializer)?.unwrap_or_default())
}

#[derive(Deserialize, Debug)]
pub struct BriefingResponse {
    pub generated_at: Option<String>,
    pub window: Option<BriefingWindowResponse>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub attention: Vec<BriefingAttentionItemResponse>,
    pub monitoring: Option<BriefingMonitoringResponse>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub changes: Vec<BriefingChangeGroupResponse>,
    /// Histograms keyed by series. The main briefing nests the per-subsystem series one level deeper than the
    /// subsystem briefings do, so this stays untyped and is unpacked by the caller.
    #[serde(default, deserialize_with = "null_as_default")]
    pub activity: BTreeMap<String, serde_json::Value>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub locations: Vec<BriefingLocationResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingWindowResponse {
    pub mode: Option<String>,
    pub from: Option<String>,
    pub to: Option<String>,
    pub duration_ms: Option<i64>,
    pub is_first_visit: Option<bool>,
    pub capped: Option<bool>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingAttentionItemResponse {
    pub severity: Option<String>,
    pub subsystem: Option<String>,
    pub title: Option<String>,
    pub text: Option<String>,
    pub count: Option<i64>,
}

#[derive(Deserialize, Debug)]
pub struct BriefingMonitoringResponse {
    pub taps_online: Option<i64>,
    pub taps_total: Option<i64>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub taps: Vec<BriefingTapResponse>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub checks: Vec<BriefingCheckResponse>,
    pub health_indicators: Option<BTreeMap<String, String>>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingTapResponse {
    pub name: Option<String>,
    pub online: Option<bool>,
    pub last_report: Option<String>,
    pub location_name: Option<String>,
    pub active_alerts: Option<i64>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub captures: Vec<BriefingTapCaptureResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingTapCaptureResponse {
    pub is_running: Option<bool>,
    pub dropping: Option<bool>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingCheckResponse {
    pub level: Option<String>,
    pub subsystem: Option<String>,
    pub title: Option<String>,
    pub detail: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingChangeGroupResponse {
    pub subsystem: Option<String>,
    pub title: Option<String>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub tiles: Vec<BriefingChangeTileResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingChangeTileResponse {
    pub title: Option<String>,
    pub current: Option<i64>,
    pub previous: Option<i64>,
    pub higher_is_worse: Option<bool>,
    pub unit: Option<String>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub breakdown: Vec<BriefingBreakdownEntryResponse>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub samples: Vec<BriefingSampleResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingBreakdownEntryResponse {
    pub label: Option<String>,
    pub count: Option<i64>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingSampleResponse {
    pub label: Option<String>,
    pub detail: Option<String>,
    pub severity: Option<String>,
    pub timestamp: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingHistogramBucketResponse {
    pub bucket: Option<String>,
    pub value: Option<f64>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingLocationResponse {
    pub name: Option<String>,
    pub taps_online: Option<i64>,
    pub taps_total: Option<i64>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub offline_taps: Vec<String>,
    pub alert_count: Option<i64>,
    pub environment: Option<BriefingLocationEnvironmentResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct BriefingLocationEnvironmentResponse {
    pub condition: Option<LocationEnvironmentConditionDetailsResponse>,
    /// Degrees Celsius.
    pub temperature: Option<f64>,
    /// Kilometers per hour.
    pub wind_speed: Option<f64>,
    pub wind_gust: Option<f64>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub alerts: Vec<String>,
}

#[cfg(test)]
mod briefing_tests {
    use super::*;

    #[test]
    fn briefing_tolerates_null_collections() {
        let json = r#"{"briefing_type":"MAIN","generated_at":"2026-11-03T20:54:17.862Z","window":null,
            "attention":null,"monitoring":{"taps_online":2,"taps_total":3,"taps":null,"checks":[],"health_indicators":null},
            "changes":[{"key":"access","tiles":[{"key":"logins","current":0,"previous":null,"breakdown":null,"samples":null}]}],
            "activity":null,"subsystems":null,"locations":[{"id":"x","offline_taps":null,"environment":{"alerts":null}}]}"#;

        let parsed: BriefingResponse = serde_json::from_str(json).unwrap();
        assert!(parsed.attention.is_empty());
        assert!(parsed.monitoring.unwrap().taps.is_empty());
        assert!(parsed.changes[0].tiles[0].breakdown.is_empty());
        assert!(parsed.locations[0].environment.as_ref().unwrap().alerts.is_empty());
    }
}

#[derive(Deserialize, Debug)]
pub struct AssetsListResponse {
    #[serde(default)]
    pub assets: Vec<AssetDetailsResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetDetailsResponse {
    pub uuid: Option<String>,
    pub mac: Option<EthernetMacAddressResponse>,
    pub oui: Option<String>,
    pub is_active: Option<bool>,
    pub name: Option<String>,
    pub dhcp_fingerprint_initial: Option<String>,
    pub dhcp_fingerprint_renew: Option<String>,
    pub dhcp_fingerprint_reboot: Option<String>,
    pub dhcp_fingerprint_rebind: Option<String>,
    pub seen_arp: Option<bool>,
    pub seen_ndp: Option<bool>,
    pub seen_dhcp: Option<bool>,
    pub seen_tcp: Option<bool>,
    pub seen_udp: Option<bool>,
    pub first_seen: Option<String>,
    pub last_seen: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct EthernetMacAddressResponse {
    pub address: Option<String>,
    pub context: Option<EthernetMacAddressContextResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct EthernetMacAddressContextResponse {
    pub name: Option<String>,
    pub description: Option<String>,
    pub notes: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct AssetHostnamesListResponse {
    pub total: Option<i64>,
    #[serde(default)]
    pub hostnames: Vec<AssetHostnameDetailsResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetHostnameDetailsResponse {
    pub hostname: Option<String>,
    pub source: Option<String>,
    pub first_seen: Option<String>,
    pub last_seen: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct AssetIpAddressesListResponse {
    pub total: Option<i64>,
    #[serde(default)]
    pub addresses: Vec<AssetIpAddressDetailsResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetIpAddressDetailsResponse {
    pub address: Option<String>,
    pub source: Option<String>,
    pub first_seen: Option<String>,
    pub last_seen: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetProfileSummaryResponse {
    pub active_alerts: Option<i64>,
    pub total_alerts: Option<i64>,
    pub exclusions: Option<i64>,
    pub last_change_at: Option<String>,
    pub last_change_type: Option<String>,
    pub last_change_value: Option<String>,
    pub last_change_detail: Option<String>,
    pub has_context: Option<bool>,
    pub randomized_mac: Option<bool>,
    #[serde(default)]
    pub taps: Vec<AssetTapResponse>,
    #[serde(default)]
    pub roles: Vec<AssetRoleResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetTapResponse {
    pub uuid: Option<String>,
    pub name: Option<String>,
    pub count: Option<i64>,
    pub first_seen: Option<String>,
    pub last_seen: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetRoleResponse {
    pub role: Option<String>,
    pub count: Option<i64>,
    pub first_seen: Option<String>,
    pub last_seen: Option<String>,
    pub dependents: Option<i64>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetAttributesResponse {
    #[serde(default)]
    pub attributes: BTreeMap<String, Vec<AssetAttributeResponse>>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetAttributeResponse {
    pub value: Option<String>,
    pub count: Option<i64>,
    pub bytes: Option<i64>,
    pub first_seen: Option<String>,
    pub last_seen: Option<String>,
    pub l4_address: Option<L4AddressResponse>,
    pub mac_address: Option<EthernetMacAddressResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct L4AddressResponse {
    pub mac: Option<EthernetMacAddressResponse>,
    pub geo: Option<L4AddressGeoResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct L4AddressGeoResponse {
    pub asn_name: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetActivityResponse {
    pub retention_days: Option<i64>,
    pub days: Option<i64>,
    #[serde(default)]
    pub heatmap: Vec<AssetActivityCellResponse>,
    #[serde(default)]
    pub calendar: Vec<AssetActivityDayResponse>,
    pub total_events: Option<i64>,
    pub total_bytes: Option<i64>,
    pub active_days: Option<i64>,
    pub longest_gap_days: Option<i64>,
    pub longest_gap_end: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetActivityCellResponse {
    pub day_of_week: Option<i64>,
    pub hour: Option<i64>,
    pub events: Option<i64>,
    pub bytes: Option<i64>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetActivityDayResponse {
    pub day: Option<String>,
    pub events: Option<i64>,
    pub bytes: Option<i64>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetCommunicationResponse {
    #[serde(default)]
    pub edges: Vec<AssetCommunicationEdgeResponse>,
    pub retention_days: Option<i64>,
    pub origin: Option<AssetCommunicationOriginResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetCommunicationOriginResponse {
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub tap_name: Option<String>,
    pub location_name: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetCommunicationEdgeResponse {
    pub direction: Option<String>,
    pub peer_type: Option<String>,
    pub peer_key: Option<String>,
    pub peer_address: Option<String>,
    pub peer_asset: Option<AssetPeerAssetResponse>,
    pub peer_asn_number: Option<i64>,
    pub peer_asn_name: Option<String>,
    pub peer_country_code: Option<String>,
    pub peer_city: Option<String>,
    pub peer_latitude: Option<f64>,
    pub peer_longitude: Option<f64>,
    pub peer: Option<L4AddressResponse>,
    pub port: Option<i64>,
    pub sessions: Option<i64>,
    pub bytes_rx: Option<i64>,
    pub bytes_tx: Option<i64>,
    pub last_seen: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetPeerAssetResponse {
    pub name: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetTimelineResponse {
    pub total: Option<i64>,
    #[serde(default)]
    pub events: Vec<AssetTimelineEventResponse>,
    pub attribute_retention_days: Option<i64>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetTimelineEventResponse {
    #[serde(rename = "type")]
    pub event_type: Option<String>,
    pub timestamp: Option<String>,
    pub value: Option<String>,
    pub detail: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetAlertsResponse {
    pub active: Option<i64>,
    #[serde(default)]
    pub alerts: Vec<AssetAlertSummaryResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetAlertSummaryResponse {
    pub detection_type: Option<String>,
    pub detection_type_description: Option<String>,
    pub role: Option<String>,
    pub count: Option<i64>,
    pub active_count: Option<i64>,
    pub last_seen: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetFindingsResponse {
    #[serde(default)]
    pub findings: Vec<AssetFindingResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetFindingResponse {
    #[serde(rename = "type")]
    pub finding_type: Option<String>,
    pub role: Option<String>,
    pub count: Option<i64>,
    pub last_seen: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetExclusionsResponse {
    #[serde(default)]
    pub exclusions: Vec<AssetExclusionMatchResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetExclusionMatchResponse {
    #[serde(rename = "type")]
    pub exclusion_type: Option<String>,
    pub name: Option<String>,
    pub enabled: Option<bool>,
    pub matched_by: Option<String>,
    pub match_count: Option<i64>,
    pub last_matched_at: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetRisksResponse {
    #[serde(default)]
    pub signals: Vec<AssetRiskSignalResponse>,
    pub tls_sessions: Option<i64>,
    #[serde(default)]
    pub tls_versions: BTreeMap<String, i64>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct AssetRiskSignalResponse {
    pub signal: Option<String>,
    pub severity: Option<String>,
    pub count: Option<i64>,
    pub detail: Option<String>,
    pub last_seen: Option<String>,
}

#[derive(Deserialize, Debug)]
pub struct SearchResponse {
    #[serde(default, deserialize_with = "null_as_default")]
    pub interpretations: Vec<SearchInterpretationResponse>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub groups: Vec<SearchGroupResponse>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub pending: Vec<SearchSourceResponse>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct SearchInterpretationResponse {
    pub kind: Option<String>,
    pub value: Option<String>,
    pub detail: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct SearchSourceResponse {
    pub source: Option<String>,
    pub title: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct SearchGroupResponse {
    pub source: Option<String>,
    pub title: Option<String>,
    pub tier: Option<String>,
    pub position: Option<i64>,
    pub status: Option<String>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub hits: Vec<SearchHitResponse>,
    pub has_more: Option<bool>,
    pub limited: Option<bool>,
    pub offset: Option<i64>,
    pub searched_from: Option<String>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct SearchHitResponse {
    pub title: Option<String>,
    pub subtitle: Option<String>,
    #[serde(default, deserialize_with = "null_as_default")]
    pub facts: Vec<SearchFactResponse>,
    pub timestamp: Option<String>,
    pub score: Option<i64>,
}

#[derive(Deserialize, Debug, Clone)]
pub struct SearchFactResponse {
    pub label: Option<String>,
    pub value: Option<String>,
}

#[cfg(test)]
mod search_tests {
    use super::*;

    #[test]
    fn search_tolerates_null_and_missing_fields() {
        let json = r#"{"query":"lab","interpretations":null,"pending":[{"source":"dns","title":"DNS","position":20}],
            "groups":[{"source":"assets","title":"Assets","tier":"INSTANT","position":0,"status":"OK","has_more":false,
            "offset":0,"limit":5,"searched_from":null,"all_link":null,"took_ms":3,
            "hits":[{"type":"ASSET","title":"AA:BB:CC:DD:EE:FF","subtitle":null,"facts":null,"timestamp":null,
            "score":100,"link":{"target":"ASSET","params":{}}}]}]}"#;

        let parsed: SearchResponse = serde_json::from_str(json).unwrap();
        assert!(parsed.interpretations.is_empty());
        assert_eq!(parsed.pending[0].source.as_deref(), Some("dns"));
        assert_eq!(parsed.groups[0].hits[0].title.as_deref(), Some("AA:BB:CC:DD:EE:FF"));
        assert!(parsed.groups[0].hits[0].facts.is_empty());
    }
}
