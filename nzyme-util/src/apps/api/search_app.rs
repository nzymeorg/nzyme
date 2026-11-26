use std::collections::VecDeque;
use std::io::IsTerminal;
use std::sync::Mutex;
use std::time::Duration;

use serde_json::{json, Value};

use crate::api::client::{ApiClient, Typed};
use crate::api::endpoints::search::{
    self, SearchQuery, MAXIMUM_OFFSET, MAXIMUM_QUERY_LENGTH, MINIMUM_QUERY_LENGTH,
};
use crate::api::error::ApiError;
use crate::api::types::{SearchGroupResponse, SearchHitResponse, SearchInterpretationResponse};
use crate::apps::api::briefing_app::plural;
use crate::apps::api::scopes::{discover_scopes, Scope};
use crate::apps::api::{connect, exit_with_api_error, fixed_time_range, print_json, BOLD, FG_RED, FG_YELLOW, RESET};
use crate::arguments::{ConnectionArgs, NamedTimeRange, SearchArgs};
use crate::exit_codes::{EX_DATAERR, EX_UNAVAILABLE, EX_USAGE};
use crate::tools::{
    format_age_seconds, format_count, format_duration_ms, now_unix, parse_iso8601_to_unix, short_time,
};

// The node limits how many scans of recorded data run at once. One search stays well below that.
const SOURCES_AT_ONCE: usize = 3;
const BUSY_ATTEMPTS: usize = 3;
const BUSY_WAIT: Duration = Duration::from_secs(2);

// Administrators find these wherever they administer them, whichever tenant is searched.
const ACROSS_TENANTS: [&str; 2] = ["taps", "administration"];

const MARK: &str = "\x1b[33m";
const MARK_END: &str = "\x1b[39m";

struct Wanted {
    source: String,
    title: String,
}

struct Failure {
    source: String,
    title: String,
    reason: String,
}

struct Searched {
    interpretations: Vec<SearchInterpretationResponse>,
    raw_interpretations: Value,
    /// Groups that found something, for display. The raw groups hold every response, for `--json`.
    groups: Vec<SearchGroupResponse>,
    raw_groups: Vec<Value>,
    failures: Vec<Failure>,
    not_searched: Vec<Wanted>,
}

struct TenantResult {
    scope: Scope,
    searched: Searched,
}

pub fn run(connection: &ConnectionArgs, args: SearchArgs) {
    let query = args.query.join(" ").split_whitespace().collect::<Vec<&str>>().join(" ");
    let length = query.chars().count();
    if !(MINIMUM_QUERY_LENGTH..=MAXIMUM_QUERY_LENGTH).contains(&length) {
        eprintln!("{FG_RED}[x] ERROR:{RESET} A query has between {} and {} characters.",
                  MINIMUM_QUERY_LENGTH, MAXIMUM_QUERY_LENGTH);
        std::process::exit(EX_USAGE);
    }

    if args.offset > 0 && args.source.len() != 1 {
        eprintln!("{FG_RED}[x] ERROR:{RESET} --offset reads on in one source. Pass exactly one --source with it.");
        std::process::exit(EX_USAGE);
    }

    let time_range = fixed_time_range(args.last, args.range, args.from.as_deref(), args.to.as_deref());
    let time_range_description = describe_time_range(&args);

    let client = connect(connection);
    let scopes = discover_scopes(&client, args.organization_id.clone(), args.tenant_id.clone());

    let mut results = Vec::new();
    for scope in scopes {
        eprintln!("{FG_YELLOW}[>] Searching tenant {}...{RESET}", clean(scope.tenant_label()));

        let request = SearchQuery {
            query: &query,
            organization_id: &scope.organization_id,
            tenant_id: &scope.tenant_id,
            limit: args.limit,
            time_range: time_range.as_deref(),
        };

        let searched = if args.source.is_empty() {
            search_everything(&client, &request, args.quick)
        } else {
            search_sources_only(&client, &request, &args.source, args.offset)
        };

        results.push(TenantResult { scope, searched });
    }

    // Nothing found is no failure. A source that was not searched is one: its results are missing.
    let incomplete = results.iter().any(|r| !r.searched.failures.is_empty());

    if connection.json {
        let out: Vec<Value> = results.iter().map(|r| json!({
            "organization_id": r.scope.organization_id,
            "organization_name": r.scope.organization_name,
            "tenant_id": r.scope.tenant_id,
            "tenant_name": r.scope.tenant_name,
            "query": query,
            "interpretations": r.searched.raw_interpretations,
            "groups": r.searched.raw_groups,
            "not_searched": r.searched.not_searched.iter()
                .map(|w| json!({"source": w.source, "title": w.title}))
                .collect::<Vec<Value>>(),
            "errors": r.searched.failures.iter()
                .map(|f| json!({"source": f.source, "error": format!("{} {}", f.title, f.reason)}))
                .collect::<Vec<Value>>(),
        })).collect();
        print_json(&Value::Array(out));
    } else {
        eprintln!("{BOLD}==> Nzyme Search{RESET}");
        eprintln!("    Server: {}\n", client.base_url());

        print_results(&query, time_range_description, results);
    }

    if incomplete {
        std::process::exit(EX_UNAVAILABLE);
    }
}

