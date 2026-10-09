//! Helpers shared by the shell and its views: a background poller, a scrollable table and an options popup.
//! Views compose these instead of each owning a worker thread and key handling. The visual theme lives in
//! `crate::tui::theme` and is re-exported here.

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, MutexGuard};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

use ratatui::crossterm::event::KeyCode;
use ratatui::layout::{Margin, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Clear, Paragraph, Scrollbar, ScrollbarOrientation, ScrollbarState, TableState, Wrap};
use ratatui::Frame;

pub use crate::tui::theme::{
    palette, blink_on, centered_rect, cycle, fill_background, header_cell, lock, panel, panel_title, popup_panel,
    render_key_hints, render_message, row_highlight_style, segment_bar, spinner_frame, titled_block,
    HIGHLIGHT_SYMBOL,
};

pub const REFRESH_PRESETS: &[u64] = &[2, 5, 10, 15, 30, 60];
pub const DEFAULT_REFRESH_SECONDS: u64 = 10;

// ---------------------------------------------------------------------------------------------------------------
// Poller
// ---------------------------------------------------------------------------------------------------------------

pub struct PollState<T> {
    pub data: Option<T>,
    pub last_refresh: Option<Instant>,
    pub last_error: Option<String>,
    pub refreshing: bool,
    pub refresh_count: u64,
}

impl<T> PollState<T> {
    pub fn empty_text(&self, empty: &'static str) -> &'static str {
        if self.refresh_count == 0 {
            "Loading..."
        } else if self.last_error.is_some() {
            "No data received yet. See the error above."
        } else {
            empty
        }
    }
}

/// Runs a fetch function on a background thread at a configurable interval, only while the owning view is
/// visible. The latest result is kept in `PollState`. Dropping the poller stops the thread.
pub struct Poller<T> {
    state: Arc<Mutex<PollState<T>>>,
    interval: Arc<dyn Fn() -> u64 + Send + Sync>,
    active: Arc<AtomicBool>,
    quit: Arc<AtomicBool>,
    refresh_now: Arc<AtomicBool>,
    fetch: Arc<dyn Fn() -> Result<T, String> + Send + Sync>,
    worker: Option<JoinHandle<()>>,
}

impl<T: Send + 'static> Poller<T> {
    /// `interval` returns the current refresh interval in seconds and is consulted after every fetch.
    pub fn new(interval: impl Fn() -> u64 + Send + Sync + 'static,
               fetch: impl Fn() -> Result<T, String> + Send + Sync + 'static) -> Self {
        Poller {
            state: Arc::new(Mutex::new(PollState {
                data: None, last_refresh: None, last_error: None, refreshing: false, refresh_count: 0,
            })),
            interval: Arc::new(interval),
            active: Arc::new(AtomicBool::new(false)),
            quit: Arc::new(AtomicBool::new(false)),
            refresh_now: Arc::new(AtomicBool::new(false)),
            fetch: Arc::new(fetch),
            worker: None,
        }
    }

    /// Starts or resumes polling with an immediate fetch.
    pub fn start(&mut self) {
        self.active.store(true, Ordering::Relaxed);
        self.refresh_now.store(true, Ordering::Relaxed);

        if self.worker.is_none() {
            let state = Arc::clone(&self.state);
            let interval = Arc::clone(&self.interval);
            let active = Arc::clone(&self.active);
            let quit = Arc::clone(&self.quit);
            let refresh_now = Arc::clone(&self.refresh_now);
            let fetch = Arc::clone(&self.fetch);

            self.worker = Some(thread::spawn(move || {
                loop {
                    if quit.load(Ordering::Relaxed) {
                        return;
                    }

                    if !active.load(Ordering::Relaxed) {
                        thread::sleep(Duration::from_millis(100));
                        continue;
                    }

                    lock(&state).refreshing = true;
                    let result = (fetch)();
                    {
                        let mut s = lock(&state);
                        s.refreshing = false;
                        s.refresh_count += 1;
                        s.last_refresh = Some(Instant::now());
                        match result {
                            Ok(data) => { s.data = Some(data); s.last_error = None; }
                            Err(e) => s.last_error = Some(e),
                        }
                    }

                    // Sleep in small slices so quit, pause, interval changes and manual refresh react quickly.
                    let deadline = Instant::now() + Duration::from_secs((interval)().max(1));
                    while Instant::now() < deadline {
                        if quit.load(Ordering::Relaxed) {
                            return;
                        }
                        if refresh_now.swap(false, Ordering::Relaxed) || !active.load(Ordering::Relaxed) {
                            break;
                        }
                        thread::sleep(Duration::from_millis(100));
                    }
                }
            }));
        }
    }

