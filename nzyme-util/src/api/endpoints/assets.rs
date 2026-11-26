use serde_json::json;

use crate::api::client::{ApiClient, Typed};
use crate::api::endpoints::Endpoint;
use crate::api::error::ApiError;
use crate::api::types::{
    AssetActivityResponse, AssetAlertsResponse, AssetAttributesResponse, AssetCommunicationResponse,
    AssetDetailsResponse, AssetExclusionsResponse, AssetFindingsResponse, AssetHostnamesListResponse,
    AssetIpAddressesListResponse, AssetProfileSummaryResponse, AssetRisksResponse, AssetTimelineResponse,
    AssetsListResponse,
};

pub const FIND_ASSETS: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets" };
pub const FIND_ASSET: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}" };
pub const FIND_ASSET_HOSTNAMES: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}/hostnames" };
pub const FIND_ASSET_IP_ADDRESSES: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}/ip_addresses" };
pub const FIND_ASSET_PROFILE_SUMMARY: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}/profile/summary" };
pub const FIND_ASSET_PROFILE_ATTRIBUTES: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}/profile/attributes" };
pub const FIND_ASSET_PROFILE_ACTIVITY: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}/profile/activity" };
pub const FIND_ASSET_PROFILE_COMMUNICATION: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}/profile/communication" };
pub const FIND_ASSET_PROFILE_TIMELINE: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}/profile/timeline" };
pub const FIND_ASSET_PROFILE_ALERTS: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}/profile/alerts" };
pub const FIND_ASSET_PROFILE_FINDINGS: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}/profile/findings" };
pub const FIND_ASSET_PROFILE_EXCLUSIONS: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}/profile/exclusions" };
pub const FIND_ASSET_PROFILE_RISKS: Endpoint = Endpoint { method: "GET", path: "/api/ethernet/assets/show/{asset_id}/profile/risks" };

pub const ALL_TIME: &str = r#"{"type":"named","name":"all_time"}"#;

pub struct AssetRef<'a> {
    pub asset_id: &'a str,
    pub organization_id: &'a str,
    pub tenant_id: &'a str,
}

impl<'a> AssetRef<'a> {
    fn scope(&self) -> [(&'a str, &'a str); 2] {
        [("organization_id", self.organization_id), ("tenant_id", self.tenant_id)]
    }
}

pub fn find_by_mac(client: &ApiClient, organization_id: &str, tenant_id: &str, mac: &str)
    -> Result<Typed<AssetsListResponse>, ApiError> {
    let filters = json!({"mac": [{"field": "mac", "operator": "equals", "value": mac}]}).to_string();
    client.get_typed(FIND_ASSETS.path, &[
        ("organization_id", organization_id),
        ("tenant_id", tenant_id),
        ("time_range", ALL_TIME),
        ("filters", &filters),
        ("limit", "5"),
        ("offset", "0"),
    ])
}

pub fn find_one(client: &ApiClient, asset: &AssetRef) -> Result<Typed<AssetDetailsResponse>, ApiError> {
    client.get_typed(&FIND_ASSET.with_param("asset_id", asset.asset_id), &asset.scope())
}

pub fn find_hostnames(client: &ApiClient, asset: &AssetRef, limit: usize)
    -> Result<Typed<AssetHostnamesListResponse>, ApiError> {
    let limit = limit.to_string();
    client.get_typed(&FIND_ASSET_HOSTNAMES.with_param("asset_id", asset.asset_id), &[
        ("organization_id", asset.organization_id),
        ("tenant_id", asset.tenant_id),
        ("time_range", ALL_TIME),
        ("order_column", "last_seen"),
        ("order_direction", "DESC"),
        ("limit", &limit),
        ("offset", "0"),
    ])
}

