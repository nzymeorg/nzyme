use serde_json::{json, Value};
use crate::api::endpoints::briefing::{self, BriefingQuery};
use crate::api::types::{
    BriefingAttentionItemResponse, BriefingChangeGroupResponse, BriefingHistogramBucketResponse,
    BriefingLocationResponse, BriefingMonitoringResponse, BriefingResponse, BriefingWindowResponse,
};
use crate::apps::api::scopes::{discover_scopes, Scope};
use crate::apps::api::{
    connect, describe_wind, exit_with_api_error, fixed_time_range, print_json, status_label, UnitSystem, BOLD,
    FG_GREEN, FG_RED, FG_YELLOW, RESET,
};
use crate::arguments::{BriefingSince, BriefingType, ConnectionArgs, NamedTimeRange};
use crate::tools::{
    format_age_seconds, format_bytes, format_count, format_duration_ms, now_unix, parse_iso8601_to_unix, short_time,
};

const SPARKLINE: [char; 8] = ['▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'];
const MAX_BREAKDOWN_ENTRIES: usize = 5;
const MAX_ENVIRONMENT_ALERTS: usize = 2;

/// Clap enforces that at most one fixed-window option is set. `since` only matters when none is.
pub struct Window {
    pub since: BriefingSince,
    pub last: Option<u32>,
    pub range: Option<NamedTimeRange>,
    pub from: Option<String>,
    pub to: Option<String>,
}

impl Window {
    fn to_query(&self) -> (&'static str, Option<String>) {
        match fixed_time_range(self.last, self.range, self.from.as_deref(), self.to.as_deref()) {
            Some(time_range) => ("TIME_RANGE", Some(time_range)),
            None => (self.since.api_name(), None),
        }
    }
}

pub fn show(connection: &ConnectionArgs,
            briefing_type: BriefingType,
            organization_id: Option<String>,
            tenant_id: Option<String>,
            taps: Vec<String>,
            window: Window) {
    let (since, time_range) = window.to_query();

    let client = connect(connection);
    let scopes = discover_scopes(&client, organization_id, tenant_id);
    let taps_param = if taps.is_empty() { None } else { Some(taps.join(",")) };

    let mut results = Vec::new();
    for scope in scopes {
        let query = BriefingQuery {
            briefing_type: briefing_type.api_name(),
            organization_id: &scope.organization_id,
            tenant_id: &scope.tenant_id,
            taps: taps_param.as_deref(),
            since,
            time_range: time_range.as_deref(),
        };

        match briefing::find(&client, &query) {
            Ok(response) => results.push((scope, response)),
            Err(e) => exit_with_api_error(&e),
        }
    }

    if connection.json {
        let out: Vec<Value> = results.iter().map(|(scope, response)| json!({
            "organization_id": scope.organization_id,
            "organization_name": scope.organization_name,
            "tenant_id": scope.tenant_id,
            "tenant_name": scope.tenant_name,
            "briefing": response.raw,
        })).collect();
        print_json(&Value::Array(out));
        return;
    }

    let units = UnitSystem::of_user(&client);

    eprintln!("{BOLD}==> Nzyme {} Briefing{RESET}", briefing_type.display_name());
    eprintln!("    Server: {}\n", client.base_url());

    let mut attention_total = 0usize;
    for (scope, response) in &results {
        print_briefing(scope, briefing_type, &response.data, units);
        attention_total += response.data.attention.len();
    }

    eprintln!("[*] {} {} need attention across {} {}.",
              attention_total, plural(attention_total as i64, "item", "items"),
              results.len(), plural(results.len() as i64, "tenant", "tenants"));
}

pub fn mark_seen(connection: &ConnectionArgs, briefing_type: BriefingType) {
    let client = connect(connection);

    if let Err(e) = briefing::mark_seen(&client, briefing_type.api_name()) {
        exit_with_api_error(&e);
    }

    if connection.json {
        print_json(&json!({"briefing_type": briefing_type.api_name(), "seen": true}));
        return;
    }

    eprintln!("{BOLD}==> Nzyme {} Briefing{RESET}", briefing_type.display_name());
    eprintln!("    Server: {}\n", client.base_url());
    println!("[*] {} briefing marked as seen. The next one in last-visit mode starts a fresh window.",
             briefing_type.display_name());
}

