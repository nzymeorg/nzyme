use crate::api::client::{ApiClient, Typed};
use crate::api::endpoints::Endpoint;
use crate::api::error::ApiError;
use crate::api::types::{SearchGroupResponse, SearchResponse};

pub const SEARCH: Endpoint = Endpoint { method: "GET", path: "/api/search" };
pub const SEARCH_SOURCE: Endpoint = Endpoint { method: "GET", path: "/api/search/sources/{source}" };

pub const MINIMUM_QUERY_LENGTH: usize = 2;
pub const MAXIMUM_QUERY_LENGTH: usize = 128;
pub const MAXIMUM_OFFSET: i64 = 1000;

pub struct SearchQuery<'a> {
    pub query: &'a str,
    pub organization_id: &'a str,
    pub tenant_id: &'a str,
    pub limit: u32,
    pub time_range: Option<&'a str>,
}

/// Searches configuration and findings. Sources of recorded network data that apply to the query come back as
/// pending and are searched one by one with `search_source`.
pub fn search(client: &ApiClient, query: &SearchQuery) -> Result<Typed<SearchResponse>, ApiError> {
    let limit = query.limit.to_string();
    let mut params = vec![
        ("query", query.query),
        ("organization_id", query.organization_id),
        ("tenant_id", query.tenant_id),
        ("limit", limit.as_str()),
    ];

    if let Some(time_range) = query.time_range {
        params.push(("time_range", time_range));
    }

    client.get_typed(SEARCH.path, &params)
}

/// Not found if the source does not exist, does not apply to the query or is not accessible by the calling user.
pub fn search_source(client: &ApiClient, query: &SearchQuery, source: &str, offset: u32)
    -> Result<Typed<SearchGroupResponse>, ApiError> {
    let limit = query.limit.to_string();
    let offset = offset.to_string();
    let mut params = vec![
        ("query", query.query),
        ("organization_id", query.organization_id),
        ("tenant_id", query.tenant_id),
        ("limit", limit.as_str()),
        ("offset", offset.as_str()),
    ];

    if let Some(time_range) = query.time_range {
        params.push(("time_range", time_range));
    }

    client.get_typed(&SEARCH_SOURCE.with_param("source", source), &params)
}
