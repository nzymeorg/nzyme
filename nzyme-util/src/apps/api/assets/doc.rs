use std::collections::{BTreeMap, HashMap, HashSet};

use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};

use crate::api::types::{
    AssetActivityResponse, AssetAttributeResponse, AssetCommunicationEdgeResponse, AssetCommunicationResponse,
};
use crate::apps::api::assets::labels::{
    alert_role_title, country_centroid, country_name, describe_change, dimension_title, exclusion_title,
    external_peer_label, finding_title, risk_signal, role_title, timeline_kind_title,
};
use crate::apps::api::assets::profile::AssetProfile;
use crate::apps::api::assets::worldmap::{self, traffic_color, Marker, MarkerKind};
use crate::tools::{format_age_seconds, format_bytes, format_count, parse_iso8601_to_unix, short_time};
use crate::tui::theme::{palette, segment_bar};

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Section {
    Overview,
    Activity,
    Communication,
    Map,
    Changes,
    Detections,
    Risks,
    Identity,
}

impl Section {
    pub const ALL: [Section; 8] = [
        Section::Overview, Section::Activity, Section::Communication, Section::Map, Section::Changes,
        Section::Detections, Section::Risks, Section::Identity,
    ];

    pub fn title(&self) -> &'static str {
        match self {
            Section::Overview => "Overview",
            Section::Activity => "Activity",
            Section::Communication => "Communication",
            Section::Map => "Map",
            Section::Changes => "Changes",
            Section::Detections => "Detections",
            Section::Risks => "Risk Signals",
            Section::Identity => "Identity",
        }
    }
}

fn label() -> Style { Style::default().fg(palette::MUTED) }
fn text() -> Style { Style::default().fg(palette::TEXT) }
fn bright() -> Style { Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD) }
fn faint() -> Style { Style::default().fg(palette::FAINT) }
fn accent() -> Style { Style::default().fg(palette::ORANGE) }
fn machine() -> Style { Style::default().fg(Color::Rgb(0x9c, 0xd3, 0xff)) }
fn good() -> Style { Style::default().fg(Color::Green) }
fn bad() -> Style { Style::default().fg(Color::Red).add_modifier(Modifier::BOLD) }
fn warn() -> Style { Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD) }

const ASSET_COLOR: Color = Color::Rgb(0x22, 0xd3, 0xee);

pub struct Doc {
    pub width: usize,
    pub lines: Vec<Line<'static>>,
}

#[derive(Clone, Copy)]
pub enum Align { Left, Right }

pub struct Column {
    pub title: &'static str,
    pub width: usize,
    pub align: Align,
}

impl Column {
    pub const fn left(title: &'static str, width: usize) -> Self { Column { title, width, align: Align::Left } }
    pub const fn right(title: &'static str, width: usize) -> Self { Column { title, width, align: Align::Right } }
}

impl Doc {
    pub fn new(width: usize) -> Self {
        Doc { width: width.max(40), lines: Vec::new() }
    }

    pub fn blank(&mut self) {
        self.lines.push(Line::raw(""));
    }

    pub fn push(&mut self, spans: Vec<Span<'static>>) {
        self.lines.push(Line::from(spans));
    }

    pub fn section(&mut self, title: &str) {
        self.blank();
        let label = format!(" // {} ", title.to_uppercase());
        self.push(vec![
            Span::styled(label.clone(), Style::default().fg(palette::RED).add_modifier(Modifier::BOLD)),
            Span::styled("─".repeat(self.width.saturating_sub(label.chars().count())), Style::default().fg(palette::RED_DIM)),
        ]);
    }

    pub fn subsection(&mut self, title: &str) {
        self.blank();
        self.push(vec![Span::styled(format!("  {}", title.to_uppercase()), accent().add_modifier(Modifier::BOLD))]);
    }

    pub fn text(&mut self, indent: usize, text: &str, style: Style) {
        for row in wrap(text, self.width.saturating_sub(indent).max(10)) {
            self.push(vec![Span::raw(" ".repeat(indent)), Span::styled(row, style)]);
        }
    }

    pub fn note(&mut self, text: &str) {
        self.text(2, text, label());
    }

    pub fn fact(&mut self, name: &str, value: Vec<Span<'static>>) {
        let mut spans = vec![Span::styled(format!("  {:<18}", name.to_uppercase()), label())];
        spans.extend(value);
        self.push(spans);
    }

    pub fn table(&mut self, columns: &[Column], rows: Vec<Vec<Span<'static>>>) {
        let fixed: usize = columns.iter().map(|c| c.width).sum::<usize>() + columns.len() * 2;
        let flex = self.width.saturating_sub(fixed + 2).max(8);
        let width_of = |c: &Column| if c.width == 0 { flex } else { c.width };

        let header: Vec<Span<'static>> = std::iter::once(Span::raw("  ")).chain(columns.iter().map(|c| {
            Span::styled(pad(&c.title.to_uppercase(), width_of(c), c.align) + "  ", Style::default().fg(palette::RED).add_modifier(Modifier::BOLD))
        })).collect();
        self.push(header);

        for row in rows {
            let mut spans = vec![Span::raw("  ")];
            for (i, column) in columns.iter().enumerate() {
                let cell = row.get(i).cloned().unwrap_or_else(|| Span::raw(""));
                let content = pad(&cell.content, width_of(column), column.align) + "  ";
                spans.push(Span::styled(content, cell.style));
            }
            self.push(spans);
        }
    }
}

fn pad(text: &str, width: usize, align: Align) -> String {
    let truncated = truncate(text, width);
    let len = truncated.chars().count();
    match align {
        Align::Left => format!("{}{}", truncated, " ".repeat(width.saturating_sub(len))),
        Align::Right => format!("{}{}", " ".repeat(width.saturating_sub(len)), truncated),
    }
}

pub fn truncate(text: &str, width: usize) -> String {
    let count = text.chars().count();
    if count <= width {
        return text.to_string();
    }
    if width == 0 {
        return String::new();
    }
    let mut out: String = text.chars().take(width - 1).collect();
    out.push('…');
    out
}

pub fn wrap(text: &str, width: usize) -> Vec<String> {
    let mut rows = Vec::new();
    let mut current = String::new();
    for word in text.split_whitespace() {
        let candidate_len = current.chars().count() + if current.is_empty() { 0 } else { 1 } + word.chars().count();
        if !current.is_empty() && candidate_len > width {
            rows.push(std::mem::take(&mut current));
        }
        if !current.is_empty() {
            current.push(' ');
        }
        current.push_str(word);
    }
    if !current.is_empty() || rows.is_empty() {
        rows.push(current);
    }
    rows
}

fn tap_name<'a>(profile: &'a AssetProfile, uuid: &'a str) -> &'a str {
    profile.summary.as_ref().ok()
        .and_then(|s| s.taps.iter().find(|t| t.uuid.as_deref() == Some(uuid)))
        .and_then(|t| t.name.as_deref())
        .unwrap_or(uuid)
}

fn change_value(profile: &AssetProfile, event_type: &str, detail: Option<&str>, value: Option<String>) -> Option<String> {
    match (event_type, detail, value) {
        ("ATTRIBUTE", Some("TAP"), Some(uuid)) => Some(tap_name(profile, &uuid).to_string()),
        (_, _, value) => value,
    }
}

