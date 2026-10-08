use serde_json::{json, Value};
use crate::api::client::ApiClient;
use crate::api::endpoints::{organizations, taps};
use crate::api::error::ApiError;
use crate::api::types::{TapDetailsResponse, TapHighLevelInformationDetailsResponse};
use crate::apps::api::{connect, exit_with_api_error, or_unknown, print_json, status_label, BOLD, FG_RED, FG_YELLOW, RESET};
use crate::arguments::ConnectionArgs;
use crate::exit_codes::EX_PERMISSION_DENIED;
use crate::tools::{format_bytes, format_count, format_usage};

/// An organization/tenant pair to list taps for. Names are only known when the pair was discovered through the
/// organizations and tenants endpoints.
struct Scope {
    organization_id: String,
    organization_name: Option<String>,
    tenant_id: String,
    tenant_name: Option<String>,
}

enum TapsOfScope {
    Full(Vec<TapDetailsResponse>),
    HighLevel(Vec<TapHighLevelInformationDetailsResponse>),
}

struct ScopeResult {
    scope: Scope,
    raw: Value,
    taps: TapsOfScope,
}

pub fn run(connection: &ConnectionArgs, organization_id: Option<String>, tenant_id: Option<String>) {
    let client = connect(connection);

    let scopes = discover_scopes(&client, organization_id, tenant_id);

    let mut results = Vec::new();
    for scope in scopes {
        results.push(fetch_taps(&client, scope));
    }

    if connection.json {
        let out: Vec<Value> = results.iter().map(|r| json!({
            "organization_id": r.scope.organization_id,
            "organization_name": r.scope.organization_name,
            "tenant_id": r.scope.tenant_id,
            "tenant_name": r.scope.tenant_name,
            "detail_level": match r.taps { TapsOfScope::Full(_) => "full", TapsOfScope::HighLevel(_) => "highlevel" },
            "response": r.raw,
        })).collect();
        print_json(&Value::Array(out));
        return;
    }

    eprintln!("{BOLD}==> Nzyme Taps{RESET}");
    eprintln!("    Server: {}\n", client.base_url());

    let mut total = 0usize;
    let mut online = 0usize;
    let mut limited_scopes = 0usize;

    for result in &results {
        println!(
            "{BOLD}Organization {} / Tenant {}{RESET}",
            result.scope.organization_name.as_deref().unwrap_or(&result.scope.organization_id),
            result.scope.tenant_name.as_deref().unwrap_or(&result.scope.tenant_id),
        );
        println!("  ({} / {})\n", result.scope.organization_id, result.scope.tenant_id);

        match &result.taps {
            TapsOfScope::Full(taps) => {
                if taps.is_empty() {
                    println!("No taps.\n");
                }
                for tap in taps {
                    print_tap(tap);
                    println!();
                    total += 1;
                    if tap.active == Some(true) { online += 1; }
                }
            }
            TapsOfScope::HighLevel(taps) => {
                limited_scopes += 1;
                if taps.is_empty() {
                    println!("No taps.\n");
                }
                for tap in taps {
                    print_tap_high_level(tap);
                    println!();
                    total += 1;
                    if tap.is_online == Some(true) { online += 1; }
                }
            }
        }
    }

    eprintln!("[*] {} taps in {} tenants, {} online.", total, results.len(), online);

    if limited_scopes > 0 {
        eprintln!("{FG_YELLOW}[!] Metrics are only available to organization administrators. Showing high-level \
            status only for {} tenants.{RESET}", limited_scopes);
    }
}

fn discover_scopes(client: &ApiClient, organization_id: Option<String>, tenant_id: Option<String>) -> Vec<Scope> {
    match (organization_id, tenant_id) {
        (Some(org), Some(tenant)) => vec![Scope {
            organization_id: org, organization_name: None, tenant_id: tenant, tenant_name: None
        }],

        (Some(org), None) => {
            eprintln!("{FG_YELLOW}[>] Discovering tenants of organization {}...{RESET}", org);
            match organizations::find_tenants(client, &org) {
                Ok(tenants) => tenants.into_iter().map(|t| Scope {
                    organization_id: org.clone(), organization_name: None, tenant_id: t.id, tenant_name: t.name
                }).collect(),
                Err(ApiError::Unauthorized) => {
                    eprintln!("{FG_RED}[x] ERROR:{RESET} Listing the tenants of an organization requires \
                        organization administrator permissions. Pass --tenant-id as well to list the taps of a \
                        single tenant.");
                    std::process::exit(EX_PERMISSION_DENIED);
                }
                Err(e) => exit_with_api_error(&e),
            }
        }

        (None, _) => {
            eprintln!("{FG_YELLOW}[>] Discovering all organizations and tenants...{RESET}");
            let orgs = match organizations::find_all(client) {
                Ok(orgs) => orgs,
                Err(ApiError::Unauthorized) => {
                    eprintln!("{FG_RED}[x] ERROR:{RESET} Listing all organizations requires super administrator \
                        permissions. Organization administrators pass --organization-id, all other users pass \
                        --organization-id and --tenant-id.");
                    std::process::exit(EX_PERMISSION_DENIED);
                }
                Err(e) => exit_with_api_error(&e),
            };

            let mut scopes = Vec::new();
            for org in orgs {
                let tenants = match organizations::find_tenants(client, &org.id) {
                    Ok(tenants) => tenants,
                    Err(e) => exit_with_api_error(&e),
                };

                for tenant in tenants {
                    scopes.push(Scope {
                        organization_id: org.id.clone(),
                        organization_name: org.name.clone(),
                        tenant_id: tenant.id,
                        tenant_name: tenant.name,
                    });
                }
            }

            scopes
        }
    }
}

