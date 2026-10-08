//! Alerts overview: a status strip with the active alert count on top and a single alert list below, across the
//! selected organization and tenant scope. Every row shows whether the alert is active. Enter expands a row to show the
//! alert's attributes.

use std::collections::{HashMap, HashSet};
use std::sync::Arc;

use ratatui::crossterm::event::{KeyCode, KeyEvent};
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span, Text};
use ratatui::widgets::{Cell, Row, Table};
use ratatui::Frame;

use crate::api::endpoints::alerts;
use crate::api::error::ApiError;
use crate::apps::api::gui::common::{
    header_cell, lock, palette, render_message, row_highlight_style, skipped_label, titled_block, Poller,
    ScrollableTable, HIGHLIGHT_SYMBOL,
};
use crate::apps::api::gui::prefs::Context;
use crate::apps::api::gui::view::{View, ViewAction};
use crate::tools::{format_age_seconds, now_unix, parse_iso8601_to_unix};

const MAX_ALERTS_PER_TENANT: usize = 2000;

struct AlertRow {
    id: String,
    active: bool,
    subsystem: String,
    detection_type: String,
    details: String,
    scope: String,
    tap: String,
    created_unix: Option<i64>,
    last_seen_unix: Option<i64>,
    attributes: Vec<(String, String)>,
}

struct AlertsData {
    /// All alerts, active first, then by last activity.
    rows: Vec<AlertRow>,
    /// Indexes into `rows` of the active alerts.
    active: Vec<usize>,
    total: i64,
    /// Active alerts per subsystem, for the overview title.
    active_by_subsystem: Vec<(String, usize)>,
    skipped_tenants: usize,
    scope_label: String,
}

pub struct AlertsView {
    poller: Poller<AlertsData>,
    table: ScrollableTable,
    expanded: HashSet<String>,
}

impl AlertsView {
    pub fn new(ctx: Arc<Context>) -> Self {
        let interval_ctx = Arc::clone(&ctx);
        let poller = Poller::new(move || interval_ctx.refresh_seconds(), move || fetch(&ctx));

        AlertsView { poller, table: ScrollableTable::new(), expanded: HashSet::new() }
    }

    /// Toggles the expansion of the selected alert.
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

fn fetch(ctx: &Context) -> Result<AlertsData, String> {
    let pairs = ctx.pairs()?;
    let scope_label = ctx.prefs().scope.label(&lock(&ctx.scope_tree));

    let mut rows = Vec::new();
    let mut total = 0;
    let mut skipped_tenants = 0;

    for pair in pairs {
        let page = match alerts::find_all(&ctx.client, &pair.organization_id, &pair.tenant_id, MAX_ALERTS_PER_TENANT) {
            Ok(page) => page,
            // The tenant no longer exists, usually deleted since the last discovery.
            Err(ApiError::NotFound(_)) => { skipped_tenants += 1; continue; }
            Err(e) => return Err(e.to_string()),
        };
        total += page.total;

        for a in page.alerts {
            let attributes = a.attributes.iter()
                .map(|(k, v)| (k.clone(), match v {
                    serde_json::Value::String(s) => s.clone(),
                    serde_json::Value::Null => String::new(),
                    other => other.to_string(),
                }))
                .collect();

            rows.push(AlertRow {
                id: a.id.unwrap_or_default(),
                active: a.is_active.unwrap_or(false),
                subsystem: a.subsystem.unwrap_or_default(),
                detection_type: a.detection_type.unwrap_or_default(),
                details: a.details.unwrap_or_default(),
                scope: pair.tenant_name.clone(),
                tap: a.tap_id.as_ref().map(|id| ctx.tap_name(id).unwrap_or_else(|| id.clone())).unwrap_or_default(),
                created_unix: a.created_at.as_deref().and_then(parse_iso8601_to_unix),
                last_seen_unix: a.last_seen.as_deref().and_then(parse_iso8601_to_unix),
                attributes,
            });
        }
    }

    // Active first, then most recent activity first.
    rows.sort_by(|a, b| b.active.cmp(&a.active).then_with(|| b.last_seen_unix.cmp(&a.last_seen_unix)));

    let active: Vec<usize> = rows.iter().enumerate().filter(|(_, r)| r.active).map(|(i, _)| i).collect();

    let mut by_subsystem: HashMap<String, usize> = HashMap::new();
    for &i in &active {
        *by_subsystem.entry(rows[i].subsystem.clone()).or_default() += 1;
    }
    let mut active_by_subsystem: Vec<(String, usize)> = by_subsystem.into_iter().collect();
    active_by_subsystem.sort_by(|a, b| b.1.cmp(&a.1).then_with(|| a.0.cmp(&b.0)));

    Ok(AlertsData { rows, active, total, active_by_subsystem, skipped_tenants, scope_label })
}

impl View for AlertsView {
    fn on_enter(&mut self) {
        self.poller.start();
    }

