use crate::api::client::{ApiClient, Typed};
use crate::api::endpoints::Endpoint;
use crate::api::error::ApiError;
use crate::api::types::NodesListResponse;

pub const FIND_NODES: Endpoint = Endpoint { method: "GET", path: "/api/system/cluster/nodes" };

/// Lists all cluster nodes with their current status and metrics. Requires super administrator permissions.
pub fn find_all(client: &ApiClient) -> Result<Typed<NodesListResponse>, ApiError> {
    client.get_typed(FIND_NODES.path, &[])
}
