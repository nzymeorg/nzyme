use crate::api::client::{ApiClient, Typed};
use crate::api::endpoints::{Endpoint, PAGE_SIZE};
use crate::api::error::ApiError;
use crate::api::types::{BssidAndSsidHistogramResponse, BssidListResponse, BssidSummaryDetailsResponse};

pub const FIND_BSSIDS: Endpoint = Endpoint { method: "GET", path: "/api/dot11/networks/bssids" };
pub const FIND_BSSID_HISTOGRAM: Endpoint = Endpoint { method: "GET", path: "/api/dot11/networks/bssids/histogram" };

/// Value for the `taps` parameter that selects all taps the user can access. An omitted parameter selects
/// no taps at all.
pub const ALL_TAPS: &str = "*";

/// Builds the `time_range` query parameter for the last `minutes` minutes. The server expects the same JSON the
/// web interface time range picker produces.
pub fn relative_time_range(minutes: u32) -> String {
    format!("{{\"type\":\"relative\",\"minutes\":{}}}", minutes)
}

/// Lists the BSSIDs recorded in the time range, sorted by average signal strength, walking all pages up to
/// `max_results`. Returns the rows and the server-side total.
pub fn find_bssids(client: &ApiClient, time_range: &str, taps: &str, max_results: usize)
    -> Result<(Vec<BssidSummaryDetailsResponse>, i64), ApiError> {
    let mut all = Vec::new();
    let mut offset = 0usize;
    let total;

    loop {
        let page: Typed<BssidListResponse> = client.get_typed(FIND_BSSIDS.path, &[
            ("time_range", time_range),
            ("taps", taps),
            ("limit", &PAGE_SIZE.to_string()),
            ("offset", &offset.to_string()),
        ])?;

        let page_total = page.data.total.unwrap_or(0);
        let received = page.data.bssids.len();
        all.extend(page.data.bssids);
        offset += received;

        if received == 0 || offset >= page_total.max(0) as usize || all.len() >= max_results {
            total = page_total;
            break;
        }
    }

    Ok((all, total))
}

/// Returns the distinct BSSID and SSID counts per time bucket for the time range.
pub fn find_bssid_histogram(client: &ApiClient, time_range: &str, taps: &str)
    -> Result<Typed<BssidAndSsidHistogramResponse>, ApiError> {
    client.get_typed(FIND_BSSID_HISTOGRAM.path, &[("time_range", time_range), ("taps", taps)])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn builds_relative_time_range() {
        let tr = relative_time_range(1440);
        let parsed: serde_json::Value = serde_json::from_str(&tr).unwrap();
        assert_eq!(parsed["type"], "relative");
        assert_eq!(parsed["minutes"], 1440);
    }
}
