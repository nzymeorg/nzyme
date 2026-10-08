use std::fmt;
use crate::exit_codes::{EX_CONFIG, EX_DATAERR, EX_PERMISSION_DENIED, EX_PROTOCOL, EX_UNAVAILABLE};

#[derive(Debug)]
pub enum ApiError {
    /// The server URL is not usable.
    InvalidUrl(String),
    /// The CA file could not be read or contained no certificates.
    InvalidCaFile(String),
    /// Connection, DNS, TLS or timeout problem. The request never produced a HTTP response.
    Transport(String),
    /// HTTP 401. Nzyme also returns 401 for authenticated users that lack a required permission.
    Unauthorized,
    /// HTTP 403.
    Forbidden,
    /// HTTP 404.
    NotFound(String),
    /// Any other non-2xx status with the beginning of the response body.
    Status(u16, String),
    /// The response was not the JSON we expected.
    Decode(String),
}

impl ApiError {
    pub fn exit_code(&self) -> i32 {
        match self {
            ApiError::InvalidUrl(_) | ApiError::InvalidCaFile(_) => EX_CONFIG,
            ApiError::Transport(_) => EX_UNAVAILABLE,
            ApiError::Unauthorized | ApiError::Forbidden => EX_PERMISSION_DENIED,
            ApiError::NotFound(_) => EX_DATAERR,
            ApiError::Status(_, _) | ApiError::Decode(_) => EX_PROTOCOL,
        }
    }
}

impl fmt::Display for ApiError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ApiError::InvalidUrl(e) => write!(f, "Invalid server URL: {}", e),
            ApiError::InvalidCaFile(e) => write!(f, "Invalid CA file: {}", e),
            ApiError::Transport(e) => write!(f, "Could not reach the Nzyme REST API: {}", e),
            ApiError::Unauthorized => write!(f, "The server rejected the request (HTTP 401). The API key is \
                invalid, expired, or the user does not have the required permissions."),
            ApiError::Forbidden => write!(f, "The server denied the request (HTTP 403)."),
            ApiError::NotFound(path) => write!(f, "Not found (HTTP 404): {}", path),
            ApiError::Status(code, body) => {
                if body.is_empty() {
                    write!(f, "Unexpected HTTP status {}.", code)
                } else {
                    write!(f, "Unexpected HTTP status {}: {}", code, body)
                }
            }
            ApiError::Decode(e) => write!(f, "Could not decode the API response: {}", e),
        }
    }
}