fn print_briefing(scope: &Scope, briefing_type: BriefingType, b: &BriefingResponse, units: UnitSystem) {
    let is_main = briefing_type == BriefingType::Main;

    println!("{BOLD}Organization {} / Tenant {}{RESET}", scope.organization_label(), scope.tenant_label());
    println!("  ({} / {})\n", scope.organization_id, scope.tenant_id);

    if let Some(generated) = &b.generated_at {
        println!("Generated {}.", short_time(generated));
    }
    if let Some(window) = &b.window {
        println!("{}", describe_window(window));
    }
    println!();

    print_verdict(b, is_main);
    print_attention(&b.attention, is_main);

    if let Some(monitoring) = &b.monitoring {
        print_health(monitoring);
        print_checks(monitoring);
        print_taps(monitoring);
    }

    print_locations(&b.locations, briefing_type, units);

    let taps_total = b.monitoring.as_ref().and_then(|m| m.taps_total).unwrap_or(0);
    if taps_total > 0 {
        print_activity(b, briefing_type);
    }

    print_changes(&b.changes, b.window.as_ref(), is_main);
}

fn print_verdict(b: &BriefingResponse, is_main: bool) {
    let items: Vec<&BriefingAttentionItemResponse> = b.attention.iter().collect();
    let (tone, sentence) = verdict(&items, is_main);

    let color = match tone {
        VerdictTone::Quiet => FG_GREEN,
        VerdictTone::Note => "",
        VerdictTone::Orange => FG_YELLOW,
        VerdictTone::Alert | VerdictTone::Red => FG_RED,
    };

    println!("{BOLD}{}{}{RESET}\n", color, sentence);
}

#[derive(Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Debug)]
pub enum VerdictTone {
    Quiet,
    Note,
    Alert,
    Orange,
    Red,
}

pub fn verdict(attention: &[&BriefingAttentionItemResponse], is_main: bool) -> (VerdictTone, String) {
    let count_severity = |severity: &str| attention.iter().filter(|a| a.severity.as_deref() == Some(severity)).count() as i64;

    let red = count_severity("RED");
    let orange = count_severity("ORANGE");
    let alerts: i64 = attention.iter()
        .filter(|a| a.severity.as_deref() == Some("ALERT"))
        .map(|a| a.count.unwrap_or(1).max(1))
        .sum();
    let urgent = red + orange + alerts;

    if attention.is_empty() {
        return (VerdictTone::Quiet, "Nothing needs your attention.".to_string());
    }

    if urgent == 0 {
        return (VerdictTone::Note, format!("{} {} worth a look, nothing urgent.",
                                           attention.len(), plural(attention.len() as i64, "thing", "things")));
    }

    let mut parts = Vec::new();
    if red > 0 {
        parts.push(format!("{} red health {}", red, plural(red, "indicator", "indicators")));
    }
    if orange > 0 {
        parts.push(format!("{} orange health {}", orange, plural(orange, "indicator", "indicators")));
    }
    if alerts > 0 {
        let breakdown = if is_main { alerts_by_subsystem(attention) } else { Vec::new() };
        let split = if breakdown.len() > 1 { format!(", {},", join_and(&breakdown)) } else { String::new() };
        parts.push(format!("{} detection {}{}", alerts, plural(alerts, "alert", "alerts"), split));
    }

    let tone = if red > 0 { VerdictTone::Red } else if orange > 0 { VerdictTone::Orange } else { VerdictTone::Alert };
    (tone, format!("{} {} your attention.", join_and(&parts), if urgent == 1 { "needs" } else { "need" }))
}

pub fn alerts_by_subsystem(attention: &[&BriefingAttentionItemResponse]) -> Vec<String> {
    let mut sums: Vec<(String, i64)> = Vec::new();

    for item in attention.iter().filter(|a| a.severity.as_deref() == Some("ALERT")) {
        let key = item.subsystem.clone().unwrap_or_else(|| "OTHER".to_string());
        let count = item.count.unwrap_or(1).max(1);
        match sums.iter_mut().find(|(k, _)| *k == key) {
            Some(entry) => entry.1 += count,
            None => sums.push((key, count)),
        }
    }

    sums.sort_by(|a, b| b.1.cmp(&a.1));
    sums.into_iter()
        .map(|(key, count)| format!("{} {}", count, if key == "OTHER" { "other" } else { subsystem_name(&key) }))
        .collect()
}