fn age(timestamp: Option<&str>, now: i64) -> String {
    match timestamp.and_then(parse_iso8601_to_unix) {
        Some(t) => format!("{} ago", format_age_seconds(now - t)),
        None => "n/a".to_string(),
    }
}

fn when(timestamp: Option<&str>) -> String {
    timestamp.map(short_time).unwrap_or_else(|| "n/a".to_string())
}

fn count(n: Option<i64>) -> String {
    format_count(n.unwrap_or(0))
}

fn bytes(n: Option<i64>) -> String {
    format_bytes(n.unwrap_or(0))
}

fn share(value: i64, max: i64, width: usize) -> Span<'static> {
    let fraction = if max <= 0 { 0.0 } else { value as f64 / max as f64 };
    Span::styled(segment_bar(fraction, width), Style::default().fg(traffic_color(fraction)))
}

fn s(text: impl Into<String>, style: Style) -> Span<'static> {
    Span::styled(text.into(), style)
}

fn unavailable(doc: &mut Doc, what: &str, error: &str) {
    doc.text(2, &format!("{} could not be loaded: {}", what, error), bad());
}

pub fn build(profile: &AssetProfile, section: Section, width: usize, now: i64) -> Vec<Line<'static>> {
    let mut doc = Doc::new(width);
    match section {
        Section::Overview => overview(&mut doc, profile, now),
        Section::Activity => activity(&mut doc, profile, now),
        Section::Communication => communication(&mut doc, profile, now),
        Section::Map => map(&mut doc, profile),
        Section::Changes => changes(&mut doc, profile, now),
        Section::Detections => detections(&mut doc, profile, now),
        Section::Risks => risks(&mut doc, profile, now),
        Section::Identity => identity(&mut doc, profile, now),
    }
    doc.blank();
    doc.lines
}

pub fn header(profile: &AssetProfile, width: usize, now: i64) -> Vec<Line<'static>> {
    let mut doc = Doc::new(width);
    let asset = &profile.located.asset;
    let active = asset.is_active.unwrap_or(false);

    let mut title = vec![
        s("  ◉ ", Style::default().fg(ASSET_COLOR).add_modifier(Modifier::BOLD)),
        s(profile.label(), bright()),
    ];
    if profile.name().is_some() {
        title.push(s(format!("  {}", profile.mac()), machine()));
    }
    if let Some(oui) = asset.oui.as_deref().filter(|o| !o.is_empty()) {
        title.push(s(format!("  {}", oui), label()));
    }
    title.push(s("  ", text()));
    title.push(if active { s("● ACTIVE", good().add_modifier(Modifier::BOLD)) } else { s("· INACTIVE", label()) });
    doc.push(title);

    doc.push(vec![
        s(format!("    first seen {}  ·  last seen {} ({})  ·  {}",
                  when(asset.first_seen.as_deref()), when(asset.last_seen.as_deref()), age(asset.last_seen.as_deref(), now),
                  profile.located.scope.tenant_label()), label()),
    ]);
    doc.lines
}

fn overview(doc: &mut Doc, profile: &AssetProfile, now: i64) {
    doc.section("Summary");
    match &profile.summary {
        Err(e) => unavailable(doc, "The summary", e),
        Ok(summary) => {
            let active_alerts = summary.active_alerts.unwrap_or(0);
            doc.fact("Alerts", vec![
                s(count(Some(active_alerts)), if active_alerts > 0 { bad() } else { good() }),
                s(format!(" active of {} total", count(summary.total_alerts)), label()),
            ]);
            doc.fact("Exclusions", vec![s(count(summary.exclusions), text())]);

            match summary.last_change_type.as_deref() {
                Some(change_type) => {
                    let change = describe_change(change_type, summary.last_change_value.as_deref(), summary.last_change_detail.as_deref());
                    let mut spans = vec![s(change.action.clone(), text())];
                    if let Some(value) = change_value(profile, change_type, summary.last_change_detail.as_deref(), change.value.clone()) {
                        spans.push(s(format!(" {}", value), machine()));
                    }
                    spans.push(s(format!("  {}", age(summary.last_change_at.as_deref(), now)), label()));
                    doc.fact("Last change", spans);
                }
                None => doc.fact("Last change", vec![s("None recorded", label())]),
            }

            doc.fact("Context", vec![if summary.has_context.unwrap_or(false) {
                s("MAC address context exists", good())
            } else {
                s("No MAC address context. Not described or approved by an analyst.", warn())
            }]);
            if summary.randomized_mac.unwrap_or(false) {
                doc.fact("MAC address", vec![s("Locally administered, usually randomized. Identity may change.", warn())]);
            }

            if !summary.roles.is_empty() {
                doc.subsection("Roles");
                let rows = summary.roles.iter().map(|r| vec![
                    s(role_title(r.role.as_deref().unwrap_or("")), bright()),
                    s(count(r.count), text()),
                    s(r.dependents.map(|d| format!("{} assets", format_count(d))).unwrap_or_default(), text()),
                    s(when(r.first_seen.as_deref()), label()),
                    s(age(r.last_seen.as_deref(), now), label()),
                ]).collect();
                doc.table(&[
                    Column::left("Role", 28), Column::right("Sessions", 10), Column::left("Depends on it", 14),
                    Column::left("First seen", 20), Column::left("Last seen", 0),
                ], rows);
            }

            if !summary.taps.is_empty() {
                doc.subsection("Observed by");
                let rows = summary.taps.iter().map(|t| vec![
                    s(t.name.clone().unwrap_or_else(|| t.uuid.clone().unwrap_or_default()), text()),
                    s(count(t.count), text()),
                    s(when(t.first_seen.as_deref()), label()),
                    s(age(t.last_seen.as_deref(), now), label()),
                ]).collect();
                doc.table(&[
                    Column::left("Tap", 32), Column::right("Events", 12), Column::left("First seen", 20), Column::left("Last seen", 0),
                ], rows);
            }
        }
    }

    doc.section("Roles and Protocols");
    match &profile.attributes {
        Err(e) => unavailable(doc, "The attributes", e),
        Ok(attributes) => {
            let get = |dimension: &str| attributes.attributes.get(dimension).cloned().unwrap_or_default();

            for (dimension, title) in [("PROTOCOL_CLIENT", "Protocols as client"), ("PROTOCOL_SERVER", "Protocols as server")] {
                let mut values = get(dimension);
                if values.is_empty() {
                    continue;
                }
                values.sort_by(|a, b| b.count.unwrap_or(0).cmp(&a.count.unwrap_or(0)));
                let max = values.iter().map(|v| v.bytes.unwrap_or(0)).max().unwrap_or(0);
                doc.subsection(title);
                let rows = values.iter().map(|v| vec![
                    s(v.value.clone().unwrap_or_default(), bright()),
                    s(count(v.count), text()),
                    s(bytes(v.bytes), text()),
                    share(v.bytes.unwrap_or(0), max, 16),
                    s(age(v.last_seen.as_deref(), now), label()),
                ]).collect();
                doc.table(&[
                    Column::left("Protocol", 22), Column::right("Sessions", 10), Column::right("Traffic", 10),
                    Column::left("", 16), Column::left("Last seen", 0),
                ], rows);
            }

            inbound_ports(doc, &get("INBOUND_PORT"), &get("INBOUND_PORT_PROTOCOL"), now);

            let infrastructure: Vec<(&str, &str)> = vec![
                ("GATEWAY_MAC", "Gateway"), ("DNS_SERVER", "DNS Server"), ("DHCP_SERVER", "DHCP Server"), ("NTP_SERVER", "NTP Server"),
            ];
            let mut rows = Vec::new();
            for (dimension, title) in infrastructure {
                let mut values = get(dimension);
                values.sort_by(|a, b| b.count.unwrap_or(0).cmp(&a.count.unwrap_or(0)));
                for v in values.iter().take(5) {
                    rows.push(vec![
                        s(title, accent()),
                        s(v.value.clone().unwrap_or_default(), machine()),
                        s(infrastructure_name(v), text()),
                        s(count(v.count), text()),
                        s(age(v.last_seen.as_deref(), now), label()),
                    ]);
                }
                if values.len() > 5 {
                    rows.push(vec![s("", text()), s(format!("{} more", values.len() - 5), label())]);
                }
            }
            if !rows.is_empty() {
                doc.subsection("Infrastructure used");
                doc.table(&[
                    Column::left("Role", 12), Column::left("Address", 40), Column::left("Name", 24), Column::right("Uses", 10),
                    Column::left("Last seen", 0),
                ], rows);
            }

            let mut countries = get("EXTERNAL_COUNTRY");
            let mut networks = get("EXTERNAL_ASN");
            if !countries.is_empty() || !networks.is_empty() {
                countries.sort_by(|a, b| b.count.unwrap_or(0).cmp(&a.count.unwrap_or(0)));
                networks.sort_by(|a, b| b.count.unwrap_or(0).cmp(&a.count.unwrap_or(0)));
                doc.subsection(&format!("External networks ({} countries, {} autonomous systems)", countries.len(), networks.len()));
                let rows = networks.iter().take(10).map(|v| vec![
                    s(v.value.clone().unwrap_or_default(), text()),
                    s(count(v.count), text()),
                    s(age(v.last_seen.as_deref(), now), label()),
                ]).collect();
                doc.table(&[Column::left("Network", 50), Column::right("Sessions", 10), Column::left("Last seen", 0)], rows);
                if !countries.is_empty() {
                    let listed: Vec<String> = countries.iter().take(12)
                        .map(|c| format!("{} {}", country_name(c.value.as_deref().unwrap_or("")), count(c.count)))
                        .collect();
                    doc.blank();
                    doc.text(2, &format!("Countries: {}", listed.join("  ·  ")), label());
                }
            }
        }
    }
}

