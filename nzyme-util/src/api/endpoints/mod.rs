pub mod nodes;
pub mod taps;
pub mod organizations;
pub mod dot11;
pub mod alerts;
pub mod locations;
pub mod user;

pub struct Endpoint {
    /// Only used by the spec test; requests are all GET.
    #[cfg_attr(not(test), allow(dead_code))]
    pub method: &'static str,
    /// Path template with `{param}` placeholders, exactly as in the OpenAPI spec.
    pub path: &'static str,
}

impl Endpoint {
    /// Replaces a single `{param}` placeholder with a URL-safe value.
    pub fn with_param(&self, name: &str, value: &str) -> String {
        self.path.replace(&format!("{{{}}}", name), &percent_encode(value))
    }
}

#[cfg_attr(not(test), allow(dead_code))]
pub const ALL_ENDPOINTS: &[&Endpoint] = &[
    &nodes::FIND_NODES,
    &taps::FIND_TAPS,
    &taps::FIND_TAPS_HIGH_LEVEL,
    &taps::FIND_TAP_METRICS,
    &organizations::FIND_ORGANIZATIONS,
    &organizations::FIND_TENANTS,
    &dot11::FIND_BSSIDS,
    &dot11::FIND_BSSID_HISTOGRAM,
    &alerts::FIND_ALERTS,
    &locations::FIND_LOCATIONS,
    &user::FIND_OWN_PROFILE,
];

/// Page size used when walking paginated list endpoints.
pub const PAGE_SIZE: usize = 250;

pub(crate) fn percent_encode(value: &str) -> String {
    let mut out = String::with_capacity(value.len());
    for b in value.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => out.push(b as char),
            _ => out.push_str(&format!("%{:02X}", b)),
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::path::Path;

    #[test]
    fn encodes_path_params() {
        assert_eq!(nodes::FIND_NODES.with_param("x", "y"), nodes::FIND_NODES.path);
        assert_eq!(
            taps::FIND_TAP_METRICS.with_param("uuid", "a b/c"),
            "/api/taps/show/a%20b%2Fc/metrics"
        );
    }

    #[test]
    fn all_endpoints_exist_in_openapi_spec() {
        let spec_path = Path::new(env!("CARGO_MANIFEST_DIR")).join("../openapi/openapi.json");
        let spec: serde_json::Value = serde_json::from_str(
            &fs::read_to_string(&spec_path).expect("Could not read committed OpenAPI spec")
        ).expect("Could not parse committed OpenAPI spec");

        for endpoint in ALL_ENDPOINTS {
            let operation = &spec["paths"][endpoint.path][endpoint.method.to_lowercase()];
            assert!(
                operation.is_object(),
                "Endpoint [{} {}] is not in the OpenAPI spec at [{}]. The API changed and the client must follow.",
                endpoint.method, endpoint.path, spec_path.display()
            );
        }
    }
}