fn describe_time_range(args: &SearchArgs) -> Option<String> {
    if let Some(minutes) = args.last {
        return Some(format!("the last {}", format_duration_ms(minutes as i64 * 60000)));
    }

    if let Some(range) = args.range {
        return Some(match range {
            NamedTimeRange::Today => "today",
            NamedTimeRange::Yesterday => "yesterday",
            NamedTimeRange::WeekToDate => "this week",
            NamedTimeRange::MonthToDate => "this month",
        }.to_string());
    }

    match (&args.from, &args.to) {
        (Some(from), Some(to)) => Some(format!("{} to {}", short_time(from), short_time(to))),
        _ => None,
    }
}

impl Searched {
    fn new() -> Searched {
        Searched {
            interpretations: Vec::new(),
            raw_interpretations: Value::Array(Vec::new()),
            groups: Vec::new(),
            raw_groups: Vec::new(),
            failures: Vec::new(),
            not_searched: Vec::new(),
        }
    }

    fn add(&mut self, wanted: &Wanted, outcome: Result<Typed<SearchGroupResponse>, ApiError>, named_by_user: bool) {
        match outcome {
            Ok(group) => {
                self.raw_groups.push(group.raw);
                self.add_group(group.data);
            }
            Err(ApiError::NotFound(_)) if named_by_user => self.failures.push(Failure {
                source: wanted.source.clone(),
                title: format!("Source [{}]", wanted.source),
                reason: "does not exist, does not apply to this query or is not accessible to you.".to_string(),
            }),
            Err(e) => self.failures.push(Failure {
                source: wanted.source.clone(),
                title: wanted.title.clone(),
                reason: format!("could not be searched: {}", e),
            }),
        }
    }

    fn add_group(&mut self, group: SearchGroupResponse) {
        let reason = match group.status.as_deref() {
            Some("OK") | None => None,
            Some("TIMEOUT") => Some("took too long to search. A shorter time range can help."),
            Some("BUSY") => Some("was not searched because too many searches are running. Try again in a moment."),
            Some(_) => Some("could not be searched."),
        };

        match reason {
            Some(reason) => self.failures.push(Failure {
                source: group.source.clone().unwrap_or_default(),
                title: title_of(&group).to_string(),
                reason: reason.to_string(),
            }),
            None if !group.hits.is_empty() => self.groups.push(group),
            None => {}
        }
    }
}