fn infrastructure_name(v: &AssetAttributeResponse) -> String {
    if let Some(l4) = &v.l4_address {
        if let Some(name) = l4.mac.as_ref().and_then(|m| m.context.as_ref()).and_then(|c| c.name.clone()) {
            return name;
        }
        if let Some(geo) = &l4.geo {
            if let Some(name) = &geo.asn_name {
                return name.clone();
            }
        }
    }
    if let Some(name) = v.mac_address.as_ref().and_then(|m| m.context.as_ref()).and_then(|c| c.name.clone()) {
        return name;
    }
    String::new()
}

fn inbound_ports(doc: &mut Doc, ports: &[AssetAttributeResponse], protocols: &[AssetAttributeResponse], now: i64) {
    if ports.is_empty() {
        return;
    }

    let mut tags: HashMap<String, Vec<String>> = HashMap::new();
    for p in protocols {
        if let Some(value) = &p.value {
            if let Some((port, tag)) = value.split_once(' ') {
                tags.entry(port.to_string()).or_default().push(tag.to_string());
            }
        }
    }

    let mut ranked: Vec<&AssetAttributeResponse> = ports.iter().collect();
    ranked.sort_by(|a, b| b.count.unwrap_or(0).cmp(&a.count.unwrap_or(0)));
    let total: i64 = ranked.iter().map(|p| p.count.unwrap_or(0)).sum();
    let max = ranked.first().map(|p| p.count.unwrap_or(0)).unwrap_or(0);

    doc.subsection(&format!("Inbound ports ({})", ports.len()));
    let shown = 12.min(ranked.len());
    let rows = ranked.iter().take(shown).map(|p| {
        let port = p.value.clone().unwrap_or_default();
        let protocol = tags.get(&port).map(|t| t.join(", ")).unwrap_or_default();
        vec![
            s(port, machine()),
            s(protocol, text()),
            s(count(p.count), text()),
            s(bytes(p.bytes), text()),
            share(p.count.unwrap_or(0), max, 16),
            s(age(p.last_seen.as_deref(), now), label()),
        ]
    }).collect();
    doc.table(&[
        Column::left("Port", 12), Column::left("Protocols", 20), Column::right("Sessions", 10), Column::right("Traffic", 10),
        Column::left("", 16), Column::left("Last seen", 0),
    ], rows);

    if ranked.len() > shown {
        let rest: i64 = ranked.iter().skip(shown).map(|p| p.count.unwrap_or(0)).sum();
        doc.text(2, &format!("{} more ports with {} sessions, {:.0} % of all inbound sessions.",
                             ranked.len() - shown, format_count(rest), if total > 0 { rest as f64 / total as f64 * 100.0 } else { 0.0 }), label());
    }
}

const WEEKDAYS: [&str; 7] = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const MONTHS: [&str; 12] = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

fn activity(doc: &mut Doc, profile: &AssetProfile, now: i64) {
    doc.section(&format!("Activity, last {} days", profile.activity_days));
    let activity = match &profile.activity {
        Err(e) => { unavailable(doc, "The activity", e); return; }
        Ok(a) => a,
    };

    doc.fact("Network events", vec![s(count(activity.total_events), text())]);
    doc.fact("Traffic", vec![s(bytes(activity.total_bytes), text())]);
    doc.fact("Active days", vec![
        s(format!("{} of {}", count(activity.active_days), activity.days.unwrap_or(profile.activity_days as i64)), text()),
    ]);
    match activity.longest_gap_days {
        Some(gap) if gap > 0 => doc.fact("Longest gap", vec![
            s(format!("{} days, ending {}", gap, when(activity.longest_gap_end.as_deref())), text()),
        ]),
        _ => doc.fact("Longest gap", vec![s("none", label())]),
    }
    doc.fact("Retention", vec![s(format!("{} days", count(activity.retention_days)), text())]);

    if activity.total_events.unwrap_or(0) == 0 {
        doc.blank();
        doc.note("No activity recorded in this period.");
        return;
    }

    let by_bytes = activity.total_bytes.unwrap_or(0) > 0;
    let value_of = |events: Option<i64>, bytes: Option<i64>| if by_bytes { bytes.unwrap_or(0) } else { events.unwrap_or(0) };

    doc.subsection(&format!("Hour of day by weekday (UTC), colored by {}", if by_bytes { "traffic" } else { "events" }));
    heatmap(doc, activity, value_of);

    doc.subsection("Daily activity");
    calendar(doc, activity, value_of, now);

    doc.blank();
    doc.push(vec![
        s("  ", text()), s("·", faint()), s(" none  ", label()),
        s("██", Style::default().fg(traffic_color(0.0))), s(" quiet  ", label()),
        s("██", Style::default().fg(traffic_color(0.5))), s(" busy  ", label()),
        s("██", Style::default().fg(traffic_color(1.0))), s(" busiest, capped at the 95th percentile", label()),
    ]);
}