fn print_attention(attention: &[BriefingAttentionItemResponse], is_main: bool) {
    if attention.is_empty() {
        println!("{BOLD}Needs your attention:{RESET}");
        println!("  All quiet. No new detection alerts, every tap is reporting, and nothing in the monitoring \
            configuration needs fixing.\n");
        return;
    }

    println!("{BOLD}Needs your attention ({}):{RESET}", attention.len());
    for item in attention {
        let subsystem = match (&item.subsystem, is_main) {
            (Some(s), true) => format!("[{}] ", subsystem_name(s)),
            _ => String::new(),
        };

        println!("  {}  {}{}", severity_tag(item.severity.as_deref()), subsystem, item.title.as_deref().unwrap_or(""));
        if let Some(text) = item.text.as_deref().filter(|t| !t.is_empty()) {
            println!("          {}", text);
        }
    }
    println!();
}

fn print_health(monitoring: &BriefingMonitoringResponse) {
    let Some(indicators) = &monitoring.health_indicators else { return; };

    let count = |level: &str| indicators.values().filter(|l| l.as_str() == level).count();
    println!("{BOLD}Cluster health:{RESET}");
    println!("  {} green, {} orange, {} red.", count("GREEN"), count("ORANGE"), count("RED"));

    let mut not_green: Vec<(&String, &String)> = indicators.iter().filter(|(_, l)| l.as_str() != "GREEN").collect();
    not_green.sort_by_key(|(_, level)| if level.as_str() == "RED" { 0 } else { 1 });

    for (id, level) in not_green {
        println!("  {}  {}", level_tag(level), health_indicator_name(id));
    }
    println!();
}

fn print_checks(monitoring: &BriefingMonitoringResponse) {
    if monitoring.checks.is_empty() {
        return;
    }

    println!("{BOLD}Detection coverage:{RESET}");
    for check in &monitoring.checks {
        let subsystem = check.subsystem.as_deref().map(|s| format!("[{}] ", subsystem_name(s))).unwrap_or_default();
        println!("  {}  {}{}", check_tag(check.level.as_deref()), subsystem, check.title.as_deref().unwrap_or(""));
        if let Some(detail) = check.detail.as_deref().filter(|d| !d.is_empty()) {
            println!("          {}", detail);
        }
    }
    println!();
}

fn print_taps(monitoring: &BriefingMonitoringResponse) {
    if monitoring.taps.is_empty() {
        return;
    }

    println!("{BOLD}Taps ({} of {} online):{RESET}",
             monitoring.taps_online.unwrap_or(0), monitoring.taps_total.unwrap_or(monitoring.taps.len() as i64));

    let name_width = monitoring.taps.iter().map(|t| t.name.as_deref().unwrap_or("<unknown>").chars().count()).max().unwrap_or(0);
    let location_width = monitoring.taps.iter().map(|t| t.location_name.as_deref().unwrap_or("<none>").chars().count()).max().unwrap_or(0);
    let now = now_unix();

    for tap in &monitoring.taps {
        let last_report = tap.last_report.as_deref().and_then(parse_iso8601_to_unix)
            .map(|t| format!("last report {} ago", format_age_seconds(now - t)))
            .unwrap_or_else(|| "never reported".to_string());

        let captures = if tap.captures.is_empty() {
            "no captures".to_string()
        } else {
            let running = tap.captures.iter().filter(|c| c.is_running == Some(true)).count();
            let mut s = format!("{}/{} captures running", running, tap.captures.len());
            if tap.captures.iter().any(|c| c.dropping == Some(true)) {
                s.push_str(", dropping packets");
            }
            s
        };

        let alerts = match tap.active_alerts {
            Some(0) | None => String::new(),
            Some(n) => format!(", {}{} active {}{}", FG_RED, n, plural(n, "alert", "alerts"), RESET),
        };

        // The status label carries color codes, so it is padded by hand.
        let status = status_label(tap.online);
        let status_pad = if tap.online == Some(true) { " " } else { "" };

        println!("  {}{}  {:<name_width$}  {:<location_width$}  {}, {}{}",
                 status, status_pad,
                 tap.name.as_deref().unwrap_or("<unknown>"),
                 tap.location_name.as_deref().unwrap_or("<none>"),
                 last_report, captures, alerts,
                 name_width = name_width, location_width = location_width);
    }
    println!();
}