/// Fetches the full tap list of a scope and falls back to the high-level list if the user is not an
/// organization administrator.
fn fetch_taps(client: &ApiClient, scope: Scope) -> ScopeResult {
    match taps::find_all(client, &scope.organization_id, &scope.tenant_id) {
        Ok(response) => ScopeResult { scope, raw: response.raw, taps: TapsOfScope::Full(response.data.taps) },
        Err(ApiError::Unauthorized) => {
            match taps::find_all_high_level(client, &scope.organization_id, &scope.tenant_id) {
                Ok(response) => ScopeResult {
                    scope, raw: response.raw, taps: TapsOfScope::HighLevel(response.data.taps)
                },
                Err(e) => exit_with_api_error(&e),
            }
        }
        Err(e) => exit_with_api_error(&e),
    }
}

fn location(location_name: &Option<String>, floor_name: &Option<String>) -> String {
    match (location_name, floor_name) {
        (Some(l), Some(f)) => format!("{} / {}", l, f),
        (Some(l), None) => l.clone(),
        _ => "<none>".to_string(),
    }
}

fn print_tap(tap: &TapDetailsResponse) {
    let description = tap.description.as_deref().filter(|d| !d.trim().is_empty());

    println!(
        "Tap:\n\
         ├─ Name:            {BOLD}{}{RESET}\n\
         ├─ UUID:            {}\n\
         ├─ Status:          {}\n\
         ├─ Version:         {}\n\
         ├─ Last Report:     {}\n\
         ├─ Remote Address:  {}\n\
         ├─ Location:        {}\n\
         ├─ Clock Drift:     {}\n\
         ├─ CPU Load:        {}\n\
         ├─ Memory:          {}\n\
         ├─ Processed:       {}",
        or_unknown(&tap.name),
        or_unknown(&tap.uuid),
        status_label(tap.active),
        or_unknown(&tap.version),
        tap.last_report.as_deref().unwrap_or("never"),
        or_unknown(&tap.remote_address),
        location(&tap.location_name, &tap.floor_name),
        tap.clock_drift_ms.map(|d| format!("{} ms", d)).unwrap_or_else(|| "<unknown>".to_string()),
        tap.cpu_load.map(|l| format!("{:.1} %", l)).unwrap_or_else(|| "<unknown>".to_string()),
        format_usage(tap.memory_used, tap.memory_total),
        tap.processed_bytes.as_ref()
            .map(|p| format!(
                "{} total, {}/s average",
                p.total.map(format_bytes).unwrap_or_else(|| "<unknown>".to_string()),
                p.average.map(format_bytes).unwrap_or_else(|| "<unknown>".to_string())
            ))
            .unwrap_or_else(|| "<unknown>".to_string()),
    );

    if let Some(description) = description {
        println!("├─ Description:     {}", description);
    }

    let has_buses = !tap.buses.is_empty();
    let capture_prefix = if has_buses { "├─" } else { "└─" };

    if tap.captures.is_empty() {
        println!("{} Captures:        none", capture_prefix);
    } else {
        let running = tap.captures.iter().filter(|c| c.is_running == Some(true)).count();
        println!("{} Captures:        {} running / {} total", capture_prefix, running, tap.captures.len());
        let inner = if has_buses { "│" } else { " " };
        for (i, capture) in tap.captures.iter().enumerate() {
            let branch = if i + 1 == tap.captures.len() { "└─" } else { "├─" };
            println!(
                "{}    {} {} [{}] {}: received {}, dropped {} (buffer) / {} (interface)",
                inner, branch,
                or_unknown(&capture.interface_name),
                or_unknown(&capture.capture_type),
                match capture.is_running {
                    Some(true) => "running",
                    Some(false) => "stopped",
                    None => "<unknown>",
                },
                capture.received.map(format_count).unwrap_or_else(|| "?".to_string()),
                capture.dropped_buffer.map(format_count).unwrap_or_else(|| "?".to_string()),
                capture.dropped_interface.map(format_count).unwrap_or_else(|| "?".to_string()),
            );
        }
    }

    if has_buses {
        println!("└─ Buses:           {}", tap.buses.len());
        for (b, bus) in tap.buses.iter().enumerate() {
            let last_bus = b + 1 == tap.buses.len();
            println!("     {} {}", if last_bus { "└─" } else { "├─" }, or_unknown(&bus.name));
            let inner = if last_bus { " " } else { "│" };
            for (c, channel) in bus.channels.iter().enumerate() {
                let branch = if c + 1 == bus.channels.len() { "└─" } else { "├─" };
                println!(
                    "     {}    {} {}: watermark {} / {}, {} msg/s, {}/s, {} errors",
                    inner, branch,
                    or_unknown(&channel.name),
                    channel.watermark.map(format_count).unwrap_or_else(|| "?".to_string()),
                    channel.capacity.map(format_count).unwrap_or_else(|| "?".to_string()),
                    channel.throughput_messages.as_ref().and_then(|t| t.average).map(format_count)
                        .unwrap_or_else(|| "?".to_string()),
                    channel.throughput_bytes.as_ref().and_then(|t| t.average).map(format_bytes)
                        .unwrap_or_else(|| "?".to_string()),
                    channel.errors.as_ref().and_then(|t| t.total).map(format_count)
                        .unwrap_or_else(|| "?".to_string()),
                );
            }
        }
    }
}

fn print_tap_high_level(tap: &TapHighLevelInformationDetailsResponse) {
    println!(
        "Tap:\n\
         ├─ Name:            {BOLD}{}{RESET}\n\
         ├─ UUID:            {}\n\
         ├─ Status:          {}\n\
         └─ Location:        {}",
        or_unknown(&tap.name),
        or_unknown(&tap.uuid),
        status_label(tap.is_online),
        location(&tap.location_name, &tap.floor_name),
    );
}