/// 95th percentile of the non-zero cells, so one exceptional hour does not flatten the rest.
fn ceiling(values: &[i64]) -> i64 {
    let mut sorted: Vec<i64> = values.iter().copied().filter(|v| *v > 0).collect();
    if sorted.is_empty() {
        return 1;
    }
    sorted.sort_unstable();
    sorted[((sorted.len() as f64 * 0.95).floor() as usize).min(sorted.len() - 1)].max(1)
}

fn cell_style(value: i64, cap: i64) -> Style {
    Style::default().fg(traffic_color((value as f64 / cap as f64).clamp(0.0, 1.0)))
}

fn heatmap(doc: &mut Doc, activity: &AssetActivityResponse, value_of: impl Fn(Option<i64>, Option<i64>) -> i64) {
    let mut grid = [[0i64; 24]; 7];
    for cell in &activity.heatmap {
        let (Some(dow), Some(hour)) = (cell.day_of_week, cell.hour) else { continue; };
        if (1..=7).contains(&dow) && (0..24).contains(&hour) {
            grid[(dow - 1) as usize][hour as usize] += value_of(cell.events, cell.bytes);
        }
    }
    let cap = ceiling(&grid.iter().flatten().copied().collect::<Vec<_>>());
    let wide = doc.width >= 60;
    let cell_width = if wide { 2 } else { 1 };

    let mut axis = String::from("       ");
    for hour in 0..24 {
        if wide {
            axis.push_str(&if hour % 3 == 0 { format!("{:<6}", format!("{:02}", hour)) } else { String::new() });
        } else if hour % 6 == 0 {
            axis.push_str(&format!("{:<6}", format!("{:02}", hour)));
        }
    }
    doc.push(vec![s(truncate(&axis, 7 + 24 * cell_width), label())]);

    for (d, row) in grid.iter().enumerate() {
        let mut spans = vec![s(format!("  {:<5}", WEEKDAYS[d]), label())];
        for value in row {
            if *value == 0 {
                spans.push(s(if wide { "· " } else { "·" }, faint()));
            } else {
                spans.push(s("█".repeat(cell_width), cell_style(*value, cap)));
            }
        }
        doc.push(spans);
    }
}

fn calendar(doc: &mut Doc, activity: &AssetActivityResponse, value_of: impl Fn(Option<i64>, Option<i64>) -> i64, now: i64) {
    let days = activity.days.unwrap_or(90).max(1);
    let today = now.div_euclid(86400);
    let start = today - (days - 1);
    // 1970-01-01 was a Thursday; ISO weekdays start on Monday.
    let weekday = |day: i64| (day + 3).rem_euclid(7) as usize;
    let grid_start = start - weekday(start) as i64;
    let weeks = ((today - grid_start) / 7 + 1) as usize;

    let mut values: HashMap<i64, i64> = HashMap::new();
    for day in &activity.calendar {
        if let Some(unix) = day.day.as_deref().and_then(|d| parse_iso8601_to_unix(&format!("{}T00:00:00Z", d))) {
            values.insert(unix.div_euclid(86400), value_of(day.events, day.bytes));
        }
    }
    let cap = ceiling(&values.values().copied().collect::<Vec<_>>());

    let cell_width = if doc.width >= 7 + weeks * 2 { 2 } else { 1 };

    let mut months = String::from("       ");
    let mut last_month = None;
    for week in 0..weeks {
        let monday = grid_start + week as i64 * 7;
        let (_, month, _) = civil(monday);
        if last_month != Some(month) && (week == 0 || month != civil(monday - 7).1) {
            let label = MONTHS[(month - 1) as usize];
            if months.chars().count() <= 7 + week * cell_width {
                months.push_str(&" ".repeat((7 + week * cell_width).saturating_sub(months.chars().count())));
                months.push_str(label);
            }
        }
        last_month = Some(month);
    }
    doc.push(vec![s(months, label())]);

    for d in 0..7 {
        let mut spans = vec![s(format!("  {:<5}", WEEKDAYS[d]), label())];
        for week in 0..weeks {
            let day = grid_start + week as i64 * 7 + d as i64;
            if day < start || day > today {
                spans.push(s(" ".repeat(cell_width), faint()));
                continue;
            }
            match values.get(&day).copied().unwrap_or(0) {
                0 => spans.push(s(if cell_width == 2 { "· " } else { "·" }, faint())),
                value => spans.push(s("█".repeat(cell_width), cell_style(value, cap))),
            }
        }
        doc.push(spans);
    }
}

fn civil(days: i64) -> (i64, i64, i64) {
    let z = days + 719468;
    let era = if z >= 0 { z } else { z - 146096 } / 146097;
    let doe = z - era * 146097;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    (if m <= 2 { y + 1 } else { y }, m, d)
}

#[derive(Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
enum Direction { Outbound, Inbound, Both }

struct Peer {
    label: String,
    detail: String,
    external: bool,
    direction: Direction,
    sessions: i64,
    sent: i64,
    received: i64,
    country_code: Option<String>,
    coordinates: Option<(f64, f64)>,
    last_seen: Option<String>,
    ports: BTreeMap<i64, i64>,
}

impl Peer {
    fn total(&self) -> i64 { self.sent + self.received }
}

fn peer_key(edge: &AssetCommunicationEdgeResponse) -> String {
    format!("{}|{}", edge.peer_type.as_deref().unwrap_or(""), edge.peer_key.as_deref().unwrap_or(""))
}

fn internal_label(edge: &AssetCommunicationEdgeResponse) -> (String, String) {
    let address = edge.peer_address.clone().unwrap_or_default();
    let name = edge.peer.as_ref().and_then(|p| p.mac.as_ref()).and_then(|m| m.context.as_ref()).and_then(|c| c.name.clone())
        .or_else(|| edge.peer_asset.as_ref().and_then(|a| a.name.clone()));
    (address, name.unwrap_or_default())
}

fn peers(communication: &AssetCommunicationResponse) -> Vec<Peer> {
    let mut by_key: BTreeMap<String, Peer> = BTreeMap::new();

    for edge in &communication.edges {
        let external = edge.peer_type.as_deref() == Some("EXTERNAL");
        let direction = match edge.direction.as_deref() {
            Some("INBOUND") => Direction::Inbound,
            _ => Direction::Outbound,
        };
        let entry = by_key.entry(peer_key(edge)).or_insert_with(|| {
            let (label, detail) = if external {
                let label = external_peer_label(edge.peer_asn_name.as_deref(), edge.peer_asn_number, edge.peer_country_code.as_deref());
                let mut detail = Vec::new();
                if let Some(city) = edge.peer_city.as_deref().filter(|c| !c.is_empty()) { detail.push(city.to_string()); }
                if let Some(code) = edge.peer_country_code.as_deref().filter(|c| !c.is_empty()) { detail.push(country_name(code).to_string()); }
                (label, detail.join(", "))
            } else {
                internal_label(edge)
            };
            let coordinates = match (edge.peer_latitude, edge.peer_longitude) {
                (Some(lat), Some(lng)) => Some((lat, lng)),
                _ => edge.peer_country_code.as_deref().and_then(country_centroid),
            };
            Peer {
                label, detail, external, direction, sessions: 0, sent: 0, received: 0,
                country_code: edge.peer_country_code.clone(), coordinates, last_seen: None, ports: BTreeMap::new(),
            }
        });

        if entry.direction != direction {
            entry.direction = Direction::Both;
        }
        entry.sessions += edge.sessions.unwrap_or(0);
        entry.sent += edge.bytes_tx.unwrap_or(0);
        entry.received += edge.bytes_rx.unwrap_or(0);
        if let Some(port) = edge.port.filter(|p| *p > 0) {
            *entry.ports.entry(port).or_default() += edge.sessions.unwrap_or(0);
        }
        let newer = match (&entry.last_seen, &edge.last_seen) {
            (Some(a), Some(b)) => parse_iso8601_to_unix(b) > parse_iso8601_to_unix(a),
            (None, Some(_)) => true,
            _ => false,
        };
        if newer {
            entry.last_seen = edge.last_seen.clone();
        }
    }

    let mut peers: Vec<Peer> = by_key.into_values().collect();
    peers.sort_by(|a, b| b.total().cmp(&a.total()).then_with(|| b.sessions.cmp(&a.sessions)));
    peers
}

