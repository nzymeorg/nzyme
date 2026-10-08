use crate::api::client::ApiClient;
use crate::api::endpoints::{Endpoint, PAGE_SIZE};
use crate::api::error::ApiError;
use crate::api::types::{OrganizationDetailsResponse, OrganizationsListResponse, TenantDetailsResponse, TenantsListResponse};

pub const FIND_ORGANIZATIONS: Endpoint = Endpoint {
    method: "GET", path: "/api/system/authentication/mgmt/organizations"
};
pub const FIND_TENANTS: Endpoint = Endpoint {
    method: "GET", path: "/api/system/authentication/mgmt/organizations/show/{organizationId}/tenants"
};

/// Lists all organizations, walking all pages. Requires super administrator permissions.
pub fn find_all(client: &ApiClient) -> Result<Vec<OrganizationDetailsResponse>, ApiError> {
    let mut all = Vec::new();
    let mut offset = 0usize;

    loop {
        let page: OrganizationsListResponse = client.get_typed(
            FIND_ORGANIZATIONS.path,
            &[("limit", &PAGE_SIZE.to_string()), ("offset", &offset.to_string())]
        )?.data;

        let received = page.organizations.len();
        all.extend(page.organizations);
        offset += received;

        let total = page.count.unwrap_or(0).max(0) as usize;
        if received == 0 || offset >= total {
            break;
        }
    }

    Ok(all)
}

/// Lists all tenants of an organization, walking all pages. Requires organization administrator permissions.
pub fn find_tenants(client: &ApiClient, organization_id: &str) -> Result<Vec<TenantDetailsResponse>, ApiError> {
    let path = FIND_TENANTS.with_param("organizationId", organization_id);
    let mut all = Vec::new();
    let mut offset = 0usize;

    loop {
        let page: TenantsListResponse = client.get_typed(
            &path,
            &[("limit", &PAGE_SIZE.to_string()), ("offset", &offset.to_string())]
        )?.data;

        let received = page.tenants.len();
        all.extend(page.tenants);
        offset += received;

        let total = page.count.unwrap_or(0).max(0) as usize;
        if received == 0 || offset >= total {
            break;
        }
    }

    Ok(all)
}