fn print_locations(locations: &[BriefingLocationResponse], briefing_type: BriefingType, units: UnitSystem) {
    if locations.is_empty() {
        return;
    }

    let subsystem = if briefing_type == BriefingType::Main { None } else { Some(briefing_type.display_name()) };
    let can_view_alerts = locations.iter().any(|l| l.alert_count.is_some());

    println!("{BOLD}Locations:{RESET}");
    println!("  Taps, active {}detection alerts and weather at each location as of now, not limited to this \
        briefing's window.", subsystem.map(|s| format!("{} ", s)).unwrap_or_default());

    let name_width = locations.iter().map(|l| l.name.as_deref().unwrap_or("<unknown>").chars().count()).max().unwrap_or(0);

    for location in locations {
        let taps = match (location.taps_total, location.taps_online) {
            (Some(0), _) | (None, _) => "no taps".to_string(),
            (Some(total), online) => {
                let text = format!("{} of {} {} online", online.unwrap_or(0), total, plural(total, "tap", "taps"));
                if location.offline_taps.is_empty() { text } else { format!("{FG_RED}{}{RESET}", text) }
            }
        };

        let alerts = if !can_view_alerts {
            String::new()
        } else {
            match location.alert_count {
                None => ", alerts n/a".to_string(),
                Some(0) => ", no active alerts".to_string(),
                Some(n) => format!(", {FG_RED}{} active {}{RESET}", format_count(n), plural(n, "alert", "alerts")),
            }
        };

        println!("  {:<name_width$}  {}{}", location.name.as_deref().unwrap_or("<unknown>"), taps, alerts,
                 name_width = name_width);

        match &location.environment {
            None => println!("  {:<name_width$}  weather: no data", "", name_width = name_width),
            Some(env) => {
                let mut parts = Vec::new();
                if let Some(condition) = env.condition.as_ref().and_then(|c| c.display_name.clone()) {
                    parts.push(condition);
                }
                if let Some(t) = env.temperature {
                    parts.push(units.temperature(t));
                }
                if let Some(wind) = describe_wind(env.wind_speed, env.wind_gust, units) {
                    parts.push(wind);
                }
                if parts.is_empty() {
                    parts.push("no data".to_string());
                }
                println!("  {:<name_width$}  weather: {}", "", parts.join(", "), name_width = name_width);

                if !env.alerts.is_empty() {
                    let shown: Vec<&str> = env.alerts.iter().take(MAX_ENVIRONMENT_ALERTS).map(String::as_str).collect();
                    let remaining = env.alerts.len().saturating_sub(MAX_ENVIRONMENT_ALERTS);
                    let more = if remaining > 0 {
                        format!(" and {} more {}", remaining, plural(remaining as i64, "alert", "alerts"))
                    } else {
                        String::new()
                    };
                    println!("  {:<name_width$}  {FG_RED}severe environmental alerts: {}{RESET}{}", "",
                             shown.join(", "), more, name_width = name_width);
                }
            }
        }
    }
    println!();
}

pub struct Series {
    pub title: String,
    pub unit: Option<&'static str>,
    pub buckets: Vec<BriefingHistogramBucketResponse>,
}