fn direction_label(direction: Direction) -> Span<'static> {
    match direction {
        Direction::Outbound => s("→ out", Style::default().fg(Color::Rgb(0x38, 0xbd, 0xf8))),
        Direction::Inbound => s("← in", Style::default().fg(Color::Rgb(0xfb, 0x71, 0x85))),
        Direction::Both => s("↔ both", Style::default().fg(Color::Rgb(0xfd, 0xe6, 0x8a))),
    }
}

fn communication(doc: &mut Doc, profile: &AssetProfile, now: i64) {
    doc.section(&format!("Communication, {}", profile.options_label));
    let communication = match &profile.communication {
        Err(e) => { unavailable(doc, "The communication", e); return; }
        Ok(c) => c,
    };

    let all = peers(communication);
    let internal: Vec<&Peer> = all.iter().filter(|p| !p.external).collect();
    let external: Vec<&Peer> = all.iter().filter(|p| p.external).collect();
    let sessions: i64 = all.iter().map(|p| p.sessions).sum();
    let traffic: i64 = all.iter().map(|p| p.total()).sum();
    let inbound_internal = internal.iter().filter(|p| p.direction != Direction::Outbound).count();
    let inbound_external = external.iter().filter(|p| p.direction != Direction::Outbound).count();

    doc.fact("Internal peers", vec![s(format_count(internal.len() as i64), text())]);
    doc.fact("External networks", vec![s(format_count(external.len() as i64), text())]);
    doc.fact("Sessions", vec![s(format_count(sessions), text())]);
    doc.fact("Traffic", vec![s(format_bytes(traffic), text())]);
    doc.fact("Inbound from", vec![
        s(format!("{} internal peers", inbound_internal), text()),
        s(format!("  ({} external networks)", inbound_external), if inbound_external > 0 { warn() } else { label() }),
    ]);
    doc.fact("Retention", vec![s(format!("{} days", count(communication.retention_days)), text())]);

    if all.is_empty() {
        doc.blank();
        doc.note("No communication was recorded for this asset in the selected time range.");
        return;
    }

    doc.subsection("Constellation");
    constellation(doc, &profile.label(), &internal, &external);

    if !internal.is_empty() {
        doc.subsection(&format!("Top internal peers ({})", internal.len()));
        peer_table(doc, &internal, now, false);
    }
    if !external.is_empty() {
        doc.subsection(&format!("Top external networks ({})", external.len()));
        peer_table(doc, &external, now, true);
    }
}

fn peer_table(doc: &mut Doc, peers: &[&Peer], now: i64, external: bool) {
    let max = peers.iter().map(|p| p.total()).max().unwrap_or(0);
    // Wide enough for a full IPv6 address where one is listed.
    let label_width = peers.iter().take(15).map(|p| p.label.chars().count() + 1).max().unwrap_or(0).clamp(30, 40);
    let rows = peers.iter().take(15).map(|p| {
        let ports: Vec<String> = {
            let mut ranked: Vec<(&i64, &i64)> = p.ports.iter().collect();
            ranked.sort_by(|a, b| b.1.cmp(a.1));
            ranked.iter().take(4).map(|(port, _)| port.to_string()).collect()
        };
        vec![
            s(p.label.clone(), if external { text() } else { machine() }),
            s(p.detail.clone(), label()),
            direction_label(p.direction),
            s(ports.join(" "), label()),
            s(format_count(p.sessions), text()),
            s(format_bytes(p.sent), text()),
            s(format_bytes(p.received), text()),
            share(p.total(), max, 12),
            s(age(p.last_seen.as_deref(), now), label()),
        ]
    }).collect();
    doc.table(&[
        Column::left(if external { "Network" } else { "Peer" }, label_width), Column::left(if external { "Location" } else { "Name" }, 22),
        Column::left("Dir", 7), Column::left("Ports", 18), Column::right("Sessions", 9), Column::right("Sent", 9),
        Column::right("Received", 9), Column::left("", 12), Column::left("Last seen", 0),
    ], rows);
    if peers.len() > 15 {
        doc.text(2, &format!("{} more.", peers.len() - 15), label());
    }
}

