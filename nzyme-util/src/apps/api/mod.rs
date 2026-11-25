pub mod list_nodes_app;
pub mod list_taps_app;
pub mod tap_metrics_app;
pub mod gui;
pub mod profiles;
pub mod scopes;
pub mod briefing_app;
pub mod assets;
pub mod search_app;

use crate::api::client::ApiClient;
use crate::api::error::ApiError;
use crate::arguments::{ConnectionArgs, NamedTimeRange};
use crate::exit_codes::{EX_CONFIG, EX_USAGE};
use crate::profiles::connection;
use crate::tools::parse_iso8601_to_unix;

pub const RESET: &str = "\x1b[0m";
pub const BOLD: &str = "\x1b[1m";
pub const FG_RED: &str = "\x1b[31m";
pub const FG_GREEN: &str = "\x1b[32m";
pub const FG_YELLOW: &str = "\x1b[33m";

/// Resolves the connection settings and builds the API client. Exits with a message if anything is missing.
pub fn connect(args: &ConnectionArgs) -> ApiClient {
    let resolved = match connection::resolve(args) {
        Ok(resolved) => resolved,
        Err(e) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} {}", e);
            std::process::exit(EX_CONFIG);
        }
    };

    if resolved.config.server.starts_with("http://") {
        eprintln!("{FG_YELLOW}[!] WARNING:{RESET} Connecting over plain HTTP. The API key is sent unencrypted.");
    }

    if resolved.config.insecure {
        eprintln!("{FG_YELLOW}[!] WARNING:{RESET} TLS certificate verification is disabled.");
    }

    match ApiClient::new(resolved.config) {
        Ok(client) => client,
        Err(e) => exit_with_api_error(&e),
    }
}

pub fn exit_with_api_error(e: &ApiError) -> ! {
    eprintln!("{FG_RED}[x] ERROR:{RESET} {}", e);
    std::process::exit(e.exit_code());
}

pub fn print_json(value: &serde_json::Value) {
    match serde_json::to_string_pretty(value) {
        Ok(s) => println!("{}", s),
        Err(e) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} Could not serialize JSON: {}", e);
            std::process::exit(crate::exit_codes::EX_PROTOCOL);
        }
    }
}

/// The time range parameter of the API for a fixed window, or `None` if no option asks for one. Clap enforces
/// that at most one of them is set. Exits with a message if `--from` and `--to` are not usable.
pub fn fixed_time_range(last: Option<u32>,
                        range: Option<NamedTimeRange>,
                        from: Option<&str>,
                        to: Option<&str>) -> Option<String> {
    if let Some(minutes) = last {
        return Some(serde_json::json!({"type": "relative", "minutes": minutes}).to_string());
    }

    if let Some(range) = range {
        return Some(serde_json::json!({"type": "named", "name": range.api_name()}).to_string());
    }

    let (Some(from), Some(to)) = (from, to) else { return None; };

    let (Some(from_unix), Some(to_unix)) = (parse_iso8601_to_unix(from), parse_iso8601_to_unix(to)) else {
        eprintln!("{FG_RED}[x] ERROR:{RESET} --from and --to must be ISO 8601 timestamps like \
            2026-11-03T08:00:00Z.");
        std::process::exit(EX_USAGE);
    };

    if from_unix > to_unix {
        eprintln!("{FG_RED}[x] ERROR:{RESET} --from must not be after --to.");
        std::process::exit(EX_USAGE);
    }

    Some(serde_json::json!({"type": "absolute", "from": from, "to": to}).to_string())
}

pub fn or_unknown(value: &Option<String>) -> &str {
    value.as_deref().unwrap_or("<unknown>")
}

pub fn status_label(online: Option<bool>) -> String {
    match online {
        Some(true) => format!("{FG_GREEN}{BOLD}ONLINE{RESET}"),
        Some(false) => format!("{FG_RED}{BOLD}OFFLINE{RESET}"),
        None => "<unknown>".to_string(),
    }
}

#[derive(Clone, Copy, PartialEq)]
pub enum UnitSystem {
    Metric,
    Imperial,
}

impl UnitSystem {
    pub fn of_user(client: &ApiClient) -> UnitSystem {
        match crate::api::endpoints::user::find_own_profile(client) {
            Ok(profile) if profile.data.unit_system.as_deref() == Some("imperial") => UnitSystem::Imperial,
            _ => UnitSystem::Metric,
        }
    }

    pub fn temperature(&self, celsius: f64) -> String {
        match self {
            UnitSystem::Metric => format!("{:.0}°C", celsius),
            UnitSystem::Imperial => format!("{:.0}°F", celsius * 1.8 + 32.0),
        }
    }

    pub fn speed(&self, kmh: f64) -> String {
        match self {
            UnitSystem::Metric => format!("{:.0} km/h", kmh),
            UnitSystem::Imperial => format!("{:.0} mph", kmh * 0.621371),
        }
    }

    pub fn distance_km(&self, km: f64) -> String {
        match self {
            UnitSystem::Metric => format!("{:.1} km", km),
            UnitSystem::Imperial => format!("{:.1} miles", km * 0.621371),
        }
    }
}

pub fn describe_wind(speed: Option<f64>, gust: Option<f64>, units: UnitSystem) -> Option<String> {
    if speed.is_none() && gust.is_none() {
        return None;
    }

    let effective = speed.unwrap_or(0.0).max(gust.unwrap_or(0.0));
    let label = match effective {
        e if e < 5.0 => return Some("calm winds".to_string()),
        e if e < 15.0 => "light breeze",
        e if e < 25.0 => "breezy",
        e if e < 40.0 => "windy",
        e if e < 60.0 => "strong winds",
        e if e < 80.0 => "very strong winds",
        _ => "dangerous winds",
    };

    let mut parts = Vec::new();
    if let Some(s) = speed { parts.push(units.speed(s)); }
    if let Some(g) = gust { parts.push(format!("gusts {}", units.speed(g))); }

    Some(format!("{} ({})", label, parts.join(", ")))
}
