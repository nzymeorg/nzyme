//! Locations overview, modeled on the web interface locations page: one row per location with taps, detection
//! alerts, the current weather observation and severe environmental alerts. Enter expands a location to show its
//! description, coordinates, local time, floors, taps, environmental alerts and active detection alerts.

use std::collections::HashSet;
use std::sync::{Arc, Mutex};

use ratatui::crossterm::event::{KeyCode, KeyEvent};
use ratatui::layout::{Constraint, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span, Text};
use ratatui::widgets::{Cell, Row, Table};
use ratatui::Frame;

use crate::api::endpoints::{locations, user};
use crate::api::error::ApiError;
use crate::api::types::{LocationEnvironmentDataResponse, LocationSummaryResponse};
use crate::apps::api::gui::common::{
    header_cell, lock, palette, render_message, row_highlight_style, skipped_label, titled_block, Poller,
    ScrollableTable, HIGHLIGHT_SYMBOL,
};
use crate::apps::api::gui::prefs::Context;
use crate::apps::api::gui::view::{View, ViewAction};
use crate::tools::{format_age_seconds, now_unix, parse_iso8601_to_unix};

#[derive(Clone, Copy, PartialEq)]
enum UnitSystem {
    Metric,
    Imperial,
}

impl UnitSystem {
    fn temperature(&self, celsius: f64) -> String {
        match self {
            UnitSystem::Metric => format!("{:.0}°C", celsius),
            UnitSystem::Imperial => format!("{:.0}°F", celsius * 1.8 + 32.0),
        }
    }

    fn speed(&self, kmh: f64) -> String {
        match self {
            UnitSystem::Metric => format!("{:.0} km/h", kmh),
            UnitSystem::Imperial => format!("{:.0} mph", kmh * 0.621371),
        }
    }

    fn distance_km(&self, km: f64) -> String {
        match self {
            UnitSystem::Metric => format!("{:.1} km", km),
            UnitSystem::Imperial => format!("{:.1} miles", km * 0.621371),
        }
    }
}

struct LocationRow {
    id: String,
    name: String,
    tenant: String,
    description: String,
    coordinates: Option<(f64, f64)>,
    timezone: Option<String>,
    tap_count: i64,
    taps_online: usize,
    taps: Vec<(String, String, Option<bool>)>,
    floors: Vec<(String, i64, i64, bool)>,
    /// None when the user lacks the alerts_view permission.
    alert_count: Option<i64>,
    alerts: Vec<(String, String, bool)>,
    environment: Option<LocationEnvironmentDataResponse>,
}

struct LocationsData {
    rows: Vec<LocationRow>,
    units: UnitSystem,
    skipped_tenants: usize,
    scope_label: String,
}

pub struct LocationsView {
    poller: Poller<LocationsData>,
    table: ScrollableTable,
    expanded: HashSet<String>,
}

impl LocationsView {
    pub fn new(ctx: Arc<Context>) -> Self {
        let units: Arc<Mutex<Option<UnitSystem>>> = Arc::new(Mutex::new(None));
        let interval_ctx = Arc::clone(&ctx);
        let poller = Poller::new(move || interval_ctx.refresh_seconds(), move || fetch(&ctx, &units));

        LocationsView { poller, table: ScrollableTable::new(), expanded: HashSet::new() }
    }

    fn toggle_expanded(&mut self) {
        let state = self.poller.state();
        let Some(data) = state.data.as_ref() else { return; };

        if let Some(row) = self.table.state.selected().and_then(|i| data.rows.get(i)) {
            if !self.expanded.remove(&row.id) {
                self.expanded.insert(row.id.clone());
            }
        }
    }
}

