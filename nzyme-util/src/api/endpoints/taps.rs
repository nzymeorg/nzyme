use crate::api::client::{ApiClient, Typed};
use crate::api::endpoints::Endpoint;
use crate::api::error::ApiError;
use crate::api::types::{TapHighLevelInformationListResponse, TapListResponse, TapMetricsResponse};

pub const FIND_TAPS: Endpoint = Endpoint { method: "GET", path: "/api/taps" };
pub const FIND_TAPS_HIGH_LEVEL: Endpoint = Endpoint { method: "GET", path: "/api/taps/highlevel" };
pub const FIND_TAP_METRICS: Endpoint = Endpoint { method: "GET", path: "/api/taps/show/{uuid}/metrics" };

/// Lists all taps of a tenant with full details. Requires organization administrator permissions.
pub fn find_all(client: &ApiClient, organization_id: &str, tenant_id: &str)
    -> Result<Typed<TapListResponse>, ApiError> {
    client.get_typed(FIND_TAPS.path, &[("organization_id", organization_id), ("tenant_id", tenant_id)])
}

/// Lists the taps of a tenant that the calling user can access, with name, location and online status only.
/// Available to any user.
pub fn find_all_high_level(client: &ApiClient, organization_id: &str, tenant_id: &str)
    -> Result<Typed<TapHighLevelInformationListResponse>, ApiError> {
    client.get_typed(FIND_TAPS_HIGH_LEVEL.path, &[("organization_id", organization_id), ("tenant_id", tenant_id)])
}

/// Returns the current gauge and timer metrics of a tap. Requires organization administrator permissions.
pub fn find_metrics(client: &ApiClient, uuid: &str) -> Result<Typed<TapMetricsResponse>, ApiError> {
    client.get_typed(&FIND_TAP_METRICS.with_param("uuid", uuid), &[])
}