fn search_everything(client: &ApiClient, request: &SearchQuery, quick: bool) -> Searched {
    let response = match search::search(client, request) {
        Ok(response) => response,
        Err(e) => exit_with_search_error(&e, request),
    };

    let mut searched = Searched::new();
    searched.interpretations = response.data.interpretations;
    if let Some(raw) = response.raw.get("interpretations").filter(|raw| raw.is_array()) {
        searched.raw_interpretations = raw.clone();
    }
    searched.raw_groups = response.raw.get("groups").and_then(Value::as_array).cloned().unwrap_or_default();

    for group in response.data.groups {
        searched.add_group(group);
    }

    let pending: Vec<Wanted> = response.data.pending.into_iter()
        .filter_map(|p| {
            let source = p.source?;
            Some(Wanted { title: p.title.unwrap_or_else(|| source.clone()), source })
        })
        .collect();

    if quick {
        searched.not_searched = pending;
        return searched;
    }

    if !pending.is_empty() {
        eprintln!("{FG_YELLOW}[>] Searching {} {} of recorded network data...{RESET}",
                  pending.len(), plural(pending.len() as i64, "source", "sources"));
    }

    for (wanted, outcome) in pending.iter().zip(fetch_sources(client, request, &pending, 0)) {
        searched.add(wanted, outcome, false);
    }

    searched
}

fn search_sources_only(client: &ApiClient, request: &SearchQuery, sources: &[String], offset: u32) -> Searched {
    let wanted: Vec<Wanted> = sources.iter()
        .map(|s| s.trim())
        .filter(|s| !s.is_empty())
        .map(|s| Wanted { source: s.to_string(), title: s.to_string() })
        .collect();

    let mut searched = Searched::new();

    for (wanted, outcome) in wanted.iter().zip(fetch_sources(client, request, &wanted, offset)) {
        if let Err(e @ ApiError::Status(400, _)) = &outcome {
            exit_with_search_error(e, request);
        }

        searched.add(wanted, outcome, true);
    }

    searched
}

/// Results come back in the order of `wanted`.
fn fetch_sources(client: &ApiClient, request: &SearchQuery, wanted: &[Wanted], offset: u32)
    -> Vec<Result<Typed<SearchGroupResponse>, ApiError>> {
    let queue: Mutex<VecDeque<usize>> = Mutex::new((0..wanted.len()).collect());
    let done: Mutex<Vec<(usize, Result<Typed<SearchGroupResponse>, ApiError>)>> = Mutex::new(Vec::new());

    std::thread::scope(|threads| {
        for _ in 0..SOURCES_AT_ONCE.min(wanted.len()) {
            threads.spawn(|| {
                loop {
                    let next = queue.lock().unwrap().pop_front();
                    let Some(i) = next else { break; };

                    let outcome = fetch_source(client, request, &wanted[i].source, offset);
                    done.lock().unwrap().push((i, outcome));
                }
            });
        }
    });

    let mut done = done.into_inner().unwrap();
    done.sort_by_key(|(i, _)| *i);
    done.into_iter().map(|(_, outcome)| outcome).collect()
}

fn fetch_source(client: &ApiClient, request: &SearchQuery, source: &str, offset: u32)
    -> Result<Typed<SearchGroupResponse>, ApiError> {
    let mut attempt = 1;

    loop {
        let group = search::search_source(client, request, source, offset)?;
        if group.data.status.as_deref() != Some("BUSY") || attempt >= BUSY_ATTEMPTS {
            return Ok(group);
        }

        attempt += 1;
        std::thread::sleep(BUSY_WAIT);
    }
}

fn exit_with_search_error(e: &ApiError, request: &SearchQuery) -> ! {
    match e {
        ApiError::NotFound(_) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} The server does not know organization {} with tenant {}, they are \
                not accessible to you, or the server is an older version without search.",
                request.organization_id, request.tenant_id);
            std::process::exit(EX_DATAERR);
        }
        ApiError::Status(400, _) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} The server did not accept the query, the time range or the paging. \
                A query has between {} and {} characters, and --offset goes up to {}.",
                MINIMUM_QUERY_LENGTH, MAXIMUM_QUERY_LENGTH, MAXIMUM_OFFSET);
            std::process::exit(EX_USAGE);
        }
        other => exit_with_api_error(other),
    }
}