    pub fn pause(&self) {
        self.active.store(false, Ordering::Relaxed);
    }

    pub fn refresh_now(&self) {
        self.refresh_now.store(true, Ordering::Relaxed);
    }

    pub fn interval_seconds(&self) -> u64 {
        (self.interval)()
    }

    pub fn state(&self) -> MutexGuard<'_, PollState<T>> {
        lock(&self.state)
    }

    /// Header spans describing the refresh state: loading, error, or age plus a countdown gauge.
    pub fn status_spans(&self) -> Vec<Span<'static>> {
        let s = self.state();
        let interval = self.interval_seconds();

        if let Some(err) = &s.last_error {
            return vec![
                Span::styled("▲ ERROR ", Style::default().fg(palette::WHITE).bg(Color::Red).add_modifier(Modifier::BOLD)),
                Span::styled(format!(" {}", err), Style::default().fg(Color::Red)),
            ];
        }

        if s.refreshing && s.refresh_count == 0 {
            return vec![Span::styled(format!("{} ACQUIRING", spinner_frame()), Style::default().fg(palette::ORANGE))];
        }

        let age = s.last_refresh.map(|t| t.elapsed().as_secs()).unwrap_or(0);
        let next = interval.saturating_sub(age);
        let fraction = if interval == 0 { 1.0 } else { age as f64 / interval as f64 };

        let mut spans = vec![
            Span::styled(
                if s.refreshing { format!("{} SYNC", spinner_frame()) } else { "● SYNC".to_string() },
                Style::default().fg(if s.refreshing { palette::ORANGE } else { Color::Green }).add_modifier(Modifier::BOLD),
            ),
            Span::styled(format!(" {}s ago ", age), Style::default().fg(palette::MUTED)),
            Span::styled(segment_bar(fraction, 10), Style::default().fg(palette::RED_DIM)),
            Span::styled(format!(" T-{}s", next), Style::default().fg(palette::MUTED)),
        ];
        if s.refreshing {
            spans.push(Span::styled(" refreshing", Style::default().fg(palette::ORANGE)));
        }
        spans
    }
}