fn print_activity(b: &BriefingResponse, briefing_type: BriefingType) {
    let series = activity_series(&b.activity, briefing_type);
    if series.is_empty() {
        return;
    }

    let Some(window) = &b.window else { return; };
    let split_at = window.from.as_deref().and_then(parse_iso8601_to_unix).unwrap_or(0);

    println!("{BOLD}Activity:{RESET}");
    println!("  Each line is the window before this briefing's, then a bar, then everything {}.", window_phrase(window));

    let title_width = series.iter().map(|s| s.title.chars().count()).max().unwrap_or(0);

    for s in &series {
        let mut buckets: Vec<(i64, f64)> = s.buckets.iter()
            .filter_map(|bucket| Some((parse_iso8601_to_unix(bucket.bucket.as_deref()?)?, bucket.value.unwrap_or(0.0))))
            .collect();
        buckets.sort_by_key(|(t, _)| *t);

        if buckets.is_empty() || buckets.iter().all(|(_, v)| *v == 0.0) {
            println!("  {:<title_width$}  no data in this period", s.title, title_width = title_width);
            continue;
        }

        let max = buckets.iter().map(|(_, v)| *v).fold(0.0f64, f64::max);
        let glyph = |v: f64| SPARKLINE[((v / max) * 7.0).round().clamp(0.0, 7.0) as usize];

        let previous: String = buckets.iter().filter(|(t, _)| *t < split_at).map(|(_, v)| glyph(*v)).collect();
        let current: String = buckets.iter().filter(|(t, _)| *t >= split_at).map(|(_, v)| glyph(*v)).collect();
        let peak_previous = buckets.iter().filter(|(t, _)| *t < split_at).map(|(_, v)| *v).fold(0.0f64, f64::max);
        let peak_current = buckets.iter().filter(|(t, _)| *t >= split_at).map(|(_, v)| *v).fold(0.0f64, f64::max);

        println!("  {:<title_width$}  {}|{}  peak {}, before {}",
                 s.title, previous, current, format_value(peak_current, s.unit), format_value(peak_previous, s.unit),
                 title_width = title_width);
    }
    println!();
}

pub fn activity_series(activity: &std::collections::BTreeMap<String, Value>, briefing_type: BriefingType) -> Vec<Series> {
    let mut series = Vec::new();

    let buckets = |value: Option<&Value>| -> Vec<BriefingHistogramBucketResponse> {
        value.cloned().and_then(|v| serde_json::from_value(v).ok()).unwrap_or_default()
    };

    let mut push = |title: String, unit: Option<&'static str>, value: Option<&Value>| {
        if value.is_some() {
            series.push(Series { title, unit, buckets: buckets(value) });
        }
    };

    let dot11 = |push: &mut dyn FnMut(String, Option<&'static str>, Option<&Value>), a: &Value, prefix: &str| {
        push(format!("{}Access points seen", prefix), None, a.get("bssids"));
        push(format!("{}Networks seen", prefix), None, a.get("ssids"));
        push(format!("{}Disconnection frames", prefix), None, a.get("disco"));
    };
    let ethernet = |push: &mut dyn FnMut(String, Option<&'static str>, Option<&Value>), a: &Value, prefix: &str| {
        push(format!("{}Active assets", prefix), None, a.get("assets"));
    };
    let bluetooth = |push: &mut dyn FnMut(String, Option<&'static str>, Option<&Value>), a: &Value, prefix: &str| {
        push(format!("{}Devices seen", prefix), None, a.get("devices"));
    };

    let flat = Value::Object(activity.iter().map(|(k, v)| (k.clone(), v.clone())).collect());

    match briefing_type {
        BriefingType::Dot11 => dot11(&mut push, &flat, ""),
        BriefingType::Ethernet => ethernet(&mut push, &flat, ""),
        BriefingType::Bluetooth => bluetooth(&mut push, &flat, ""),
        BriefingType::Main => {
            push("Total tap throughput".to_string(), Some("bitrate"), activity.get("throughput"));
            if let Some(a) = activity.get("ethernet") { ethernet(&mut push, a, "Ethernet: "); }
            if let Some(a) = activity.get("dot11") { dot11(&mut push, a, "WiFi: "); }
            if let Some(a) = activity.get("bluetooth") { bluetooth(&mut push, a, "Bluetooth: "); }
        }
    }

    series
}

