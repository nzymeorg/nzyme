//! WiFi: live BSSID table with BSSID and SSID count charts over the previous 24 hours, for the taps selected in
//! the shell. The table time range is the one view-local option.

use std::sync::{Arc, Mutex};

use ratatui::crossterm::event::{KeyCode, KeyEvent};
use ratatui::layout::{Constraint, Layout, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::symbols::Marker;
use ratatui::text::{Line, Span};
use ratatui::widgets::{Axis, Cell, Chart, Dataset, GraphType, Row, Table};
use ratatui::Frame;

use crate::api::endpoints::dot11;
use crate::api::types::BssidSummaryDetailsResponse;
use crate::apps::api::gui::common::{
    cycle, lock, palette, render_message, render_options_popup, row_highlight_style, header_cell, titled_block,
    HIGHLIGHT_SYMBOL, OptionRow, Poller, ScrollableTable, OPTIONS_KEYS,
};
use crate::apps::api::gui::prefs::Context;
use crate::apps::api::gui::view::{View, ViewAction};
use crate::tools::{format_age_seconds, now_unix, parse_iso8601_to_unix};

const CHART_MINUTES: u32 = 1440;
const MAX_BSSIDS: usize = 5000;

/// Largest gap between two histogram buckets that is still drawn as a continuous line.
const MAX_GAP_SECONDS: i64 = 5 * 60;

const RANGE_PRESETS: &[u32] = &[1, 5, 15, 30, 60, 180, 720, 1440];
const DEFAULT_TABLE_MINUTES: u32 = 15;

// ---------------------------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------------------------

/// View-local settings. Taps and refresh interval come from the shared preferences.
struct Settings {
    table_minutes: u32,
}

// ---------------------------------------------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------------------------------------------

struct BssidRow {
    bssid: String,
    vendor: String,
    ssids: String,
    security: String,
    mode: String,
    signal: String,
    clients: String,
    last_seen_unix: Option<i64>,
}

struct HistogramPoint {
    unix: i64,
    bssids: f64,
    ssids: f64,
}

struct Data {
    rows: Vec<BssidRow>,
    total: i64,
    histogram: Vec<HistogramPoint>,
    histogram_max: f64,
    /// The table range and tap selection this data was fetched with, so the title never disagrees with the rows.
    table_minutes: u32,
    taps_label: String,
}

fn fetch(ctx: &Context, settings: &Mutex<Settings>) -> Result<Data, String> {
    let table_minutes = lock(settings).table_minutes;
    let prefs = ctx.prefs();
    let taps_param = prefs.taps.query_value();
    let taps_label = prefs.taps.label(&lock(&ctx.taps));

    let (bssids, total) = dot11::find_bssids(&ctx.client, &dot11::relative_time_range(table_minutes), &taps_param, MAX_BSSIDS)
        .map_err(|e| e.to_string())?;
    let histogram = dot11::find_bssid_histogram(&ctx.client, &dot11::relative_time_range(CHART_MINUTES), &taps_param)
        .map_err(|e| e.to_string())?;

    // Buckets without data are missing from the response, so the points carry their real timestamp and the chart
    // plots them on a time axis instead of by index.
    let mut points: Vec<HistogramPoint> = histogram.data.values.values()
        .filter_map(|v| {
            let unix = v.timestamp.as_deref().and_then(parse_iso8601_to_unix)?;
            Some(HistogramPoint { unix, bssids: v.bssid_count.unwrap_or(0) as f64, ssids: v.ssid_count.unwrap_or(0) as f64 })
        })
        .collect();
    points.sort_by_key(|p| p.unix);
    let histogram_max = points.iter().map(|p| p.bssids.max(p.ssids)).fold(0.0, f64::max);

    Ok(Data {
        rows: bssids.iter().map(to_row).collect(),
        total,
        histogram: points,
        histogram_max,
        table_minutes,
        taps_label,
    })
}

fn to_row(b: &BssidSummaryDetailsResponse) -> BssidRow {
    let mac = b.bssid.as_ref();

    let bssid = mac.and_then(|m| m.address.clone()).unwrap_or_else(|| "<unknown>".to_string());

    let vendor = mac.and_then(|m| m.context.as_ref().and_then(|c| c.name.clone()))
        .or_else(|| mac.and_then(|m| m.oui.clone()))
        .or_else(|| if mac.and_then(|m| m.is_randomized) == Some(true) { Some("(randomized)".to_string()) } else { None })
        .unwrap_or_default();

    let mut ssids: Vec<&str> = b.advertised_ssid_names.iter().flatten().map(|s| s.as_str()).filter(|s| !s.is_empty()).collect();
    ssids.sort_unstable();
    let mut ssids = ssids.join(", ");
    if b.has_hidden_ssid_advertisements == Some(true) {
        if !ssids.is_empty() { ssids.push_str(", "); }
        ssids.push_str("[hidden]");
    }

    let mut security: Vec<&str> = b.security_protocols.iter().flatten().map(|s| s.as_str()).collect();
    security.sort_unstable();
    let security = if security.is_empty() { "None".to_string() } else { security.join("/") };

    let mut mode: Vec<&str> = b.infrastructure_types.iter().flatten().map(|s| s.as_str()).collect();
    mode.sort_unstable();

    BssidRow {
        bssid,
        vendor,
        ssids,
        security,
        mode: mode.join("/"),
        signal: b.signal_strength_average.map(|s| format!("{:.0} dBm", s)).unwrap_or_default(),
        clients: b.client_count.map(|c| c.to_string()).unwrap_or_default(),
        last_seen_unix: b.last_seen.as_deref().and_then(parse_iso8601_to_unix),
    }
}

/// `HH:MM` in UTC of a Unix timestamp.
fn short_time(unix: i64) -> String {
    let seconds_of_day = unix.rem_euclid(86400);
    format!("{:02}:{:02}", seconds_of_day / 3600, (seconds_of_day % 3600) / 60)
}

fn format_minutes(minutes: u32) -> String {
    match minutes {
        m if m % 1440 == 0 => format!("{} d", m / 1440),
        m if m % 60 == 0 => format!("{} h", m / 60),
        m => format!("{} min", m),
    }
}

// ---------------------------------------------------------------------------------------------------------------
// View
// ---------------------------------------------------------------------------------------------------------------

pub struct BssidsView {
    poller: Poller<Data>,
    settings: Arc<Mutex<Settings>>,
    table: ScrollableTable,
    options_open: bool,
}

impl BssidsView {
    pub fn new(ctx: Arc<Context>) -> Self {
        let settings = Arc::new(Mutex::new(Settings { table_minutes: DEFAULT_TABLE_MINUTES }));

        let poller = {
            let settings = Arc::clone(&settings);
            let interval_ctx = Arc::clone(&ctx);
            Poller::new(move || interval_ctx.refresh_seconds(), move || fetch(&ctx, &settings))
        };

        BssidsView { poller, settings, table: ScrollableTable::new(), options_open: false }
    }

    fn adjust_range(&mut self, direction: i32) {
        {
            let mut settings = lock(&self.settings);
            settings.table_minutes = cycle(RANGE_PRESETS, settings.table_minutes, direction);
        }
        self.poller.refresh_now();
    }
}

impl View for BssidsView {
    fn on_enter(&mut self) {
        self.poller.start();
    }

    fn on_leave(&mut self) {
        self.poller.pause();
    }

    fn draw(&mut self, f: &mut Frame, area: Rect) {
        let chart_height = (area.height / 3).clamp(8, 16);
        let chunks = Layout::vertical([Constraint::Length(chart_height), Constraint::Min(5)]).split(area);

        {
            let state = self.poller.state();
            let data = state.data.as_ref();

            render_chart(f, chunks[0], data, state.empty_text("No data in the previous 24 hours."));

            let rows: &[BssidRow] = data.map(|d| d.rows.as_slice()).unwrap_or(&[]);
            self.table.clamp(rows.len());
            self.table.fit(chunks[1]);
            render_table(f, chunks[1], data, state.empty_text("No BSSIDs recorded in this time range."), &mut self.table);
        }

        if self.options_open {
            let rows = [OptionRow {
                label: "Table time range".to_string(),
                value: format!("previous {}", format_minutes(lock(&self.settings).table_minutes)),
                cyclable: true,
            }];
            render_options_popup(f, area, "Options", &rows, 0,
                "Changes apply immediately. The charts always cover the previous 24 hours. Taps and the refresh \
                interval are set in the dashboard settings.");
        }
    }

    fn handle_key(&mut self, key: KeyEvent) -> ViewAction {
        if self.options_open {
            match key.code {
                KeyCode::Esc | KeyCode::Char('q') | KeyCode::Char('m') => self.options_open = false,
                KeyCode::Left | KeyCode::Char('h') => self.adjust_range(-1),
                KeyCode::Right | KeyCode::Char('l') | KeyCode::Enter => self.adjust_range(1),
                _ => {}
            }
            return ViewAction::Continue;
        }

        let n = self.poller.state().data.as_ref().map(|d| d.rows.len()).unwrap_or(0);
        if self.table.handle_key(key.code, n) {
            return ViewAction::Continue;
        }

        match key.code {
            KeyCode::Char('q') | KeyCode::Esc => ViewAction::Back,
            KeyCode::Char('r') => { self.poller.refresh_now(); ViewAction::Continue }
            KeyCode::Char('m') | KeyCode::Enter => { self.options_open = true; ViewAction::Continue }
            _ => ViewAction::Continue,
        }
    }

    fn footer_keys(&self) -> Vec<(&'static str, &'static str)> {
        if self.options_open {
            return OPTIONS_KEYS.to_vec();
        }

        let mut keys = ScrollableTable::KEYS.to_vec();
        keys.extend([("m", "options"), ("r", "refresh now"), ("q/Esc", "menu")]);
        keys
    }

    fn header_status(&self) -> Vec<Span<'static>> {
        let mut spans = self.poller.status_spans();
        spans.push(Span::raw("   "));
        spans.push(Span::styled(
            format!("Table {}", format_minutes(lock(&self.settings).table_minutes)),
            Style::default().fg(palette::MUTED),
        ));
        spans
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------------------------------------------

fn render_chart(f: &mut Frame, area: Rect, data: Option<&Data>, empty_text: &str) {
    let block = titled_block(" BSSIDs and SSIDs, previous 24 hours ".to_string());

    let Some(data) = data.filter(|d| !d.histogram.is_empty()) else {
        render_message(f, area, block, empty_text);
        return;
    };

    // X axis: minutes since the start of the 24 hour window, which ends now.
    let end = now_unix();
    let start = end - (CHART_MINUTES as i64) * 60;
    let to_x = |unix: i64| ((unix - start) as f64 / 60.0).clamp(0.0, CHART_MINUTES as f64);

    // Missing buckets mean no data was recorded. Split each series into segments at such gaps so the line chart
    // does not draw a bridge across an outage.
    let bssid_segments = segments(&data.histogram, |p| p.bssids, to_x);
    let ssid_segments = segments(&data.histogram, |p| p.ssids, to_x);

    let x_max = CHART_MINUTES as f64;
    let y_max = (data.histogram_max * 1.15).ceil().max(1.0);
    let latest = data.histogram.last().map(|p| (p.bssids as i64, p.ssids as i64)).unwrap_or((0, 0));

    let mut datasets = Vec::with_capacity(bssid_segments.len() + ssid_segments.len());
    for (i, segment) in bssid_segments.iter().enumerate() {
        let mut dataset = Dataset::default().marker(Marker::Braille).graph_type(GraphType::Line)
            .style(Style::default().fg(palette::RED)).data(segment);
        if i == 0 {
            dataset = dataset.name(format!("BSSIDs ({})", latest.0));
        }
        datasets.push(dataset);
    }
    for (i, segment) in ssid_segments.iter().enumerate() {
        let mut dataset = Dataset::default().marker(Marker::Braille).graph_type(GraphType::Line)
            .style(Style::default().fg(palette::ORANGE)).data(segment);
        if i == 0 {
            dataset = dataset.name(format!("SSIDs ({})", latest.1));
        }
        datasets.push(dataset);
    }

    let x_labels: Vec<Line> = [0i64, 1, 2, 3, 4].iter()
        .map(|&q| Line::from(short_time(start + q * (end - start) / 4)))
        .collect();
    let y_labels: Vec<Line> = [0.0, y_max / 2.0, y_max].iter()
        .map(|v| Line::from(format!("{:.0}", v)))
        .collect();

    let chart = Chart::new(datasets)
        .block(block)
        .x_axis(Axis::default()
            .title(Span::styled("UTC", Style::default().fg(palette::MUTED)))
            .style(Style::default().fg(palette::FAINT))
            .bounds([0.0, x_max])
            .labels(x_labels))
        .y_axis(Axis::default()
            .style(Style::default().fg(palette::FAINT))
            .bounds([0.0, y_max])
            .labels(y_labels));

    f.render_widget(chart, area);
}

/// Splits the histogram into runs of consecutive buckets, mapped to chart coordinates.
fn segments(points: &[HistogramPoint], value: impl Fn(&HistogramPoint) -> f64, to_x: impl Fn(i64) -> f64)
    -> Vec<Vec<(f64, f64)>> {
    let mut result: Vec<Vec<(f64, f64)>> = Vec::new();
    let mut previous: Option<i64> = None;

    for p in points {
        if previous.map_or(true, |prev| p.unix - prev > MAX_GAP_SECONDS) {
            result.push(Vec::new());
        }
        result.last_mut().unwrap().push((to_x(p.unix), value(p)));
        previous = Some(p.unix);
    }

    result
}

fn render_table(f: &mut Frame, area: Rect, data: Option<&Data>, empty_text: &str, table: &mut ScrollableTable) {
    let title = match data {
        Some(d) => format!(
            " BSSIDs, previous {}, {}: {} shown{} ",
            format_minutes(d.table_minutes),
            d.taps_label.to_lowercase(),
            d.rows.len(),
            if d.total as usize > d.rows.len() { format!(" of {}", d.total) } else { String::new() },
        ),
        None => " BSSIDs ".to_string(),
    };
    let block = titled_block(title);

    let Some(data) = data.filter(|d| !d.rows.is_empty()) else {
        render_message(f, area, block, empty_text);
        return;
    };

    let now = now_unix();

    let header = Row::new(["BSSID", "Vendor / Context", "SSIDs", "Security", "Mode", "Signal", "Clients", "Last Seen"]
        .into_iter()
        .map(header_cell));

    let rows = data.rows.iter().map(|r| {
        let age = r.last_seen_unix.map(|t| now - t);
        let last_seen = age.map(format_age_seconds).unwrap_or_default();
        let last_seen_style = match age {
            Some(a) if a <= 60 => Style::default().fg(Color::Green),
            Some(a) if a <= 300 => Style::default().fg(Color::Yellow),
            _ => Style::default().fg(Color::Gray),
        };

        let security_style = if r.security.split('/').any(|p| p.eq_ignore_ascii_case("none")) {
            Style::default().fg(Color::Red)
        } else if r.security.contains("WEP") || r.security.contains("WPA1") {
            Style::default().fg(Color::Yellow)
        } else {
            Style::default()
        };

        Row::new(vec![
            Cell::from(Span::styled(r.bssid.clone(), Style::default().add_modifier(Modifier::BOLD))),
            Cell::from(r.vendor.clone()),
            Cell::from(r.ssids.clone()),
            Cell::from(Span::styled(r.security.clone(), security_style)),
            Cell::from(r.mode.clone()),
            Cell::from(r.signal.clone()),
            Cell::from(r.clients.clone()),
            Cell::from(Span::styled(last_seen, last_seen_style)),
        ])
    });

    let widths = [
        Constraint::Length(17),
        Constraint::Min(14),
        Constraint::Percentage(30),
        Constraint::Length(15),
        Constraint::Length(15),
        Constraint::Length(8),
        Constraint::Length(7),
        Constraint::Length(9),
    ];

    let widget = Table::new(rows, widths)
        .header(header)
        .block(block)
        .column_spacing(1)
        .row_highlight_style(row_highlight_style())
        .highlight_symbol(HIGHLIGHT_SYMBOL);

    f.render_stateful_widget(widget, area, &mut table.state);
    table.render_scrollbar(f, area, data.rows.len());
}