fn print_results(query: &str, time_range: Option<String>, mut results: Vec<TenantResult>) {
    let color = std::io::stdout().is_terminal() && std::env::var_os("NO_COLOR").is_none();
    let paint = Paint { color };
    let now = now_unix();
    let several_tenants = results.len() > 1;

    // With a time range, every group of recorded data covers the same. Without one, each group says what it covers.
    let own_coverage = time_range.is_none();
    if let Some(time_range) = &time_range {
        println!("Recorded network data: {}.\n", time_range);
    }

    let interpretations = results.first().map(|r| r.searched.interpretations.clone()).unwrap_or_default();
    let needles = needles_of(query, &interpretations);

    for interpretation in &interpretations {
        let detail = interpretation.detail.as_deref().filter(|d| !d.is_empty())
            .map(|d| format!(" ({})", clean(d))).unwrap_or_default();
        println!("{} {}{}", interpretation_label(interpretation.kind.as_deref().unwrap_or("")),
                 paint.bold(&clean(interpretation.value.as_deref().unwrap_or(""))), detail);
    }
    if !interpretations.is_empty() {
        println!();
    }

    let across_tenants = if several_tenants { take_across_tenants(&mut results) } else { Vec::new() };

    let mut hits_total = 0usize;
    let mut groups_total = 0usize;
    let mut tenants_with_hits = 0usize;

    for result in &mut results {
        sort_for_display(&mut result.searched.groups);
        if result.searched.groups.is_empty() {
            continue;
        }

        tenants_with_hits += 1;
        if several_tenants || result.scope.tenant_name.is_some() {
            println!("{}", paint.bold(&format!("Organization {} / Tenant {}",
                                               clean(result.scope.organization_label()),
                                               clean(result.scope.tenant_label()))));
            println!("  ({} / {})\n", result.scope.organization_id, result.scope.tenant_id);
        }

        for group in &result.searched.groups {
            print_group(group, &needles, &paint, own_coverage, now);
            hits_total += group.hits.len();
            groups_total += 1;
        }
    }

    if !across_tenants.is_empty() {
        println!("{}\n", paint.bold("Across tenants"));
        for group in &across_tenants {
            print_group(group, &needles, &paint, own_coverage, now);
            hits_total += group.hits.len();
            groups_total += 1;
        }
    }

    let mut failures_reported: Vec<(&str, &str)> = Vec::new();
    for result in &results {
        for failure in &result.searched.failures {
            // A source that is the same for every tenant fails the same way for every tenant.
            let across = several_tenants && ACROSS_TENANTS.contains(&failure.source.as_str());
            if across && failures_reported.contains(&(failure.source.as_str(), failure.reason.as_str())) {
                continue;
            }
            failures_reported.push((failure.source.as_str(), failure.reason.as_str()));

            let tenant = if several_tenants && !across {
                format!(" Tenant {}.", clean(result.scope.tenant_label()))
            } else {
                String::new()
            };
            eprintln!("{FG_YELLOW}[!] WARNING:{RESET} {} {}{}", clean(&failure.title), clean(&failure.reason),
                      tenant);
        }
    }

    let not_searched = results.iter().map(|r| r.searched.not_searched.len()).max().unwrap_or(0);
    if not_searched > 0 {
        eprintln!("[*] Recorded network data was not searched. {} {} of it apply to this query.",
                  not_searched, plural(not_searched as i64, "source", "sources"));
    }

    if hits_total == 0 {
        eprintln!("[*] Nothing found.");
        return;
    }

    let tenants = if several_tenants {
        format!(", in {} of {} tenants", tenants_with_hits, results.len())
    } else {
        String::new()
    };
    eprintln!("[*] {} {} in {} {}{}.",
              format_count(hits_total as i64), plural(hits_total as i64, "result", "results"),
              groups_total, plural(groups_total as i64, "group", "groups"), tenants);
}

fn take_across_tenants(results: &mut [TenantResult]) -> Vec<SearchGroupResponse> {
    let mut across: Vec<SearchGroupResponse> = Vec::new();

    for result in results.iter_mut() {
        let (shared, own): (Vec<SearchGroupResponse>, Vec<SearchGroupResponse>) =
            std::mem::take(&mut result.searched.groups)
                .into_iter()
                .partition(|g| ACROSS_TENANTS.contains(&g.source.as_deref().unwrap_or("")));
        result.searched.groups = own;

        for group in shared {
            if !across.iter().any(|known| known.source == group.source) {
                across.push(group);
            }
        }
    }

    sort_for_display(&mut across);
    across
}