fn fetch(ctx: &Context, units_cache: &Mutex<Option<UnitSystem>>) -> Result<LocationsData, String> {
    // The unit system follows the user who owns the key, like in the web interface.
    if lock(units_cache).is_none() {
        let units = match user::find_own_profile(&ctx.client) {
            Ok(profile) if profile.data.unit_system.as_deref() == Some("imperial") => UnitSystem::Imperial,
            _ => UnitSystem::Metric,
        };
        *lock(units_cache) = Some(units);
    }
    let units = lock(units_cache).unwrap_or(UnitSystem::Metric);

    let pairs = ctx.pairs()?;
    let scope_label = ctx.prefs().scope.label(&lock(&ctx.scope_tree));

    let mut rows = Vec::new();
    let mut skipped_tenants = 0;
    for pair in pairs {
        // The locations endpoint answers 401 for a tenant the server does not know, so both codes mean "gone".
        let response = match locations::find_all(&ctx.client, &pair.organization_id, &pair.tenant_id) {
            Ok(response) => response,
            Err(ApiError::NotFound(_)) | Err(ApiError::Unauthorized) => { skipped_tenants += 1; continue; }
            Err(e) => return Err(e.to_string()),
        };
        for l in response.data {
            rows.push(to_row(l, &pair.tenant_name));
        }
    }

    rows.sort_by(|a, b| a.tenant.cmp(&b.tenant).then_with(|| a.name.cmp(&b.name)));

    Ok(LocationsData { rows, units, skipped_tenants, scope_label })
}

