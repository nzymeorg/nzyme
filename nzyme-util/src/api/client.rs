use std::fs;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;
use serde::de::DeserializeOwned;
use serde_json::Value;
use ureq::Agent;
use ureq::tls::{Certificate, RootCerts, TlsConfig};
use crate::api::error::ApiError;

pub const API_KEY_PREFIX: &str = "nzk_";

const REQUEST_TIMEOUT: Duration = Duration::from_secs(30);
const MAX_ERROR_BODY_CHARS: usize = 300;

pub struct ClientConfig {
    pub server: String,
    pub api_key: String,
    pub insecure: bool,
    pub ca_file: Option<PathBuf>,
}

/// A decoded API response together with the raw JSON it was decoded from. The raw value is what `--json` prints,
/// so human-readable and machine-readable output always come from the same single request.
pub struct Typed<T> {
    pub raw: Value,
    pub data: T,
}

pub struct ApiClient {
    agent: Agent,
    base_url: String,
    api_key: String,
}

impl ApiClient {
    pub fn new(config: ClientConfig) -> Result<Self, ApiError> {
        let base_url = normalize_base_url(&config.server)?;

        let mut tls = TlsConfig::builder();

        if config.insecure {
            tls = tls.disable_verification(true);
        }

        if let Some(ca_file) = &config.ca_file {
            tls = tls.root_certs(RootCerts::Specific(Arc::new(load_ca_file(ca_file)?)));
        }

        let agent = Agent::config_builder()
            .http_status_as_error(false)
            .timeout_global(Some(REQUEST_TIMEOUT))
            .user_agent(format!("nzyme-util/{}", env!("CARGO_PKG_VERSION")))
            .tls_config(tls.build())
            .build()
            .new_agent();

        Ok(ApiClient { agent, base_url, api_key: config.api_key })
    }

    pub fn base_url(&self) -> &str {
        &self.base_url
    }

    /// Performs a GET request and returns the response body as JSON.
    pub fn get(&self, path: &str, query: &[(&str, &str)]) -> Result<Value, ApiError> {
        let url = format!("{}{}", self.base_url, path);

        let response = self.agent.get(&url)
            .header("Authorization", format!("Bearer {}", self.api_key))
            .header("Accept", "application/json")
            .query_pairs(query.iter().copied())
            .call()
            .map_err(|e| ApiError::Transport(e.to_string()))?;

        let status = response.status().as_u16();
        let body = response.into_body()
            .read_to_string()
            .map_err(|e| ApiError::Transport(format!("Could not read response body: {}", e)))?;

        match status {
            200..=299 => serde_json::from_str(&body).map_err(|e| ApiError::Decode(e.to_string())),
            401 => Err(ApiError::Unauthorized),
            403 => Err(ApiError::Forbidden),
            404 => Err(ApiError::NotFound(path.to_string())),
            _ => Err(ApiError::Status(status, body.chars().take(MAX_ERROR_BODY_CHARS).collect())),
        }
    }

    /// Performs a GET request and decodes the response into `T`, keeping the raw JSON around.
    pub fn get_typed<T: DeserializeOwned>(&self, path: &str, query: &[(&str, &str)]) -> Result<Typed<T>, ApiError> {
        let raw = self.get(path, query)?;
        let data = serde_json::from_value(raw.clone())
            .map_err(|e| ApiError::Decode(format!("{} (response of {})", e, path)))?;

        Ok(Typed { raw, data })
    }
}

pub fn is_api_key(candidate: &str) -> bool {
    candidate.starts_with(API_KEY_PREFIX) && candidate.len() > API_KEY_PREFIX.len()
}

fn normalize_base_url(server: &str) -> Result<String, ApiError> {
    let trimmed = server.trim().trim_end_matches('/');

    if trimmed.is_empty() {
        return Err(ApiError::InvalidUrl("URL is empty.".to_string()));
    }

    if !(trimmed.starts_with("https://") || trimmed.starts_with("http://")) {
        return Err(ApiError::InvalidUrl(format!("[{}] must start with https:// or http://.", trimmed)));
    }

    Ok(trimmed.to_string())
}

/// Loads all certificates from a PEM bundle. The ureq parser only accepts a single certificate per call, so the
/// file is split into its PEM blocks first.
fn load_ca_file(path: &PathBuf) -> Result<Vec<Certificate<'static>>, ApiError> {
    let content = fs::read_to_string(path)
        .map_err(|e| ApiError::InvalidCaFile(format!("Could not read [{}]: {}", path.display(), e)))?;

    let mut certs = Vec::new();
    let mut remaining = content.as_str();

    const BEGIN: &str = "-----BEGIN CERTIFICATE-----";
    const END: &str = "-----END CERTIFICATE-----";

    while let Some(start) = remaining.find(BEGIN) {
        let after_start = &remaining[start..];
        let end = after_start.find(END)
            .ok_or_else(|| ApiError::InvalidCaFile(format!("Unterminated certificate block in [{}].", path.display())))?
            + END.len();

        let block = &after_start[..end];
        let cert = Certificate::from_pem(block.as_bytes())
            .map_err(|e| ApiError::InvalidCaFile(format!("Could not parse certificate in [{}]: {}", path.display(), e)))?;
        certs.push(cert);

        remaining = &after_start[end..];
    }

    if certs.is_empty() {
        return Err(ApiError::InvalidCaFile(format!("No certificates found in [{}].", path.display())));
    }

    Ok(certs)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_base_url() {
        assert_eq!(normalize_base_url("https://nzyme.example.org:22900/").unwrap(), "https://nzyme.example.org:22900");
        assert_eq!(normalize_base_url("  http://localhost:22900  ").unwrap(), "http://localhost:22900");
        assert!(normalize_base_url("nzyme.example.org").is_err());
        assert!(normalize_base_url("").is_err());
    }

    #[test]
    fn recognizes_api_keys() {
        assert!(is_api_key("nzk_abc"));
        assert!(!is_api_key("nzk_"));
        assert!(!is_api_key("abc"));
        assert!(!is_api_key(""));
    }
}
