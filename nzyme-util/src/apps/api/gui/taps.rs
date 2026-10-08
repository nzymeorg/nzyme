//! System: taps table across the selected organization and tenant scope. Full details need organization
//! administrator permissions; for other users the view falls back to the high-level tap status.

use std::sync::Arc;

use ratatui::crossterm::event::{KeyCode, KeyEvent};
use ratatui::layout::{Constraint, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::Span;
use ratatui::widgets::{Cell, Row, Table};
use ratatui::Frame;

use crate::api::endpoints::taps;
use crate::api::error::ApiError;
use crate::apps::api::gui::common::{
    header_cell, palette, render_message, row_highlight_style, skipped_label, titled_block, Poller, ScrollableTable,
    HIGHLIGHT_SYMBOL,
};
use crate::apps::api::gui::prefs::Context;
use crate::apps::api::gui::view::{View, ViewAction};
use crate::tools::{format_age_seconds, format_bytes, now_unix, parse_iso8601_to_unix};

struct TapRow {
    name: String,
    tenant: String,
    online: Option<bool>,
    version: String,
    last_report_unix: Option<i64>,
    remote: String,
    location: String,
    clock_drift: String,
    cpu: String,
    memory: String,
    throughput: String,
}

struct TapsData {
    rows: Vec<TapRow>,
    /// Tenants for which only high-level status was available.
    limited_scopes: usize,
    /// Tenants the server no longer knows, usually deleted since the last discovery.
    skipped_tenants: usize,
    scope_label: String,
}

pub struct TapsView {
    poller: Poller<TapsData>,
    table: ScrollableTable,
}

impl TapsView {
    pub fn new(ctx: Arc<Context>) -> Self {
        let interval_ctx = Arc::clone(&ctx);
        let poller = Poller::new(move || interval_ctx.refresh_seconds(), move || fetch(&ctx));

        TapsView { poller, table: ScrollableTable::new() }
    }
}

fn fetch(ctx: &Context) -> Result<TapsData, String> {
    let pairs = ctx.pairs()?;
    let scope_label = ctx.prefs().scope.label(&ctx.scope_tree.lock().unwrap_or_else(|e| e.into_inner()));

    let mut rows = Vec::new();
    let mut limited_scopes = 0;
    let mut skipped_tenants = 0;

    for pair in pairs {
        match taps::find_all(&ctx.client, &pair.organization_id, &pair.tenant_id) {
            Ok(response) => {
                for t in response.data.taps {
                    rows.push(TapRow {
                        name: t.name.unwrap_or_default(),
                        tenant: pair.tenant_name.clone(),
                        online: t.active,
                        version: t.version.unwrap_or_default(),
                        last_report_unix: t.last_report.as_deref().and_then(parse_iso8601_to_unix),
                        remote: t.remote_address.unwrap_or_default(),
                        location: match (t.location_name, t.floor_name) {
                            (Some(l), Some(f)) => format!("{} / {}", l, f),
                            (Some(l), None) => l,
                            _ => String::new(),
                        },
                        clock_drift: t.clock_drift_ms.map(|d| format!("{} ms", d)).unwrap_or_default(),
                        cpu: t.cpu_load.map(|l| format!("{:.1} %", l)).unwrap_or_default(),
                        memory: match (t.memory_used, t.memory_total) {
                            (Some(u), Some(tot)) => format!("{} / {}", format_bytes(u), format_bytes(tot)),
                            _ => String::new(),
                        },
                        throughput: t.processed_bytes.as_ref().and_then(|p| p.average)
                            .map(|a| format!("{}/s", format_bytes(a))).unwrap_or_default(),
                    });
                }
            }
            Err(ApiError::NotFound(_)) => { skipped_tenants += 1; continue; }
            Err(ApiError::Unauthorized) => {
                let response = match taps::find_all_high_level(&ctx.client, &pair.organization_id, &pair.tenant_id) {
                    Ok(response) => response,
                    Err(ApiError::NotFound(_)) => { skipped_tenants += 1; continue; }
                    Err(e) => return Err(e.to_string()),
                };
                limited_scopes += 1;
                for t in response.data.taps {
                    rows.push(TapRow {
                        name: t.name.unwrap_or_default(),
                        tenant: pair.tenant_name.clone(),
                        online: t.is_online,
                        version: String::new(),
                        last_report_unix: None,
                        remote: String::new(),
                        location: match (t.location_name, t.floor_name) {
                            (Some(l), Some(f)) => format!("{} / {}", l, f),
                            (Some(l), None) => l,
                            _ => String::new(),
                        },
                        clock_drift: String::new(),
                        cpu: String::new(),
                        memory: String::new(),
                        throughput: String::new(),
                    });
                }
            }
            Err(e) => return Err(e.to_string()),
        }
    }

    rows.sort_by(|a, b| a.tenant.cmp(&b.tenant).then_with(|| a.name.cmp(&b.name)));

    Ok(TapsData { rows, limited_scopes, skipped_tenants, scope_label })
}

impl View for TapsView {
    fn on_enter(&mut self) {
        self.poller.start();
    }

    fn on_leave(&mut self) {
        self.poller.pause();
    }

    fn draw(&mut self, f: &mut Frame, area: Rect) {
        let state = self.poller.state();
        let data = state.data.as_ref();
        let rows: &[TapRow] = data.map(|d| d.rows.as_slice()).unwrap_or(&[]);
        self.table.clamp(rows.len());

        let online = rows.iter().filter(|r| r.online == Some(true)).count();
        let title = match data {
            Some(d) => format!(" Taps in {}: {} total, {} online{}{} ", d.scope_label, rows.len(), online,
                               if d.limited_scopes > 0 { ", some tenants with status only" } else { "" },
                               skipped_label(d.skipped_tenants)),
            None => " Taps ".to_string(),
        };
        let block = titled_block(title);
        self.table.fit(area);

        if rows.is_empty() {
            render_message(f, area, block, state.empty_text("No taps found in this scope."));
            return;
        }

        let now = now_unix();

        let header = Row::new(["Name", "Tenant", "Status", "Version", "Last Report", "Remote", "Location", "Drift", "CPU", "Memory", "Throughput"]
            .into_iter()
            .map(header_cell));

        let table_rows = rows.iter().map(|r| {
            let status = match r.online {
                Some(true) => Span::styled("ONLINE", Style::default().fg(Color::Green).add_modifier(Modifier::BOLD)),
                Some(false) => Span::styled("OFFLINE", Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
                None => Span::raw(""),
            };
            let last_report = r.last_report_unix.map(|t| format!("{} ago", format_age_seconds(now - t))).unwrap_or_default();

            Row::new(vec![
                Cell::from(Span::styled(r.name.clone(), Style::default().add_modifier(Modifier::BOLD))),
                Cell::from(Span::styled(r.tenant.clone(), Style::default().fg(palette::MUTED))),
                Cell::from(status),
                Cell::from(r.version.clone()),
                Cell::from(last_report),
                Cell::from(r.remote.clone()),
                Cell::from(r.location.clone()),
                Cell::from(r.clock_drift.clone()),
                Cell::from(r.cpu.clone()),
                Cell::from(r.memory.clone()),
                Cell::from(r.throughput.clone()),
            ])
        });

        let widths = [
            Constraint::Min(28),
            Constraint::Min(16),
            Constraint::Length(7),
            Constraint::Length(22),
            Constraint::Length(11),
            Constraint::Length(15),
            Constraint::Percentage(10),
            Constraint::Length(7),
            Constraint::Length(6),
            Constraint::Length(21),
            Constraint::Length(11),
        ];

        let table = Table::new(table_rows, widths)
            .header(header)
            .block(block)
            .column_spacing(1)
            .row_highlight_style(row_highlight_style())
            .highlight_symbol(HIGHLIGHT_SYMBOL);

        f.render_stateful_widget(table, area, &mut self.table.state);
        self.table.render_scrollbar(f, area, rows.len());
    }

    fn handle_key(&mut self, key: KeyEvent) -> ViewAction {
        let n = self.poller.state().data.as_ref().map(|d| d.rows.len()).unwrap_or(0);
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
        let mut keys = ScrollableTable::KEYS.to_vec();
        keys.extend([("r", "refresh now"), ("q/Esc", "menu")]);
        keys
    }

    fn header_status(&self) -> Vec<Span<'static>> {
        self.poller.status_spans()
    }
}