/// The order of the web interface: configuration and findings first, best matches on top, then recorded network
/// data in a fixed order.
fn sort_for_display(groups: &mut [SearchGroupResponse]) {
    let recorded = |g: &SearchGroupResponse| g.tier.as_deref() == Some("RECORDED");
    let best = |g: &SearchGroupResponse| g.hits.iter().filter_map(|h| h.score).max().unwrap_or(0);

    groups.sort_by(|a, b| {
        recorded(a).cmp(&recorded(b))
            .then_with(|| if recorded(a) { std::cmp::Ordering::Equal } else { best(b).cmp(&best(a)) })
            .then_with(|| a.position.unwrap_or(0).cmp(&b.position.unwrap_or(0)))
    });
}

fn print_group(group: &SearchGroupResponse, needles: &[String], paint: &Paint, own_coverage: bool, now: i64) {
    let more = group.has_more == Some(true);
    let offset = group.offset.unwrap_or(0).max(0) as usize;

    let mut notes = Vec::new();
    notes.push(match (offset, more) {
        (0, false) => format_count(group.hits.len() as i64),
        (0, true) => format!("first {} of more", format_count(group.hits.len() as i64)),
        (_, _) => format!("{} to {}{}",
                          format_count(offset as i64 + 1), format_count((offset + group.hits.len()) as i64),
                          if more || group.limited == Some(true) { ", more exist" } else { "" }),
    });
    if let Some(coverage) = coverage_of(group, now).filter(|_| own_coverage) {
        notes.push(coverage);
    }

    println!("{}", paint.bold(&format!("{} ({}):", clean(title_of(group)), notes.join(", "))));

    for hit in &group.hits {
        print_hit(hit, needles, paint, now);
    }

    if let Some(source) = group.source.as_deref().filter(|_| more) {
        println!("  More results: --source {} --offset {}", clean(source), offset + group.hits.len());
    }
    if group.limited == Some(true) {
        println!("  Limited to the first {} results. More results exist.",
                 format_count((offset + group.hits.len()) as i64));
    }

    println!();
}

fn print_hit(hit: &SearchHitResponse, needles: &[String], paint: &Paint, now: i64) {
    let title = clean(hit.title.as_deref().unwrap_or("Unnamed"));
    let subtitle = hit.subtitle.as_deref().map(clean).filter(|s| !s.is_empty())
        .map(|s| format!(" ({})", paint.mark(&s, needles, false)))
        .unwrap_or_default();

    println!("  {}{}", paint.mark(&title, needles, true), subtitle);

    let mut details: Vec<String> = hit.facts.iter()
        .filter_map(|fact| {
            let value = clean(fact.value.as_deref()?);
            Some(format!("{}: {}", clean(fact.label.as_deref().unwrap_or("")), paint.mark(&value, needles, false)))
        })
        .collect();

    if let Some(seen) = hit.timestamp.as_deref().and_then(parse_iso8601_to_unix) {
        details.push(format!("{} ago", format_age_seconds(now - seen)));
    }

    if !details.is_empty() {
        println!("      {}", details.join("; "));
    }
}

fn title_of(group: &SearchGroupResponse) -> &str {
    group.title.as_deref().or(group.source.as_deref()).unwrap_or("Unnamed")
}

fn coverage_of(group: &SearchGroupResponse, now: i64) -> Option<String> {
    if group.tier.as_deref() != Some("RECORDED") {
        return None;
    }

    let Some(from) = group.searched_from.as_deref().and_then(parse_iso8601_to_unix) else {
        return Some("all recorded data".to_string());
    };

    let hours = ((now - from) as f64 / 3600.0).round() as i64;
    Some(match hours {
        h if h <= 1 => "last hour".to_string(),
        h if h < 48 => format!("last {} hours", h),
        h => format!("last {} days", (h as f64 / 24.0).round() as i64),
    })
}

