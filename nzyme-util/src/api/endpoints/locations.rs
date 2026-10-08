use crate::api::client::{ApiClient, Typed};
use crate::api::endpoints::Endpoint;
use crate::api::error::ApiError;
use crate::api::types::LocationSummaryResponse;

pub const FIND_LOCATIONS: Endpoint = Endpoint {
    method: "GET", path: "/api/locations/organizations/{organization_id}/tenants/{tenant_id}"
};

/// Returns a summary of every location of a tenant with floors, taps, environmental data and active alerts.
pub fn find_all(client: &ApiClient, organization_id: &str, tenant_id: &str)
    -> Result<Typed<Vec<LocationSummaryResponse>>, ApiError> {
    let path = FIND_LOCATIONS
        .with_param("organization_id", organization_id)
        .replace("{tenant_id}", &super::percent_encode(tenant_id));
    client.get_typed(&path, &[])
}