fn constellation(doc: &mut Doc, name: &str, internal: &[&Peer], external: &[&Peer]) {
    const MAX_ROWS: usize = 9;
    const CONNECTOR: usize = 7;

    let left: Vec<&Peer> = internal.iter().copied().take(MAX_ROWS).collect();
    let right: Vec<&Peer> = external.iter().copied().take(MAX_ROWS).collect();
    let rows = left.len().max(right.len()).max(3);
    let max = internal.iter().chain(external.iter()).map(|p| p.total()).max().unwrap_or(1).max(1);

    let box_label = truncate(name, 24);
    let box_width = box_label.chars().count() + 6;
    let fixed = CONNECTOR * 2 + 4 + box_width;
    let side = doc.width.saturating_sub(fixed + 4) / 2;
    if side < 10 {
        doc.note("The terminal is too narrow for the constellation.");
        return;
    }
    let label_of = |p: &Peer| {
        let full = if p.detail.is_empty() { p.label.clone() } else { format!("{} ({})", p.label, p.detail) };
        truncate(&full, side)
    };

    let middle = rows / 2;
    let box_top = middle.saturating_sub(1);
    let line_of = |p: &Peer, peer_on_left: bool| -> (String, Style) {
        let weight = p.total() as f64 / max as f64;
        let heavy = weight > 0.5;
        let body = if heavy { "━" } else { "─" }.repeat(CONNECTOR - 2);
        let (left_head, right_head) = if heavy { ("◀", "▶") } else { ("◁", "▷") };
        let bar = if heavy { "━" } else { "─" };
        let (to_peer, to_asset) = match p.direction {
            Direction::Outbound => (true, false),
            Direction::Inbound => (false, true),
            Direction::Both => (true, true),
        };
        let text = if peer_on_left {
            format!("{}{}{}", if to_peer { left_head } else { bar }, body, if to_asset { right_head } else { bar })
        } else {
            format!("{}{}{}", if to_asset { left_head } else { bar }, body, if to_peer { right_head } else { bar })
        };
        (text, Style::default().fg(traffic_color(weight)))
    };

    for row in 0..rows {
        let mut spans: Vec<Span<'static>> = vec![Span::raw("  ")];

        match left.get(row) {
            Some(p) => {
                let (line, style) = line_of(p, true);
                spans.push(s(pad(&label_of(p), side, Align::Right), if p.external { text() } else { machine() }));
                spans.push(s(" ", text()));
                spans.push(s(line, style));
                spans.push(s("┤", faint()));
            }
            None => {
                spans.push(s(" ".repeat(side + 1 + CONNECTOR), text()));
                spans.push(s(if row < left.len().max(1) || (row >= box_top && row <= box_top + 2) { "│" } else { " " }, faint()));
            }
        }

        let center: Vec<Span<'static>> = if row == box_top {
            vec![s(format!(" ╔{}╗ ", "═".repeat(box_width - 2)), Style::default().fg(ASSET_COLOR))]
        } else if row == box_top + 1 {
            vec![
                s(" ║ ", Style::default().fg(ASSET_COLOR)),
                s("◉ ", Style::default().fg(ASSET_COLOR).add_modifier(Modifier::BOLD)),
                s(box_label.clone(), bright()),
                s(" ║ ", Style::default().fg(ASSET_COLOR)),
            ]
        } else if row == box_top + 2 {
            vec![s(format!(" ╚{}╝ ", "═".repeat(box_width - 2)), Style::default().fg(ASSET_COLOR))]
        } else {
            vec![s(" ".repeat(box_width + 2), text())]
        };
        spans.extend(center);

        match right.get(row) {
            Some(p) => {
                let (line, style) = line_of(p, false);
                spans.push(s("├", faint()));
                spans.push(s(line, style));
                spans.push(s(" ", text()));
                spans.push(s(label_of(p), text()));
            }
            None => {
                spans.push(s(if row < right.len().max(1) || (row >= box_top && row <= box_top + 2) { "│" } else { " " }, faint()));
            }
        }

        doc.push(spans);
    }

    doc.blank();
    doc.push(vec![
        s("  ", text()),
        s("━▶", Style::default().fg(traffic_color(1.0))), s(" busy  ", label()),
        s("─▷", Style::default().fg(traffic_color(0.0))), s(" quiet  ", label()),
        s("arrows point the way the traffic flows", label()),
    ]);
    if internal.len() > left.len() || external.len() > right.len() {
        doc.text(2, &format!("Showing the busiest {} of {} internal peers and {} of {} external networks.",
                             left.len(), internal.len(), right.len(), external.len()), label());
    }
}

fn map(doc: &mut Doc, profile: &AssetProfile) {
    doc.section(&format!("Map, {}", profile.options_label));
    let communication = match &profile.communication {
        Err(e) => { unavailable(doc, "The communication", e); return; }
        Ok(c) => c,
    };

    let all = peers(communication);
    let external: Vec<&Peer> = all.iter().filter(|p| p.external).collect();
    if all.is_empty() {
        doc.note("No communication was recorded for this asset in the selected time range.");
        return;
    }
    if external.is_empty() {
        doc.note("All communication in the selected time range is internal. The map shows external networks only.");
        return;
    }

    let located: Vec<&Peer> = external.iter().copied().filter(|p| p.coordinates.is_some()).collect();
    if located.is_empty() {
        doc.note("No GeoIP data. Placing external networks on the map needs their country, which Nzyme Connect provides.");
        return;
    }

    let max = located.iter().map(|p| p.total()).max().unwrap_or(1).max(1);
    let mut markers: Vec<Marker> = located.iter().map(|p| {
        let (lat, lng) = p.coordinates.unwrap_or((0.0, 0.0));
        Marker { lat, lng, weight: (p.total() as f64 / max as f64).sqrt(), kind: MarkerKind::Peer }
    }).collect();

    let origin = communication.origin.as_ref().and_then(|o| match (o.latitude, o.longitude) {
        (Some(lat), Some(lng)) => Some((lat, lng, o.location_name.clone().or_else(|| o.tap_name.clone()))),
        _ => None,
    });
    if let Some((lat, lng, _)) = &origin {
        markers.push(Marker { lat: *lat, lng: *lng, weight: 1.0, kind: MarkerKind::Origin });
    }

    let active: HashSet<String> = located.iter().filter_map(|p| p.country_code.clone()).collect();
    let map_width = doc.width.saturating_sub(4);
    doc.blank();
    for line in worldmap::render(map_width, &markers, &active) {
        let mut spans = vec![Span::raw("  ")];
        spans.extend(line.spans);
        doc.push(spans);
    }

    doc.blank();
    let mut legend = vec![s("  ", text())];
    match &origin {
        Some((_, _, name)) => {
            legend.push(s("★", Style::default().fg(ASSET_COLOR).add_modifier(Modifier::BOLD)));
            legend.push(s(format!(" {}  ", name.clone().unwrap_or_else(|| "this asset".to_string())), label()));
        }
        None => legend.push(s("asset location unknown, assign its tap to a location with coordinates  ", label())),
    }
    legend.push(s("○", Style::default().fg(traffic_color(0.0))));
    legend.push(s(" quiet  ", label()));
    legend.push(s("◉", Style::default().fg(traffic_color(0.5))));
    legend.push(s(" busy  ", label()));
    legend.push(s("●", Style::default().fg(traffic_color(1.0))));
    legend.push(s(" busiest network  ", label()));
    legend.push(s("▀", Style::default().fg(Color::Rgb(0x8a, 0x5a, 0x1c))));
    legend.push(s(" countries with traffic", label()));
    doc.push(legend);

    let unlocated = external.len() - located.len();
    if unlocated > 0 {
        doc.text(2, &format!("{} external networks without a known country are not on the map.", unlocated), label());
    }

    let mut by_country: BTreeMap<String, (i64, i64, usize)> = BTreeMap::new();
    for p in &located {
        let Some(code) = &p.country_code else { continue; };
        let entry = by_country.entry(code.clone()).or_default();
        entry.0 += p.total();
        entry.1 += p.sessions;
        entry.2 += 1;
    }
    let mut ranked: Vec<(String, (i64, i64, usize))> = by_country.into_iter().collect();
    ranked.sort_by(|a, b| b.1.0.cmp(&a.1.0));
    let max_country = ranked.first().map(|r| r.1.0).unwrap_or(1).max(1);

    doc.subsection(&format!("Countries ({})", ranked.len()));
    let rows = ranked.iter().take(12).map(|(code, (traffic, sessions, networks))| vec![
        s(country_name(code), text()),
        s(code.clone(), label()),
        s(format_count(*networks as i64), text()),
        s(format_count(*sessions), text()),
        s(format_bytes(*traffic), text()),
        share(*traffic, max_country, 16),
    ]).collect();
    doc.table(&[
        Column::left("Country", 30), Column::left("", 4), Column::right("Networks", 9), Column::right("Sessions", 9),
        Column::right("Traffic", 10), Column::left("", 0),
    ], rows);
}

