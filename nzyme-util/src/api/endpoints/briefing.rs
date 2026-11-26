use crate::api::client::{ApiClient, Typed};
use crate::api::endpoints::Endpoint;
use crate::api::error::ApiError;
use crate::api::types::BriefingResponse;

pub const FIND_BRIEFING: Endpoint = Endpoint { method: "GET", path: "/api/briefing/{type}" };
pub const MARK_BRIEFING_SEEN: Endpoint = Endpoint { method: "PUT", path: "/api/briefing/{type}/seen" };

pub struct BriefingQuery<'a> {
    pub briefing_type: &'a str,
    pub organization_id: &'a str,
    pub tenant_id: &'a str,
    pub taps: Option<&'a str>,
    pub since: &'a str,
    pub time_range: Option<&'a str>,
}

/// Records the user's visit, so a later briefing in last-visit mode starts where this one ended.
pub fn find(client: &ApiClient, query: &BriefingQuery) -> Result<Typed<BriefingResponse>, ApiError> {
    let path = FIND_BRIEFING.with_param("type", &query.briefing_type.to_lowercase());

    let mut params = vec![
        ("organization_id", query.organization_id),
        ("tenant_id", query.tenant_id),
        ("since", query.since),
    ];

    if let Some(taps) = query.taps {
        params.push(("taps", taps));
    }

    if let Some(time_range) = query.time_range {
        params.push(("time_range", time_range));
    }

    client.get_typed(&path, &params)
}

pub fn mark_seen(client: &ApiClient, briefing_type: &str) -> Result<(), ApiError> {
    client.put_empty(&MARK_BRIEFING_SEEN.with_param("type", &briefing_type.to_lowercase()))
}
