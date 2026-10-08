//! System: cluster nodes table. Nodes are global, so this view ignores the organization and tenant scope. Listing
//! them requires super administrator permissions.

use std::sync::Arc;

use ratatui::crossterm::event::{KeyCode, KeyEvent};
use ratatui::layout::{Constraint, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::Span;
use ratatui::widgets::{Cell, Row, Table};
use ratatui::Frame;

use crate::api::endpoints::nodes;
use crate::api::types::NodeResponse;
use crate::apps::api::gui::common::{
    header_cell, palette, render_message, row_highlight_style, titled_block, Poller, ScrollableTable, HIGHLIGHT_SYMBOL,
};
use crate::apps::api::gui::prefs::Context;
use crate::apps::api::gui::view::{View, ViewAction};
use crate::tools::{format_age_seconds, format_usage, parse_iso8601_to_unix};

pub struct NodesView {
    poller: Poller<Vec<NodeResponse>>,
    table: ScrollableTable,
}

impl NodesView {
    pub fn new(ctx: Arc<Context>) -> Self {
        let interval_ctx = Arc::clone(&ctx);
        let poller = Poller::new(
            move || interval_ctx.refresh_seconds(),
            move || nodes::find_all(&ctx.client).map(|r| r.data.nodes).map_err(|e| e.to_string()),
        );

        NodesView { poller, table: ScrollableTable::new() }
    }
}

impl View for NodesView {
    fn on_enter(&mut self) {
        self.poller.start();
    }

    fn on_leave(&mut self) {
        self.poller.pause();
    }

    fn draw(&mut self, f: &mut Frame, area: Rect) {
        let state = self.poller.state();
        let nodes = state.data.as_deref().unwrap_or(&[]);
        self.table.clamp(nodes.len());

        let online = nodes.iter().filter(|n| n.active == Some(true)).count();
        let block = titled_block(format!(" Cluster Nodes: {} total, {} online ", nodes.len(), online));
        self.table.fit(area);

        if nodes.is_empty() {
            render_message(f, area, block, state.empty_text("No nodes found."));
            return;
        }

        let now = crate::tools::now_unix();

        let header = Row::new(["Name", "Status", "Version", "Last Seen", "Clock Drift", "CPU", "Memory", "Heap", "Threads", "OS"]
            .into_iter()
            .map(header_cell));

        let rows = nodes.iter().map(|n| {
            let mut status = match n.active {
                Some(true) => Span::styled("ONLINE", Style::default().fg(Color::Green).add_modifier(Modifier::BOLD)),
                Some(false) => Span::styled("OFFLINE", Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
                None => Span::raw("<unknown>"),
            };
            if n.deleted == Some(true) {
                status = Span::styled("DELETED", Style::default().fg(palette::MUTED));
            }

            let last_seen = n.last_seen.as_deref().and_then(parse_iso8601_to_unix)
                .map(|t| format!("{} ago", format_age_seconds(now - t)))
                .unwrap_or_default();

            Row::new(vec![
                Cell::from(Span::styled(n.name.clone().unwrap_or_default(), Style::default().add_modifier(Modifier::BOLD))),
                Cell::from(status),
                Cell::from(n.version.clone().unwrap_or_default()),
                Cell::from(last_seen),
                Cell::from(n.clock_drift_ms.map(|d| format!("{} ms", d)).unwrap_or_default()),
                Cell::from(n.cpu_system_load.map(|l| format!("{:.1} %", l)).unwrap_or_default()),
                Cell::from(format_usage(n.memory_bytes_used, n.memory_bytes_total)),
                Cell::from(format_usage(n.heap_bytes_used, n.heap_bytes_total)),
                Cell::from(n.cpu_thread_count.map(|t| t.to_string()).unwrap_or_default()),
                Cell::from(n.os_information.clone().unwrap_or_default()),
            ])
        });

        let widths = [
            Constraint::Min(16),
            Constraint::Length(8),
            Constraint::Length(22),
            Constraint::Length(11),
            Constraint::Length(11),
            Constraint::Length(7),
            Constraint::Length(26),
            Constraint::Length(26),
            Constraint::Length(7),
            Constraint::Percentage(20),
        ];

        let table = Table::new(rows, widths)
            .header(header)
            .block(block)
            .column_spacing(1)
            .row_highlight_style(row_highlight_style())
            .highlight_symbol(HIGHLIGHT_SYMBOL);

        f.render_stateful_widget(table, area, &mut self.table.state);
        self.table.render_scrollbar(f, area, nodes.len());
    }

    fn handle_key(&mut self, key: KeyEvent) -> ViewAction {
        let n = self.poller.state().data.as_ref().map(|d| d.len()).unwrap_or(0);
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
