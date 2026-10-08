use crate::api::client::{ApiClient, Typed};
use crate::api::endpoints::Endpoint;
use crate::api::error::ApiError;
use crate::api::types::UserProfileDetailsResponse;

pub const FIND_OWN_PROFILE: Endpoint = Endpoint { method: "GET", path: "/api/user/profile" };

/// Returns the profile of the user who owns the API key, including their unit system.
pub fn find_own_profile(client: &ApiClient) -> Result<Typed<UserProfileDetailsResponse>, ApiError> {
    client.get_typed(FIND_OWN_PROFILE.path, &[])
}