fn print_changes(changes: &[BriefingChangeGroupResponse], window: Option<&BriefingWindowResponse>, is_main: bool) {
    if changes.is_empty() {
        return;
    }

    println!("{BOLD}Changes:{RESET}");
    if let Some(window) = window {
        println!("  Everything {}, compared to the {} before that.",
                 window_phrase(window), window.duration_ms.map(format_duration_ms).unwrap_or_else(|| "window".to_string()));
    }

    let now = now_unix();

    for group in changes {
        let subsystem = match (&group.subsystem, is_main) {
            (Some(s), true) => format!("{}: ", subsystem_name(s)),
            _ => String::new(),
        };
        println!("\n  {BOLD}{}{}{RESET}", subsystem, group.title.as_deref().unwrap_or(""));

        let title_width = group.tiles.iter().map(|t| t.title.as_deref().unwrap_or("").chars().count()).max().unwrap_or(0);

        for tile in &group.tiles {
            let unit = tile.unit.as_deref();
            let current = tile.current.unwrap_or(0);

            let delta = match tile.previous {
                None => String::new(),
                Some(previous) if previous == current => "same as before".to_string(),
                Some(previous) => {
                    let diff = current - previous;
                    let tone = match tile.higher_is_worse {
                        Some(true) if diff > 0 => FG_RED,
                        Some(true) => FG_GREEN,
                        Some(false) if diff > 0 => FG_GREEN,
                        Some(false) => FG_RED,
                        None => "",
                    };
                    format!("{}{}{} vs {} before{RESET}",
                            tone, if diff > 0 { "+" } else { "" }, format_value(diff as f64, unit),
                            format_value(previous as f64, unit))
                }
            };

            let line = format!("  {:>8}  {:<title_width$}  {}", format_value(current as f64, unit),
                               tile.title.as_deref().unwrap_or(""), delta, title_width = title_width);
            println!("{}", line.trim_end());

            if !tile.breakdown.is_empty() {
                let entries: Vec<String> = tile.breakdown.iter().take(MAX_BREAKDOWN_ENTRIES)
                    .map(|e| format!("{} {}", e.label.as_deref().unwrap_or("?"), format_count(e.count.unwrap_or(0))))
                    .collect();
                let more = tile.breakdown.len().saturating_sub(MAX_BREAKDOWN_ENTRIES);
                println!("            {}{}", entries.join(", "), if more > 0 { format!(", and {} more", more) } else { String::new() });
            }

            for sample in &tile.samples {
                let severity = sample.severity.as_deref().map(|s| format!("{} ", severity_tag(Some(s)))).unwrap_or_default();
                let detail = sample.detail.as_deref().filter(|d| !d.is_empty()).map(|d| format!(" ({})", d)).unwrap_or_default();
                let age = sample.timestamp.as_deref().and_then(parse_iso8601_to_unix)
                    .map(|t| format!(", {} ago", format_age_seconds(now - t))).unwrap_or_default();
                println!("            · {}{}{}{}", severity, sample.label.as_deref().unwrap_or(""), detail, age);
            }

            if !tile.samples.is_empty() && current > tile.samples.len() as i64 {
                println!("            · and {} more", format_value((current - tile.samples.len() as i64) as f64, unit));
            }
        }
    }
    println!();
}

pub fn describe_window(w: &BriefingWindowResponse) -> String {
    let start = w.from.as_deref().map(short_time).unwrap_or_else(|| "<unknown>".to_string());
    let end = w.to.as_deref().map(short_time).unwrap_or_else(|| "now".to_string());
    let length = w.duration_ms.map(format_duration_ms).unwrap_or_else(|| "?".to_string());
    let first_visit = w.is_first_visit == Some(true);
    let capped = w.capped == Some(true);

    match w.mode.as_deref() {
        Some("LAST_VISIT") if first_visit => format!("This is your first briefing here, so changes and activity \
            cover the last 24 hours: everything since {} until now.", start),
        Some("LAST_VISIT") if capped => format!("Changes and activity cover everything since your last visit, \
            capped at 7 days: since {} until now.", start),
        Some("LAST_VISIT") => format!("Changes and activity cover everything since your last visit at {} until \
            now, the last {}.", start, length),
        Some("LAST_SESSION") if first_visit => format!("No earlier session is recorded, so changes and activity \
            cover the last 24 hours: everything since {} until now.", start),
        Some("LAST_SESSION") if capped => format!("Changes and activity cover everything since your last session \
            ended, capped at 7 days: since {} until now.", start),
        Some("LAST_SESSION") => format!("Changes and activity cover everything since your last session ended at \
            {} until now, the last {}.", start, length),
        _ => format!("Changes and activity cover the selected time range: {} to {}, {}.", start, end, length),
    }
}