pub fn find_ip_addresses(client: &ApiClient, asset: &AssetRef, limit: usize)
    -> Result<Typed<AssetIpAddressesListResponse>, ApiError> {
    let limit = limit.to_string();
    client.get_typed(&FIND_ASSET_IP_ADDRESSES.with_param("asset_id", asset.asset_id), &[
        ("organization_id", asset.organization_id),
        ("tenant_id", asset.tenant_id),
        ("time_range", ALL_TIME),
        ("order_column", "last_seen"),
        ("order_direction", "DESC"),
        ("limit", &limit),
        ("offset", "0"),
    ])
}

pub fn find_profile_summary(client: &ApiClient, asset: &AssetRef) -> Result<Typed<AssetProfileSummaryResponse>, ApiError> {
    client.get_typed(&FIND_ASSET_PROFILE_SUMMARY.with_param("asset_id", asset.asset_id), &asset.scope())
}

pub fn find_profile_attributes(client: &ApiClient, asset: &AssetRef) -> Result<Typed<AssetAttributesResponse>, ApiError> {
    client.get_typed(&FIND_ASSET_PROFILE_ATTRIBUTES.with_param("asset_id", asset.asset_id), &asset.scope())
}

/// `tz_offset` is minutes east of UTC.
pub fn find_profile_activity(client: &ApiClient, asset: &AssetRef, days: u32, tz_offset: i32)
    -> Result<Typed<AssetActivityResponse>, ApiError> {
    let days = days.to_string();
    let tz = tz_offset.to_string();
    client.get_typed(&FIND_ASSET_PROFILE_ACTIVITY.with_param("asset_id", asset.asset_id), &[
        ("organization_id", asset.organization_id),
        ("tenant_id", asset.tenant_id),
        ("days", &days),
        ("tz_offset", &tz),
    ])
}

pub fn find_profile_communication(client: &ApiClient, asset: &AssetRef, time_range: &str, limit: usize)
    -> Result<Typed<AssetCommunicationResponse>, ApiError> {
    let limit = limit.to_string();
    client.get_typed(&FIND_ASSET_PROFILE_COMMUNICATION.with_param("asset_id", asset.asset_id), &[
        ("organization_id", asset.organization_id),
        ("tenant_id", asset.tenant_id),
        ("time_range", time_range),
        ("limit", &limit),
    ])
}

pub fn find_profile_timeline(client: &ApiClient, asset: &AssetRef, time_range: &str, limit: usize)
    -> Result<Typed<AssetTimelineResponse>, ApiError> {
    let limit = limit.to_string();
    client.get_typed(&FIND_ASSET_PROFILE_TIMELINE.with_param("asset_id", asset.asset_id), &[
        ("organization_id", asset.organization_id),
        ("tenant_id", asset.tenant_id),
        ("time_range", time_range),
        ("order_by", "timestamp"),
        ("order_direction", "DESC"),
        ("limit", &limit),
        ("offset", "0"),
    ])
}

pub fn find_profile_alerts(client: &ApiClient, asset: &AssetRef) -> Result<Typed<AssetAlertsResponse>, ApiError> {
    client.get_typed(&FIND_ASSET_PROFILE_ALERTS.with_param("asset_id", asset.asset_id), &asset.scope())
}

pub fn find_profile_findings(client: &ApiClient, asset: &AssetRef) -> Result<Typed<AssetFindingsResponse>, ApiError> {
    client.get_typed(&FIND_ASSET_PROFILE_FINDINGS.with_param("asset_id", asset.asset_id), &asset.scope())
}

pub fn find_profile_exclusions(client: &ApiClient, asset: &AssetRef) -> Result<Typed<AssetExclusionsResponse>, ApiError> {
    client.get_typed(&FIND_ASSET_PROFILE_EXCLUSIONS.with_param("asset_id", asset.asset_id), &asset.scope())
}

pub fn find_profile_risks(client: &ApiClient, asset: &AssetRef, time_range: &str)
    -> Result<Typed<AssetRisksResponse>, ApiError> {
    client.get_typed(&FIND_ASSET_PROFILE_RISKS.with_param("asset_id", asset.asset_id), &[
        ("organization_id", asset.organization_id),
        ("tenant_id", asset.tenant_id),
        ("time_range", time_range),
    ])
}