impl<T> Drop for Poller<T> {
    fn drop(&mut self) {
        self.quit.store(true, Ordering::Relaxed);
        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Scrollable table
// ---------------------------------------------------------------------------------------------------------------

/// Selection and paging state of a table, with the standard navigation keys.
pub struct ScrollableTable {
    pub state: TableState,
    pub visible_rows: usize,
}

impl ScrollableTable {
    pub fn new() -> Self {
        ScrollableTable { state: TableState::default(), visible_rows: 1 }
    }

    /// Keeps the selection valid when the row count changes between refreshes.
    pub fn clamp(&mut self, n: usize) {
        match self.state.selected() {
            Some(_) if n == 0 => self.state.select(None),
            Some(s) if s >= n => self.state.select(Some(n - 1)),
            None if n > 0 => self.state.select(Some(0)),
            _ => {}
        }
    }

    /// Returns true if the key was a navigation key and has been handled.
    pub fn handle_key(&mut self, code: KeyCode, n: usize) -> bool {
        let page = self.visible_rows.max(1) as i64;

        match code {
            KeyCode::Up | KeyCode::Char('k') => self.move_by(n, -1),
            KeyCode::Down | KeyCode::Char('j') => self.move_by(n, 1),
            KeyCode::PageUp => self.move_by(n, -page),
            KeyCode::PageDown | KeyCode::Char(' ') => self.move_by(n, page),
            KeyCode::Home | KeyCode::Char('g') => if n > 0 { self.state.select(Some(0)) },
            KeyCode::End | KeyCode::Char('G') => if n > 0 { self.state.select(Some(n - 1)) },
            _ => return false,
        }

        true
    }

    fn move_by(&mut self, n: usize, delta: i64) {
        if n == 0 {
            self.state.select(None);
            return;
        }

        let current = self.state.selected().unwrap_or(0) as i64;
        let next = (current + delta).clamp(0, n as i64 - 1) as usize;
        self.state.select(Some(next));
    }

    /// Number of rows that fit into a bordered table area with a header.
    pub fn fit(&mut self, area: Rect) -> usize {
        self.visible_rows = area.height.saturating_sub(3) as usize;
        self.visible_rows
    }

    pub fn render_scrollbar(&self, f: &mut Frame, area: Rect, n: usize) {
        if n <= self.visible_rows {
            return;
        }

        let mut scrollbar_state = ScrollbarState::new(n)
            .position(self.state.selected().unwrap_or(0))
            .viewport_content_length(self.visible_rows);

        f.render_stateful_widget(
            Scrollbar::new(ScrollbarOrientation::VerticalRight)
                .begin_symbol(Some("▲")).end_symbol(Some("▼"))
                .thumb_style(Style::default().fg(palette::RED))
                .track_style(Style::default().fg(palette::FAINT)),
            area.inner(Margin { vertical: 1, horizontal: 0 }),
            &mut scrollbar_state,
        );
    }

    pub const KEYS: &'static [(&'static str, &'static str)] = &[
        ("↑/↓ j/k", "scroll"), ("PgUp/PgDn", "page"), ("g/G", "top/bottom"),
    ];
}

/// Title suffix for tenants a fetch had to skip because the server no longer knows them.
pub fn skipped_label(skipped: usize) -> String {
    match skipped {
        0 => String::new(),
        1 => ", 1 tenant skipped".to_string(),
        n => format!(", {} tenants skipped", n),
    }
}


// ---------------------------------------------------------------------------------------------------------------
// Options popup
// ---------------------------------------------------------------------------------------------------------------

/// A row in an options popup: a label and the current value, rendered as `◀ value ▶` when it can be cycled.
pub struct OptionRow {
    pub label: String,
    pub value: String,
    pub cyclable: bool,
}

pub fn render_options_popup(f: &mut Frame, area: Rect, title: &str, rows: &[OptionRow], selected: usize, hint: &str) {
    let popup = centered_rect(72, rows.len() as u16 + 6, area);
    f.render_widget(Clear, popup);

    let block = popup_panel(title);
    let inner = block.inner(popup);
    f.render_widget(block, popup);

    let selected_style = row_highlight_style();
    let label_style = Style::default().fg(palette::MUTED);
    let value_style = Style::default().fg(palette::WHITE);
    let arrow_style = Style::default().fg(palette::RED);

    let mut lines: Vec<Line> = vec![Line::raw("")];
    for (i, row) in rows.iter().enumerate() {
        let is_selected = i == selected;
        let marker = if is_selected { "▶ " } else { "  " };
        let label = Span::styled(
            format!("{}{:<22}", marker, row.label.to_uppercase()),
            if is_selected { selected_style } else { label_style },
        );

        let mut spans = vec![label];
        if row.cyclable {
            spans.push(Span::styled("◀ ", if is_selected { selected_style } else { arrow_style }));
            spans.push(Span::styled(row.value.clone(), if is_selected { selected_style } else { value_style }));
            spans.push(Span::styled(" ▶", if is_selected { selected_style } else { arrow_style }));
        } else {
            spans.push(Span::styled(row.value.clone(), if is_selected { selected_style } else { value_style }));
        }
        lines.push(Line::from(spans));
    }
    lines.push(Line::raw(""));
    lines.push(Line::styled(hint.to_string(), Style::default().fg(palette::FAINT)));

    f.render_widget(
        Paragraph::new(lines).wrap(Wrap { trim: true }),
        inner.inner(Margin { vertical: 0, horizontal: 1 }),
    );
}

pub const OPTIONS_KEYS: &[(&str, &str)] = &[("↑/↓", "select"), ("←/→", "change"), ("Esc", "close")];
