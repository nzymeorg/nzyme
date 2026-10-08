pub mod list_nodes_app;
pub mod list_taps_app;
pub mod tap_metrics_app;
pub mod gui;
pub mod profiles;

use crate::api::client::ApiClient;
use crate::api::error::ApiError;
use crate::arguments::ConnectionArgs;
use crate::exit_codes::EX_CONFIG;
use crate::profiles::connection;

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