pub fn window_phrase(w: &BriefingWindowResponse) -> String {
    let start = w.from.as_deref().map(short_time).unwrap_or_else(|| "<unknown>".to_string());
    let end = w.to.as_deref().map(short_time).unwrap_or_else(|| "now".to_string());
    let length = w.duration_ms.map(format_duration_ms).unwrap_or_else(|| "?".to_string());
    let first_visit = w.is_first_visit == Some(true);
    let capped = w.capped == Some(true);

    match w.mode.as_deref() {
        Some("LAST_VISIT") if first_visit => "in the last 24 hours because this is your first briefing here".to_string(),
        Some("LAST_VISIT") if capped => "in the last 7 days because your last visit was longer ago".to_string(),
        Some("LAST_VISIT") => format!("since your last visit at {}, {} ago", start, length),
        Some("LAST_SESSION") if first_visit => "in the last 24 hours because no earlier session is recorded".to_string(),
        Some("LAST_SESSION") if capped => "in the last 7 days because your last session ended longer ago".to_string(),
        Some("LAST_SESSION") => format!("since your last session ended at {}, {} ago", start, length),
        _ => format!("in the selected range, {} to {}", start, end),
    }
}

pub fn subsystem_name(subsystem: &str) -> &str {
    match subsystem {
        "DOT11" => "WiFi",
        "ETHERNET" => "Ethernet",
        "BLUETOOTH" => "Bluetooth",
        "UAV" => "UAV",
        "GENERIC" => "System",
        other => other,
    }
}

pub fn health_indicator_name(id: &str) -> &str {
    match id {
        "crypto_sync" => "Crypto sync",
        "db_clock" => "DB clock",
        "node_clock" => "Node clock",
        "tap_clock" => "Tap clock",
        "node_offline" => "Node offline",
        "tls_exp" => "TLS expiration",
        "tap_offline" => "Tap offline",
        "tap_tpx" => "Tap throughput",
        "tap_drop" => "Tap drop",
        "tap_buffer" => "Tap buffer",
        "tap_error" => "Tap error",
        "tasks_queue_task_stuck" => "Task stuck",
        "tasks_queue_task_failure" => "Task failure",
        "message_bus_message_failure" => "Message failure",
        "message_bus_message_stuck" => "Message stuck",
        "tap_file_permissions" => "Tap file permissions",
        "node_file_permissions" => "Node file permissions",
        "node_heap" => "Node heap",
        "connect" => "Connect",
        other => other,
    }
}

fn severity_tag(severity: Option<&str>) -> String {
    match severity {
        Some("RED") => format!("{FG_RED}{BOLD}RED   {RESET}"),
        Some("ORANGE") => format!("{FG_YELLOW}{BOLD}ORANGE{RESET}"),
        Some("ALERT") => format!("{FG_RED}ALERT {RESET}"),
        Some("NOTICE") => "NOTICE".to_string(),
        Some("INFO") => "INFO  ".to_string(),
        Some(other) => format!("{:<6}", other.chars().take(6).collect::<String>()),
        None => "      ".to_string(),
    }
}

fn level_tag(level: &str) -> String {
    match level {
        "RED" => format!("{FG_RED}{BOLD}RED   {RESET}"),
        "ORANGE" => format!("{FG_YELLOW}{BOLD}ORANGE{RESET}"),
        "GREEN" => format!("{FG_GREEN}GREEN {RESET}"),
        other => format!("{:<6}", other.chars().take(6).collect::<String>()),
    }
}

fn check_tag(level: Option<&str>) -> String {
    match level {
        Some("OK") => format!("{FG_GREEN}OK    {RESET}"),
        Some("WARN") => format!("{FG_YELLOW}{BOLD}WARN  {RESET}"),
        Some("INFO") => "INFO  ".to_string(),
        Some(other) => format!("{:<6}", other.chars().take(6).collect::<String>()),
        None => "      ".to_string(),
    }
}

pub fn format_value(value: f64, unit: Option<&str>) -> String {
    match unit {
        Some("bytes") => format_bytes(value.round() as i64),
        Some("bitrate") => format_bitrate(value),
        _ => format_count(value.round() as i64),
    }
}

pub fn format_bitrate(bits_per_second: f64) -> String {
    const UNITS: [&str; 5] = ["bit/s", "kbit/s", "Mbit/s", "Gbit/s", "Tbit/s"];
    let mut value = bits_per_second;
    let mut i = 0;
    while value >= 1000.0 && i < UNITS.len() - 1 {
        value /= 1000.0;
        i += 1;
    }
    if i == 0 { format!("{:.0} {}", value, UNITS[i]) } else { format!("{:.1} {}", value, UNITS[i]) }
}