fn interpretation_label(kind: &str) -> &str {
    match kind {
        "MAC_ADDRESS" => "MAC address",
        "MAC_ADDRESS_PREFIX" => "MAC addresses starting with",
        "IP_ADDRESS" => "IP address",
        "NETWORK" => "Network",
        "UUID" => "UUID",
        "HASH" => "Fingerprint or key",
        "JA4" => "JA4 fingerprint",
        other => other,
    }
}

fn needles_of(query: &str, interpretations: &[SearchInterpretationResponse]) -> Vec<String> {
    let mut needles: Vec<String> = Vec::new();

    let values = std::iter::once(query).chain(interpretations.iter().filter_map(|i| i.value.as_deref()));
    for value in values {
        let needle = value.split_whitespace().collect::<Vec<&str>>().join(" ").to_ascii_lowercase();
        if needle.chars().count() >= 2 && !needles.contains(&needle) {
            needles.push(needle);
        }
    }

    needles.sort_by_key(|n| std::cmp::Reverse(n.len()));
    needles
}

/// Results hold what devices on the network sent: names of networks, hosts and devices. None of it may reach the
/// terminal as a control sequence, or turn the direction of the text around.
fn clean(text: &str) -> String {
    text.chars()
        .map(|c| match c {
            '\n' | '\r' | '\t' => ' ',
            c if c.is_control() => '?',
            '\u{061C}' | '\u{200B}'..='\u{200F}' | '\u{202A}'..='\u{202E}' | '\u{2066}'..='\u{2069}'
            | '\u{FEFF}' => '?',
            c => c,
        })
        .collect()
}

struct Paint {
    color: bool,
}

impl Paint {
    fn bold(&self, text: &str) -> String {
        if self.color { format!("{BOLD}{}{RESET}", text) } else { text.to_string() }
    }