fn to_row(l: LocationSummaryResponse, tenant: &str) -> LocationRow {
    let mut floors: Vec<(String, i64, i64, bool)> = l.floors.into_iter()
        .map(|f| (f.name.unwrap_or_default(), f.number.unwrap_or(0), f.tap_count.unwrap_or(0), f.has_floor_plan.unwrap_or(false)))
        .collect();
    floors.sort_by_key(|f| f.1);

    let mut taps: Vec<(String, String, Option<bool>)> = l.taps.into_iter()
        .map(|t| (t.name.unwrap_or_default(), t.floor_name.unwrap_or_default(), t.is_online))
        .collect();
    taps.sort_by(|a, b| a.0.cmp(&b.0));
    let taps_online = taps.iter().filter(|t| t.2 == Some(true)).count();

    let alerts = l.alerts.into_iter()
        .map(|a| (a.detection_type.unwrap_or_default(), a.details.unwrap_or_default(), a.is_active.unwrap_or(false)))
        .collect();

    LocationRow {
        id: l.id.unwrap_or_default(),
        name: l.name.unwrap_or_default(),
        tenant: tenant.to_string(),
        description: l.description.unwrap_or_default(),
        coordinates: match (l.latitude, l.longitude) {
            (Some(lat), Some(lon)) => Some((lat, lon)),
            _ => None,
        },
        timezone: l.timezone,
        tap_count: l.tap_count.unwrap_or(0),
        taps_online,
        taps,
        floors,
        alert_count: l.alert_count,
        alerts,
        environment: l.environment,
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Weather wording, matching the web interface.
// ---------------------------------------------------------------------------------------------------------------

fn describe_wind(speed: Option<f64>, gust: Option<f64>, units: UnitSystem) -> Option<String> {
    if speed.is_none() && gust.is_none() {
        return None;
    }

    // The descriptor follows whichever is higher. Gusts are what makes wind feel concerning.
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

fn describe_visibility(meters: f64, units: UnitSystem) -> String {
    if meters >= 16000.0 {
        return "full visibility".to_string();
    }

    let label = match meters {
        m if m < 200.0 => "dense fog",
        m if m < 1000.0 => "fog",
        m if m < 4000.0 => "reduced visibility",
        m if m < 10000.0 => "hazy",
        _ => "mostly clear",
    };

    format!("{} ({} visibility)", label, units.distance_km(meters / 1000.0))
}

/// Condition, temperature, wind and visibility as styled spans for the table cell.
fn weather_spans(env: Option<&LocationEnvironmentDataResponse>, units: UnitSystem) -> Vec<Span<'static>> {
    let muted = Style::default().fg(palette::MUTED);
    let Some(env) = env else {
        return vec![Span::styled("n/a", muted)];
    };

    let text = Style::default().fg(palette::TEXT);
    let mut spans = Vec::new();

    match &env.condition {
        Some(c) => {
            let color = match c.severity {
                Some(s) if s >= 5 => Color::Red,
                Some(s) if s >= 1 => Color::Yellow,
                Some(_) => Color::Green,
                None => palette::MUTED,
            };
            spans.push(Span::styled("● ", Style::default().fg(color)));
            spans.push(Span::styled(c.display_name.clone().unwrap_or_default(), text));
        }
        None => {
            spans.push(Span::styled("● ", muted));
            spans.push(Span::styled("no condition data", muted));
        }
    }

    spans.push(Span::styled(", ", muted));
    match env.temperature {
        Some(t) if t != 0.0 => spans.push(Span::styled(units.temperature(t), text)),
        _ => spans.push(Span::styled("no temperature data", muted)),
    }

    spans.push(Span::styled(", ", muted));
    match describe_wind(env.wind_speed, env.wind_gust, units) {
        Some(w) => spans.push(Span::styled(w, text)),
        None => spans.push(Span::styled("no wind data", muted)),
    }

    spans.push(Span::styled(", ", muted));
    match env.visibility {
        Some(v) => spans.push(Span::styled(describe_visibility(v, units), text)),
        None => spans.push(Span::styled("no visibility data", muted)),
    }

    spans
}

fn severity_color(severity: Option<&str>) -> Color {
    match severity {
        Some("Extreme") | Some("Severe") => Color::Red,
        Some("Moderate") => Color::Yellow,
        Some("Minor") => Color::Cyan,
        _ => palette::MUTED,
    }
}

// ---------------------------------------------------------------------------------------------------------------
// View
// ---------------------------------------------------------------------------------------------------------------

impl View for LocationsView {
    fn on_enter(&mut self) {
        self.poller.start();
    }

    fn on_leave(&mut self) {
        self.poller.pause();
    }

    fn draw(&mut self, f: &mut Frame, area: Rect) {
        let state = self.poller.state();
        let data = state.data.as_ref();

        let title = match data {
            Some(d) => format!(" Locations in {}: {}{} ", d.scope_label, d.rows.len(), skipped_label(d.skipped_tenants)),
            None => " Locations ".to_string(),
        };
        let block = titled_block(title);
        let n = data.map(|d| d.rows.len()).unwrap_or(0);
        self.table.fit(area);
        self.table.clamp(n);

        let Some(data) = data.filter(|d| !d.rows.is_empty()) else {
            render_message(f, area, block, state.empty_text("No locations configured in this scope."));
            return;
        };

        let now = now_unix();
        let header = Row::new(["Name", "Tenant", "Taps", "Detection Alerts", "Environmental Alerts", "Current Weather Observation"]
            .iter().map(|c| header_cell(c)));

        let rows = data.rows.iter().map(|r| location_row(r, self.expanded.contains(&r.id), data.units, now));

        let widths = [
            Constraint::Min(22),
            Constraint::Min(14),
            Constraint::Length(12),
            Constraint::Length(16),
            Constraint::Length(20),
            Constraint::Percentage(50),
        ];

        let widget = Table::new(rows, widths)
            .header(header)
            .block(block)
            .column_spacing(1)
            .row_highlight_style(row_highlight_style())
            .highlight_symbol(HIGHLIGHT_SYMBOL);

        f.render_stateful_widget(widget, area, &mut self.table.state);
        self.table.render_scrollbar(f, area, n);
    }

    fn handle_key(&mut self, key: KeyEvent) -> ViewAction {
        let n = self.poller.state().data.as_ref().map(|d| d.rows.len()).unwrap_or(0);

        match key.code {
            KeyCode::Enter | KeyCode::Char('e') => { self.toggle_expanded(); return ViewAction::Continue; }
            KeyCode::Char('c') => { self.expanded.clear(); return ViewAction::Continue; }
            _ => {}
        }

        if self.table.handle_key(key.code, n) {
            return ViewAction::Continue;
        }

        match key.code {
            KeyCode::Char('q') | KeyCode::Esc => ViewAction::Back,
            KeyCode::Char('r') => { self.poller.refresh_now(); ViewAction::Continue }
            _ => ViewAction::Continue,
        }
    }

    fn footer_keys(&self) -> Vec<(&'static str, &'static str)> {
        let mut keys = vec![("Enter", "expand"), ("c", "collapse all")];
        keys.extend(ScrollableTable::KEYS.iter().copied());
        keys.extend([("r", "refresh now"), ("q/Esc", "menu")]);
        keys
    }

    fn header_status(&self) -> Vec<Span<'static>> {
        self.poller.status_spans()
    }
}

fn location_row(l: &LocationRow, expanded: bool, units: UnitSystem, now: i64) -> Row<'static> {
    let muted = Style::default().fg(palette::MUTED);
    let faint = Style::default().fg(palette::FAINT);
    let text = Style::default().fg(palette::TEXT);
    let key = Style::default().fg(palette::ORANGE);
    let section = Style::default().fg(palette::RED).add_modifier(Modifier::BOLD);

    // Taps: online / total.
    let taps_cell = if l.tap_count == 0 {
        Span::styled("none", muted)
    } else {
        let all_online = l.taps_online as i64 == l.tap_count;
        Span::styled(
            format!("{} / {} online", l.taps_online, l.tap_count),
            Style::default().fg(if all_online { Color::Green } else { Color::Yellow }),
        )
    };

    let alerts_cell = match l.alert_count {
        None => Span::styled("n/a", muted),
        Some(0) => Span::styled("none", muted),
        Some(n) => Span::styled(format!("▲ {}", n), Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
    };

    // Count only; the expanded row lists the individual environmental alerts.
    let env_alerts_cell = match l.environment.as_ref().map(|e| &e.alerts) {
        None => Span::styled("n/a", muted),
        Some(alerts) if alerts.is_empty() => Span::styled("none", muted),
        Some(alerts) => Span::styled(format!("▲ {}", alerts.len()), Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
    };

    // The name cell carries the expanded details below the name.
    let mut name_lines = vec![Line::from(Span::styled(
        format!("{} {}", if expanded { "▼" } else { "▶" }, l.name),
        Style::default().fg(palette::TEXT).add_modifier(Modifier::BOLD),
    ))];
    // Weather cell lines; the expanded details continue in this wider column.
    let mut weather_lines = vec![Line::from(weather_spans(l.environment.as_ref(), units))];

    if expanded {
        let mut details: Vec<Line> = Vec::new();

        if !l.description.trim().is_empty() {
            details.push(Line::from(vec![Span::styled("Description  ", key), Span::styled(l.description.clone(), text)]));
        }
        match l.coordinates {
            Some((lat, lon)) => details.push(Line::from(vec![
                Span::styled("Coordinates  ", key), Span::styled(format!("{:.5}, {:.5}", lat, lon), text),
            ])),
            None => details.push(Line::from(vec![Span::styled("Coordinates  ", key), Span::styled("not set", muted)])),
        }
        if let Some(tz) = &l.timezone {
            details.push(Line::from(vec![Span::styled("Timezone     ", key), Span::styled(tz.clone(), text)]));
        }
        if let Some(env) = &l.environment {
            if let Some(station) = &env.station_id {
                details.push(Line::from(vec![Span::styled("Station      ", key), Span::styled(station.clone(), text)]));
            }
            if let Some(metar) = &env.metar {
                details.push(Line::from(vec![Span::styled("METAR        ", key), Span::styled(metar.clone(), faint)]));
            }
        }

        details.push(Line::raw(""));
        details.push(Line::from(Span::styled(format!("FLOORS ({})", l.floors.len()), section)));
        if l.floors.is_empty() {
            details.push(Line::from(Span::styled("  no floors configured", muted)));
        }
        for (name, number, tap_count, has_plan) in &l.floors {
            details.push(Line::from(vec![
                Span::styled(format!("  {:>3}  ", number), faint),
                Span::styled(name.clone(), text),
                Span::styled(format!("  {} taps", tap_count), muted),
                Span::styled(if *has_plan { "  floor plan" } else { "  no floor plan" }, faint),
            ]));
        }

        details.push(Line::raw(""));
        details.push(Line::from(Span::styled(format!("TAPS ({})", l.taps.len()), section)));
        if l.taps.is_empty() {
            details.push(Line::from(Span::styled("  no taps present", muted)));
        }
        for (name, floor, online) in &l.taps {
            let status = match online {
                Some(true) => Span::styled("online ", Style::default().fg(Color::Green)),
                Some(false) => Span::styled("offline", Style::default().fg(Color::Red)),
                None => Span::styled("       ", muted),
            };
            details.push(Line::from(vec![
                Span::raw("  "), status, Span::raw("  "),
                Span::styled(name.clone(), text),
                Span::styled(if floor.is_empty() { String::new() } else { format!("  ({})", floor) }, muted),
            ]));
        }

        if let Some(env) = &l.environment {
            details.push(Line::raw(""));
            details.push(Line::from(Span::styled(format!("ENVIRONMENTAL ALERTS ({})", env.alerts.len()), section)));
            if env.alerts.is_empty() {
                details.push(Line::from(Span::styled("  no active environmental alerts", muted)));
            }
            for a in &env.alerts {
                let expires = a.expires.as_deref().and_then(parse_iso8601_to_unix)
                    .map(|t| if t > now { format!("expires in {}", format_age_seconds(t - now)) } else { "expired".to_string() })
                    .unwrap_or_default();
                details.push(Line::from(vec![
                    Span::styled(format!("  {:<9}", a.severity.clone().unwrap_or_else(|| "Unknown".to_string())),
                                 Style::default().fg(severity_color(a.severity.as_deref())).add_modifier(Modifier::BOLD)),
                    Span::styled(a.event.clone().unwrap_or_else(|| "Unknown event".to_string()), text),
                    Span::styled(format!("  {}", expires), faint),
                ]));
                if let Some(headline) = &a.headline {
                    details.push(Line::from(Span::styled(format!("           {}", headline), muted)));
                }
            }
        }

        if l.alert_count.is_some() {
            details.push(Line::raw(""));
            details.push(Line::from(Span::styled(format!("DETECTION ALERTS ({})", l.alerts.len()), section)));
            if l.alerts.is_empty() {
                details.push(Line::from(Span::styled("  no active detection alerts at this location", muted)));
            }
            for (detection_type, alert_details, active) in &l.alerts {
                details.push(Line::from(vec![
                    Span::styled(if *active { "  ▲ " } else { "  · " }, Style::default().fg(if *active { Color::Red } else { palette::MUTED })),
                    Span::styled(detection_type.clone(), text),
                    Span::styled(format!("  {}", alert_details), muted),
                ]));
            }
        }

        details.push(Line::raw(""));
        weather_lines.extend(details);
    }

    let height = weather_lines.len().max(name_lines.len()) as u16;
    name_lines.resize(1, Line::raw(""));

    Row::new(vec![
        Cell::from(Text::from(name_lines)),
        Cell::from(Span::styled(l.tenant.clone(), muted)),
        Cell::from(taps_cell),
        Cell::from(alerts_cell),
        Cell::from(env_alerts_cell),
        Cell::from(Text::from(weather_lines)),
    ]).height(height)
}
