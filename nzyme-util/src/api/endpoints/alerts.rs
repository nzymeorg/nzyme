use crate::api::client::ApiClient;
use crate::api::endpoints::{Endpoint, PAGE_SIZE};
use crate::api::error::ApiError;
use crate::api::types::{DetectionAlertDetailsResponse, DetectionAlertListResponse};

pub const FIND_ALERTS: Endpoint = Endpoint { method: "GET", path: "/api/alerts" };

pub struct AlertsPage {
    pub alerts: Vec<DetectionAlertDetailsResponse>,
    pub total: i64,
}

/// Lists the detection alerts of a tenant, newest activity first, walking pages up to `max_results`. Requires the
/// `alerts_view` feature permission.
pub fn find_all(client: &ApiClient, organization_id: &str, tenant_id: &str, max_results: usize)
    -> Result<AlertsPage, ApiError> {
    let mut all = Vec::new();
    let mut offset = 0usize;
    let total;

    loop {
        let page: DetectionAlertListResponse = client.get_typed(FIND_ALERTS.path, &[
            ("organization_id", organization_id),
            ("tenant_id", tenant_id),
            ("limit", &PAGE_SIZE.to_string()),
            ("offset", &offset.to_string()),
        ])?.data;

        let received = page.alerts.len();
        all.extend(page.alerts);
        offset += received;

        let page_total = page.total.unwrap_or(0).max(0);
        if received == 0 || offset >= page_total as usize || all.len() >= max_results {
            total = page_total;
            break;
        }
    }

    Ok(AlertsPage { alerts: all, total })
}