    fn mark(&self, text: &str, needles: &[String], bold: bool) -> String {
        if !self.color {
            return text.to_string();
        }

        // Lower-casing ASCII only keeps every byte in its place, so positions hold for the text as it is.
        let lower = text.to_ascii_lowercase();
        let mut out = String::new();
        let mut position = 0;

        while position < text.len() {
            let found = needles.iter()
                .filter_map(|needle| lower[position..].find(needle.as_str()).map(|at| (position + at, needle.len())))
                .min_by_key(|(at, length)| (*at, std::cmp::Reverse(*length)));

            let Some((at, length)) = found else { break; };

            out.push_str(&text[position..at]);
            out.push_str(MARK);
            out.push_str(&text[at..at + length]);
            out.push_str(MARK_END);
            position = at + length;
        }

        out.push_str(&text[position..]);
        if bold { format!("{BOLD}{}{RESET}", out) } else { out }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn group(source: &str, tier: &str, position: i64, score: i64) -> SearchGroupResponse {
        SearchGroupResponse {
            source: Some(source.to_string()), title: Some(source.to_string()), tier: Some(tier.to_string()),
            position: Some(position), status: Some("OK".to_string()),
            hits: vec![SearchHitResponse {
                title: None, subtitle: None, facts: Vec::new(), timestamp: None, score: Some(score),
            }],
            has_more: Some(false), limited: Some(false), offset: Some(0), searched_from: None,
        }
    }

    fn sources(groups: &[SearchGroupResponse]) -> Vec<&str> {
        groups.iter().map(|g| g.source.as_deref().unwrap()).collect()
    }

    #[test]
    fn orders_groups_like_the_web_interface() {
        let mut groups = vec![
            group("tls", "RECORDED", 21, 100), group("alerts", "INSTANT", 14, 30), group("dns", "RECORDED", 20, 30),
            group("assets", "INSTANT", 0, 100), group("beacons", "INSTANT", 2, 100),
        ];
        sort_for_display(&mut groups);
        assert_eq!(sources(&groups), vec!["assets", "beacons", "alerts", "dns", "tls"]);
    }

    #[test]
    fn shows_groups_across_tenants_once() {
        let scope = |tenant: &str| Scope {
            organization_id: "o".to_string(), organization_name: None, tenant_id: tenant.to_string(), tenant_name: None,
        };
        let result = |tenant: &str, groups: Vec<SearchGroupResponse>| TenantResult {
            scope: scope(tenant), searched: Searched { groups, ..Searched::new() },
        };

        let mut results = vec![
            result("a", vec![group("assets", "INSTANT", 0, 100), group("administration", "INSTANT", 38, 60)]),
            result("b", vec![group("administration", "INSTANT", 38, 60), group("taps", "INSTANT", 37, 100)]),
        ];

        let across = take_across_tenants(&mut results);
        assert_eq!(sources(&across), vec!["taps", "administration"]);
        assert_eq!(sources(&results[0].searched.groups), vec!["assets"]);
        assert!(results[1].searched.groups.is_empty());
    }

    #[test]
    fn reports_sources_that_were_not_searched() {
        let mut searched = Searched::new();

        let with_status = |source: &str, status: &str, hits: bool| {
            let mut g = group(source, "RECORDED", 20, 30);
            g.status = Some(status.to_string());
            if !hits {
                g.hits.clear();
            }
            g
        };

        searched.add_group(with_status("dns", "OK", true));
        searched.add_group(with_status("tls", "OK", false));
        searched.add_group(with_status("dhcp", "TIMEOUT", false));
        searched.add_group(with_status("arp", "BUSY", false));
        searched.add_group(with_status("ssh", "ERROR", false));

        assert_eq!(sources(&searched.groups), vec!["dns"]);
        let failed: Vec<&str> = searched.failures.iter().map(|f| f.source.as_str()).collect();
        assert_eq!(failed, vec!["dhcp", "arp", "ssh"]);
        assert!(searched.failures[0].reason.starts_with("took too long"));
    }

    #[test]
    fn describes_coverage() {
        let now = parse_iso8601_to_unix("2026-11-23T12:00:00Z").unwrap();
        let mut g = group("dns", "RECORDED", 20, 30);
        assert_eq!(coverage_of(&g, now).as_deref(), Some("all recorded data"));

        g.searched_from = Some("2026-11-22T12:00:00.000Z".to_string());
        assert_eq!(coverage_of(&g, now).as_deref(), Some("last 24 hours"));

        g.searched_from = Some("2026-11-16T12:00:00.000Z".to_string());
        assert_eq!(coverage_of(&g, now).as_deref(), Some("last 7 days"));

        assert_eq!(coverage_of(&group("assets", "INSTANT", 0, 100), now), None);
    }

    #[test]
    fn marks_matches() {
        let paint = Paint { color: true };
        let needles = needles_of("aa-bb-cc", &[SearchInterpretationResponse {
            kind: Some("MAC_ADDRESS_PREFIX".to_string()), value: Some("AA:BB:CC:".to_string()), detail: None,
        }]);
        assert_eq!(needles, vec!["aa:bb:cc:", "aa-bb-cc"]);

        assert_eq!(paint.mark("AA:BB:CC:DD:EE:FF", &needles, false), "\x1b[33mAA:BB:CC:\x1b[39mDD:EE:FF");
        assert_eq!(paint.mark("Büro GitHub", &["github".to_string()], true),
                   "\x1b[1mBüro \x1b[33mGitHub\x1b[39m\x1b[0m");
        assert_eq!(paint.mark("nothing", &needles, false), "nothing");
        assert_eq!(Paint { color: false }.mark("AA:BB:CC:DD", &needles, true), "AA:BB:CC:DD");
    }

    #[test]
    fn keeps_control_sequences_from_the_terminal() {
        assert_eq!(clean("Free\x1b[2JWiFi\nGuest"), "Free?[2JWiFi Guest");
        assert_eq!(clean("evil\u{202E}txt.exe"), "evil?txt.exe");
        assert_eq!(clean("Café 日本"), "Café 日本");
        assert_eq!(clean("a\u{200B}b\u{FEFF}c\u{061C}d"), "a?b?c?d");
    }
}