    fn on_leave(&mut self) {
        self.poller.pause();
    }

    fn draw(&mut self, f: &mut Frame, area: Rect) {
        let state = self.poller.state();
        let data = state.data.as_ref();

        let chunks = Layout::vertical([Constraint::Length(STATUS_HEIGHT), Constraint::Min(5)]).split(area);
        let now = now_unix();

        render_status_strip(f, chunks[0], data, &state.empty_text(""), now);

        let title = match data {
            Some(d) => format!(
                " Alerts in {}: {} shown{}{} ",
                d.scope_label,
                d.rows.len(),
                if d.total as usize > d.rows.len() { format!(" of {}", d.total) } else { String::new() },
                skipped_label(d.skipped_tenants),
            ),
            None => " Alerts ".to_string(),
        };
        let block = titled_block(title);
        let n = data.map(|d| d.rows.len()).unwrap_or(0);
        self.table.fit(chunks[1]);
        self.table.clamp(n);

        match data {
            Some(d) if !d.rows.is_empty() => {
                let rows = d.rows.iter().map(|r| alert_row(r, self.expanded.contains(&r.id), now));
                render_alert_table(f, chunks[1], block, rows, &mut self.table, n);
            }
            _ => render_message(f, chunks[1], block, state.empty_text("No alerts recorded in this scope.")),
        }

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

const STATUS_HEIGHT: u16 = 7;

/// Three-line block digits for the active alert counter.
const BIG_DIGITS: [[&str; 3]; 10] = [
    ["█▀█", "█ █", "▀▀▀"],
    ["▀█ ", " █ ", "▀▀▀"],
    ["▀▀█", "█▀▀", "▀▀▀"],
    ["▀▀█", " ▀█", "▀▀▀"],
    ["█ █", "▀▀█", "  ▀"],
    ["█▀▀", "▀▀█", "▀▀▀"],
    ["█▀▀", "█▀█", "▀▀▀"],
    ["▀▀█", "  █", "  ▀"],
    ["█▀█", "█▀█", "▀▀▀"],
    ["█▀█", "▀▀█", "▀▀▀"],
];

fn big_number(n: usize) -> [String; 3] {
    let digits: Vec<usize> = n.to_string().bytes().map(|b| (b - b'0') as usize).collect();
    let mut rows = [String::new(), String::new(), String::new()];
    for (i, d) in digits.iter().enumerate() {
        for (r, row) in rows.iter_mut().enumerate() {
            if i > 0 { row.push(' '); }
            row.push_str(BIG_DIGITS[*d][r]);
        }
    }
    rows
}

/// The status strip: big active count on the left, key figures on the right.
fn render_status_strip(f: &mut Frame, area: Rect, data: Option<&AlertsData>, empty_text: &str, now: i64) {
    let block = titled_block(" Alert status ".to_string());

    let Some(data) = data else {
        render_message(f, area, block, empty_text);
        return;
    };

    let inner = block.inner(area);
    f.render_widget(block, area);

    let active = data.active.len();
    let attention = active > 0;
    let accent = if attention { Color::Red } else { Color::Green };

    let digits = big_number(active);
    let digits_width = digits[0].chars().count() as u16;
    let columns = Layout::horizontal([
        Constraint::Length(digits_width + 4),
        Constraint::Length(18),
        Constraint::Min(20),
    ]).split(inner.inner(ratatui::layout::Margin { vertical: 0, horizontal: 1 }));

    // Big counter.
    let mut counter: Vec<Line> = vec![Line::raw("")];
    counter.extend(digits.iter()
        .map(|row| Line::from(Span::styled(format!(" {} ", row), Style::default().fg(accent).add_modifier(Modifier::BOLD)))));
    f.render_widget(ratatui::widgets::Paragraph::new(counter), columns[0]);

    // State word next to the counter, blinking while alerts are active.
    let state_word = if attention {
        if crate::apps::api::gui::common::blink_on() { "▲ ATTENTION" } else { "  ATTENTION" }
    } else {
        "● ALL QUIET"
    };
    let state_lines = vec![
        Line::raw(""),
        Line::raw(""),
        Line::from(Span::styled(state_word, Style::default().fg(accent).add_modifier(Modifier::BOLD))),
        Line::from(Span::styled("ACTIVE ALERTS", Style::default().fg(palette::MUTED))),
    ];
    f.render_widget(ratatui::widgets::Paragraph::new(state_lines), columns[1]);

    // Key figures.
    let label = Style::default().fg(palette::MUTED);
    let value = Style::default().fg(palette::TEXT);

    let by_subsystem = if data.active_by_subsystem.is_empty() {
        "none".to_string()
    } else {
        data.active_by_subsystem.iter().map(|(s, n)| format!("{} {}", s, n)).collect::<Vec<_>>().join("  ·  ")
    };
    let latest = data.active.first()
        .and_then(|&i| data.rows[i].last_seen_unix)
        .map(|t| format!("{} ago", format_age_seconds(now - t)))
        .unwrap_or_else(|| "none".to_string());

    let figures = vec![
        Line::raw(""),
        Line::from(vec![Span::styled("ACTIVE BY SUBSYSTEM  ", label), Span::styled(by_subsystem, value)]),
        Line::from(vec![Span::styled("LATEST ACTIVITY      ", label), Span::styled(latest, value)]),
        Line::from(vec![Span::styled("TOTAL ALERTS         ", label), Span::styled(data.total.to_string(), value)]),
    ];
    f.render_widget(ratatui::widgets::Paragraph::new(figures), columns[2]);
}

const COLUMNS: &[&str] = &["Status", "Subsystem", "Type", "Details", "Tenant", "Tap", "First Seen", "Last Seen"];

fn alert_row(a: &AlertRow, expanded: bool, now: i64) -> Row<'static> {
    let status = if a.active {
        Span::styled("▲ ACTIVE", Style::default().fg(Color::Red).add_modifier(Modifier::BOLD))
    } else {
        Span::styled("· INACTIVE", Style::default().fg(palette::MUTED))
    };

    let age = |t: Option<i64>| t.map(|t| format!("{} ago", format_age_seconds(now - t))).unwrap_or_default();

    let mut details_lines = vec![Line::from(Span::styled(
        format!("{} {}", if expanded { "▼" } else { "▶" }, a.details),
        Style::default().fg(palette::TEXT),
    ))];

    if expanded {
        let key_style = Style::default().fg(palette::ORANGE);
        let value_style = Style::default().fg(palette::TEXT);
        let faint = Style::default().fg(palette::FAINT);

        if a.attributes.is_empty() {
            details_lines.push(Line::from(Span::styled("    no attributes", faint)));
        }
        for (k, v) in &a.attributes {
            details_lines.push(Line::from(vec![
                Span::styled(format!("    {}: ", k), key_style),
                Span::styled(v.clone(), value_style),
            ]));
        }
        details_lines.push(Line::from(vec![
            Span::styled("    alert id: ".to_string(), faint),
            Span::styled(a.id.clone(), faint),
        ]));
    }

    let height = details_lines.len() as u16;

    Row::new(vec![
        Cell::from(status),
        Cell::from(a.subsystem.clone()),
        Cell::from(Span::styled(a.detection_type.clone(), Style::default().add_modifier(Modifier::BOLD))),
        Cell::from(Text::from(details_lines)),
        Cell::from(Span::styled(a.scope.clone(), Style::default().fg(palette::MUTED))),
        Cell::from(a.tap.clone()),
        Cell::from(Span::styled(age(a.created_unix), Style::default().fg(palette::MUTED))),
        Cell::from(Span::styled(
            age(a.last_seen_unix),
            Style::default().fg(if a.active { Color::Green } else { palette::MUTED }),
        )),
    ]).height(height)
}

fn render_alert_table<'a>(f: &mut Frame, area: Rect, block: ratatui::widgets::Block<'static>,
                          rows: impl Iterator<Item = Row<'a>>, table: &mut ScrollableTable, n: usize) {
    let header = Row::new(COLUMNS.iter().map(|c| header_cell(c)));

    let widths = [
        Constraint::Length(10),
        Constraint::Length(10),
        Constraint::Length(34),
        Constraint::Percentage(40),
        Constraint::Min(16),
        Constraint::Length(18),
        Constraint::Length(11),
        Constraint::Length(11),
    ];

    let widget = Table::new(rows, widths)
        .header(header)
        .block(block)
        .column_spacing(1)
        .row_highlight_style(row_highlight_style())
        .highlight_symbol(HIGHLIGHT_SYMBOL);

    f.render_stateful_widget(widget, area, &mut table.state);
    table.render_scrollbar(f, area, n);
}