pub fn plural<'a>(count: i64, singular: &'a str, plural_form: &'a str) -> &'a str {
    if count == 1 { singular } else { plural_form }
}

pub fn join_and(parts: &[String]) -> String {
    match parts.len() {
        0 => String::new(),
        1 => parts[0].clone(),
        n => format!("{} and {}", parts[..n - 1].join(", "), parts[n - 1]),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn window(mode: &str, first_visit: bool, capped: bool) -> BriefingWindowResponse {
        BriefingWindowResponse {
            mode: Some(mode.to_string()),
            from: Some("2026-11-03T08:00:00.000Z".to_string()),
            to: Some("2026-11-03T20:00:00.000Z".to_string()),
            duration_ms: Some(12 * 3600 * 1000),
            is_first_visit: Some(first_visit), capped: Some(capped),
        }
    }

    #[test]
    fn describes_windows() {
        assert_eq!(describe_window(&window("LAST_VISIT", false, false)),
                   "Changes and activity cover everything since your last visit at 2026-11-03 08:00 UTC until now, the last 12h.");
        assert!(describe_window(&window("LAST_VISIT", true, false)).starts_with("This is your first briefing here"));
        assert!(describe_window(&window("LAST_SESSION", false, true)).contains("capped at 7 days"));
        assert_eq!(describe_window(&window("TIME_RANGE", false, false)),
                   "Changes and activity cover the selected time range: 2026-11-03 08:00 UTC to 2026-11-03 20:00 UTC, 12h.");
    }

    #[test]
    fn builds_time_range_queries() {
        let w = Window { since: BriefingSince::LastSession, last: None, range: None, from: None, to: None };
        assert_eq!(w.to_query(), ("LAST_SESSION", None));

        let w = Window { since: BriefingSince::LastVisit, last: Some(360), range: None, from: None, to: None };
        assert_eq!(w.to_query(), ("TIME_RANGE", Some(r#"{"minutes":360,"type":"relative"}"#.to_string())));

        let w = Window { since: BriefingSince::LastVisit, last: None, range: Some(NamedTimeRange::WeekToDate), from: None, to: None };
        assert_eq!(w.to_query(), ("TIME_RANGE", Some(r#"{"name":"week_to_date","type":"named"}"#.to_string())));

        let w = Window { since: BriefingSince::LastVisit, last: None, range: None,
            from: Some("2026-11-01T00:00:00Z".to_string()), to: Some("2026-11-02T00:00:00Z".to_string()) };
        assert_eq!(w.to_query(), ("TIME_RANGE", Some(r#"{"from":"2026-11-01T00:00:00Z","to":"2026-11-02T00:00:00Z","type":"absolute"}"#.to_string())));
    }

    #[test]
    fn formats_bitrates() {
        assert_eq!(format_bitrate(950.0), "950 bit/s");
        assert_eq!(format_bitrate(6_498_697.0), "6.5 Mbit/s");
        assert_eq!(format_bitrate(2.0e9), "2.0 Gbit/s");
    }

    #[test]
    fn sums_alerts_by_subsystem() {
        let item = |subsystem: Option<&str>, count: i64| BriefingAttentionItemResponse {
            severity: Some("ALERT".to_string()), subsystem: subsystem.map(str::to_string),
            title: None, text: None, count: Some(count),
        };
        let items = vec![item(Some("DOT11"), 2), item(Some("ETHERNET"), 5), item(Some("DOT11"), 1), item(None, 1)];
        let refs: Vec<&BriefingAttentionItemResponse> = items.iter().collect();
        assert_eq!(alerts_by_subsystem(&refs), vec!["5 Ethernet", "3 WiFi", "1 other"]);

        let (tone, sentence) = verdict(&refs, true);
        assert_eq!(tone, VerdictTone::Alert);
        assert_eq!(sentence, "9 detection alerts, 5 Ethernet, 3 WiFi and 1 other, need your attention.");
        assert_eq!(verdict(&[], false), (VerdictTone::Quiet, "Nothing needs your attention.".to_string()));
    }
}