fn changes(doc: &mut Doc, profile: &AssetProfile, now: i64) {
    doc.section(&format!("Changes, {}", profile.options_label));
    let timeline = match &profile.timeline {
        Err(e) => { unavailable(doc, "The timeline", e); return; }
        Ok(t) => t,
    };

    if timeline.events.is_empty() {
        doc.note("No changes in the selected time range.");
        return;
    }

    let total = timeline.total.unwrap_or(timeline.events.len() as i64);
    doc.text(2, &format!("{} changes{}.", format_count(total),
                         if total as usize > timeline.events.len() { format!(", newest {} shown", timeline.events.len()) } else { String::new() }), label());
    doc.blank();

    for event in &timeline.events {
        let event_type = event.event_type.as_deref().unwrap_or("");
        let change = describe_change(event_type, event.value.as_deref(), event.detail.as_deref());
        let kind = timeline_kind_title(event_type, event.detail.as_deref());

        let mut spans = vec![
            s(format!("  {:<14}", age(event.timestamp.as_deref(), now)), label()),
            s(format!("{:<26}", truncate(&kind, 25)), accent()),
            s(change.action.clone(), text()),
        ];
        if let Some(value) = change_value(profile, event_type, event.detail.as_deref(), change.value.clone()) {
            spans.push(s(format!(" {}", value), if event_type == "ALERT" { warn() } else { machine() }));
        }
        doc.push(spans);
        if let Some(explanation) = &change.explanation {
            doc.text(42, explanation, faint());
        }
    }

    doc.blank();
    let retention = timeline.attribute_retention_days.map(|d| format!(" of {} days", format_count(d))).unwrap_or_default();
    doc.note(&format!("First seen, hostname and IP address changes are kept for as long as the asset exists. Attribute changes are kept for the attribute retention time{}, counted from when the value was last seen. Alerts stay until they are deleted.", retention));
}

fn detections(doc: &mut Doc, profile: &AssetProfile, now: i64) {
    doc.section("Alerts");
    match &profile.alerts {
        Err(e) => unavailable(doc, "The alerts", e),
        Ok(alerts) if alerts.alerts.is_empty() => doc.note("No alerts involve this asset."),
        Ok(alerts) => {
            let rows = alerts.alerts.iter().map(|a| {
                let active = a.active_count.unwrap_or(0);
                vec![
                    s(a.detection_type_description.clone().or_else(|| a.detection_type.clone()).unwrap_or_default(), bright()),
                    s(alert_role_title(a.role.as_deref().unwrap_or("")), text()),
                    s(format_count(active), if active > 0 { bad() } else { label() }),
                    s(count(a.count), text()),
                    s(age(a.last_seen.as_deref(), now), label()),
                ]
            }).collect();
            doc.table(&[
                Column::left("Alert", 44), Column::left("Role", 16), Column::right("Active", 7), Column::right("Total", 7),
                Column::left("Last seen", 0),
            ], rows);
            doc.blank();
            doc.note("Actor: this asset caused the alert. Victim: it was the target. Subject: the alert is about the asset itself.");
        }
    }

    doc.section("Findings");
    match &profile.findings {
        Err(e) => unavailable(doc, "The findings", e),
        Ok(findings) if findings.findings.is_empty() => doc.note("No scans, beacons, tunnels or spoofing findings involve this asset."),
        Ok(findings) => {
            let rows = findings.findings.iter().map(|f| vec![
                s(finding_title(f.finding_type.as_deref().unwrap_or("")), bright()),
                s(alert_role_title(f.role.as_deref().unwrap_or("")), text()),
                s(count(f.count), text()),
                s(age(f.last_seen.as_deref(), now), label()),
            ]).collect();
            doc.table(&[Column::left("Finding", 32), Column::left("Role", 16), Column::right("Count", 7), Column::left("Last seen", 0)], rows);
        }
    }

    doc.section("Exclusions");
    match &profile.exclusions {
        Err(e) => unavailable(doc, "The exclusions", e),
        Ok(exclusions) if exclusions.exclusions.is_empty() => doc.note("No detection exclusions cover this asset."),
        Ok(exclusions) => {
            let rows = exclusions.exclusions.iter().map(|e| vec![
                s(exclusion_title(e.exclusion_type.as_deref().unwrap_or("")), accent()),
                s(e.name.clone().unwrap_or_default(), text()),
                if e.enabled.unwrap_or(true) { s("enabled", good()) } else { s("disabled", label()) },
                s(e.matched_by.clone().unwrap_or_default(), machine()),
                s(count(e.match_count), text()),
                s(e.last_matched_at.as_deref().map(|t| age(Some(t), now)).unwrap_or_else(|| "never".to_string()), label()),
            ]).collect();
            doc.table(&[
                Column::left("Type", 18), Column::left("Name", 30), Column::left("State", 9), Column::left("Matched by", 22),
                Column::right("Matches", 8), Column::left("Last match", 0),
            ], rows);
        }
    }
}

fn risks(doc: &mut Doc, profile: &AssetProfile, now: i64) {
    doc.section(&format!("Risk signals, {}", profile.options_label));
    let risks = match &profile.risks {
        Err(e) => { unavailable(doc, "The risk signals", e); return; }
        Ok(r) => r,
    };

    if risks.signals.is_empty() {
        doc.note("No risk signals.");
    } else {
        let mut ranked: Vec<_> = risks.signals.iter().collect();
        let rank = |severity: Option<&str>| match severity { Some("CRITICAL") => 0, Some("WARNING") => 1, _ => 2 };
        ranked.sort_by_key(|r| rank(r.severity.as_deref()));

        for signal in ranked {
            let (title, description) = risk_signal(signal.signal.as_deref().unwrap_or(""));
            let tag = match signal.severity.as_deref() {
                Some("CRITICAL") => s("CRITICAL", bad()),
                Some("WARNING") => s("WARNING ", warn()),
                _ => s("INFO    ", Style::default().fg(Color::Rgb(0x38, 0xbd, 0xf8))),
            };
            let mut spans = vec![s("  ", text()), tag, s("  ", text()), s(title.to_string(), bright())];
            if let Some(c) = signal.count.filter(|c| *c > 0) {
                spans.push(s(format!("  ×{}", format_count(c)), text()));
            }
            if signal.signal.as_deref() != Some("INBOUND_EXTERNAL_SESSIONS") {
                if let Some(detail) = signal.detail.as_deref().filter(|d| !d.is_empty()) {
                    spans.push(s(format!("  {}", detail), machine()));
                }
            }
            if signal.last_seen.is_some() {
                spans.push(s(format!("  {}", age(signal.last_seen.as_deref(), now)), label()));
            }
            doc.push(spans);
            if !description.is_empty() {
                doc.text(12, description, faint());
            }
        }
    }

    let tls = risks.tls_sessions.unwrap_or(0);
    if tls > 0 {
        doc.blank();
        let versions: Vec<String> = risks.tls_versions.iter().map(|(v, n)| format!("{} {}", v, format_count(*n))).collect();
        let hidden = risks.tls_versions.get("TLSv1.3").copied().unwrap_or(0);
        doc.text(2, &format!("{} TLS sessions in the time range: {}. The certificate signals could inspect {:.0} % of them; TLS 1.3 encrypts the certificate handshake.",
                             format_count(tls), versions.join(", "), (tls - hidden) as f64 / tls as f64 * 100.0), label());
    }

    doc.blank();
    doc.note("The time range applies to the TLS and inbound session signals. Protocol signals come from the asset attributes, which are kept for the attribute retention time set in the asset settings. Detections and alerts follow their own retention.");
}

fn identity(doc: &mut Doc, profile: &AssetProfile, now: i64) {
    let asset = &profile.located.asset;

    doc.section("Identity");
    doc.fact("MAC address", vec![s(profile.mac().to_string(), machine())]);
    if let Some(oui) = asset.oui.as_deref().filter(|o| !o.is_empty()) {
        doc.fact("Vendor", vec![s(oui.to_string(), text())]);
    }
    if let Some(context) = asset.mac.as_ref().and_then(|m| m.context.as_ref()) {
        if let Some(name) = context.name.as_deref().filter(|n| !n.is_empty()) {
            doc.fact("Context name", vec![s(name.to_string(), bright())]);
        }
        if let Some(description) = context.description.as_deref().filter(|d| !d.is_empty()) {
            doc.fact("Description", vec![s(description.to_string(), text())]);
        }
        if let Some(notes) = context.notes.as_deref().filter(|n| !n.trim().is_empty()) {
            doc.fact("Notes", vec![]);
            doc.text(4, notes, text());
        }
    }
    let seen: Vec<&str> = [("ARP", asset.seen_arp), ("NDP", asset.seen_ndp), ("DHCP", asset.seen_dhcp), ("TCP", asset.seen_tcp), ("UDP", asset.seen_udp)]
        .iter().filter(|(_, seen)| seen.unwrap_or(false)).map(|(name, _)| *name).collect();
    doc.fact("Seen in", vec![s(if seen.is_empty() { "n/a".to_string() } else { seen.join(", ") }, text())]);
    doc.fact("Asset UUID", vec![s(profile.located.uuid().to_string(), faint())]);

    doc.subsection("Hostnames");
    match &profile.hostnames {
        Err(e) => unavailable(doc, "The hostnames", e),
        Ok((hostnames, _)) if hostnames.is_empty() => doc.note("No hostnames recorded."),
        Ok((hostnames, total)) => {
            let rows = hostnames.iter().map(|h| vec![
                s(h.hostname.clone().unwrap_or_default(), machine()),
                s(h.source.clone().unwrap_or_default(), label()),
                s(when(h.first_seen.as_deref()), label()),
                s(age(h.last_seen.as_deref(), now), text()),
            ]).collect();
            doc.table(&[Column::left("Hostname", 44), Column::left("Source", 10), Column::left("First seen", 20), Column::left("Last seen", 0)], rows);
            if *total as usize > hostnames.len() {
                doc.text(2, &format!("{} more.", *total as usize - hostnames.len()), label());
            }
        }
    }

    doc.subsection("IP addresses");
    match &profile.ip_addresses {
        Err(e) => unavailable(doc, "The IP addresses", e),
        Ok((addresses, _)) if addresses.is_empty() => doc.note("No IP addresses recorded."),
        Ok((addresses, total)) => {
            let rows = addresses.iter().map(|a| vec![
                s(a.address.clone().unwrap_or_default(), machine()),
                s(a.source.clone().unwrap_or_default(), label()),
                s(when(a.first_seen.as_deref()), label()),
                s(age(a.last_seen.as_deref(), now), text()),
            ]).collect();
            doc.table(&[Column::left("Address", 44), Column::left("Source", 10), Column::left("First seen", 20), Column::left("Last seen", 0)], rows);
            if *total as usize > addresses.len() {
                doc.text(2, &format!("{} more.", *total as usize - addresses.len()), label());
            }
        }
    }

    doc.subsection("Fingerprints");
    let dhcp: Vec<(&str, Option<&str>)> = vec![
        ("DHCP initial", asset.dhcp_fingerprint_initial.as_deref()),
        ("DHCP renew", asset.dhcp_fingerprint_renew.as_deref()),
        ("DHCP reboot", asset.dhcp_fingerprint_reboot.as_deref()),
        ("DHCP rebind", asset.dhcp_fingerprint_rebind.as_deref()),
    ];
    let mut any = false;
    for (name, value) in dhcp {
        if let Some(v) = value.filter(|v| !v.is_empty()) {
            doc.fact(name, vec![s(v.to_string(), machine())]);
            any = true;
        }
    }
    if let Ok(attributes) = &profile.attributes {
        for dimension in ["TCP_FINGERPRINT", "JA4", "DHCP_FINGERPRINT", "DHCP_VENDOR_CLASS", "NTP_CLIENT_VERSION", "TLS_VERSION", "NAME_RESOLUTION_PROTOCOL"] {
            let Some(values) = attributes.attributes.get(dimension) else { continue; };
            if values.is_empty() {
                continue;
            }
            any = true;
            let mut ranked: Vec<&AssetAttributeResponse> = values.iter().collect();
            ranked.sort_by(|a, b| b.count.unwrap_or(0).cmp(&a.count.unwrap_or(0)));
            doc.blank();
            doc.push(vec![s(format!("  {} ({})", dimension_title(dimension), values.len()), accent())]);
            let rows = ranked.iter().take(8).map(|v| vec![
                s(v.value.clone().unwrap_or_default(), machine()),
                s(count(v.count), text()),
                s(when(v.first_seen.as_deref()), label()),
                s(age(v.last_seen.as_deref(), now), text()),
            ]).collect();
            doc.table(&[Column::left("Value", 52), Column::right("Seen", 10), Column::left("First seen", 20), Column::left("Last seen", 0)], rows);
            if ranked.len() > 8 {
                doc.text(2, &format!("{} more.", ranked.len() - 8), label());
            }
        }
    }
    if !any {
        doc.note("No fingerprints recorded.");
    }
}

pub fn badge(profile: &AssetProfile, section: Section) -> Option<String> {
    match section {
        Section::Detections => {
            let active = profile.alerts.as_ref().ok().and_then(|a| a.active).unwrap_or(0);
            if active > 0 { Some(format!("{} active", active)) } else { None }
        }
        Section::Risks => {
            let n = profile.risks.as_ref().ok().map(|r| r.signals.len()).unwrap_or(0);
            if n > 0 { Some(n.to_string()) } else { None }
        }
        Section::Changes => {
            let n = profile.timeline.as_ref().ok().and_then(|t| t.total).unwrap_or(0);
            if n > 0 { Some(format_count(n)) } else { None }
        }
        _ => None,
    }
}

pub fn load_warning(profile: &AssetProfile) -> Option<String> {
    let errors = profile.section_errors();
    if errors.is_empty() {
        return None;
    }
    Some(format!("{} could not be loaded", errors.iter().map(|(n, _)| *n).collect::<Vec<_>>().join(", ")))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wraps_and_truncates() {
        assert_eq!(wrap("one two three", 8), vec!["one two", "three"]);
        assert_eq!(wrap("", 8), vec![""]);
        assert_eq!(truncate("abcdef", 4), "abc…");
        assert_eq!(truncate("abc", 4), "abc");
    }

    #[test]
    fn ceilings() {
        assert_eq!(ceiling(&[]), 1);
        assert_eq!(ceiling(&[0, 0, 5]), 5);
        let many: Vec<i64> = (1..=100).collect();
        assert_eq!(ceiling(&many), 96);
    }

    #[test]
    fn civil_dates() {
        assert_eq!(civil(0), (1970, 1, 1));
        assert_eq!(civil(19_000), (2022, 1, 8));
    }
}
