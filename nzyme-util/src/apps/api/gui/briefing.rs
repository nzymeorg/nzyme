//! A dashboard never stops looking, so fetching in last-visit mode on every refresh would move the window start
//! to the previous refresh once the server's grace period passes. The view therefore asks for the last-visit
//! window once, remembers its start as an anchor, and refreshes with an absolute range from that anchor to now:
//! the window grows while the dashboard runs instead of creeping.

use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use ratatui::crossterm::event::{KeyCode, KeyEvent};
use ratatui::layout::{Alignment, Constraint, Layout, Margin, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Paragraph, Scrollbar, ScrollbarOrientation, ScrollbarState};
use ratatui::Frame;
use serde_json::json;

use crate::api::endpoints::briefing::{self, BriefingQuery};
use crate::api::error::ApiError;
use crate::api::types::{
    BriefingAttentionItemResponse, BriefingChangeGroupResponse, BriefingLocationResponse, BriefingMonitoringResponse,
    BriefingResponse, BriefingWindowResponse,
};
use crate::apps::api::briefing_app::{
    activity_series, describe_window, format_value, health_indicator_name, plural, subsystem_name, verdict,
    window_phrase, VerdictTone,
};
use crate::apps::api::gui::common::{
    big_number, blink_on, centered_rect, lock, palette, panel, panel_title, skipped_label, spinner_frame, Poller,
    LOGO,
};
use crate::apps::api::gui::prefs::Context;
use crate::apps::api::gui::view::{View, ViewAction};
use crate::apps::api::{describe_wind, UnitSystem};
use crate::arguments::BriefingType;
use crate::tools::{format_age_seconds, format_duration_ms, format_iso8601_utc, now_unix, parse_iso8601_to_unix, short_time};

const MIN_REFRESH_SECONDS: u64 = 30;
const NOTICE_SECONDS: u64 = 6;
const STRIP_HEIGHT: u16 = 8;
const SPARKLINE_MAX_WIDTH: usize = 48;
const SPARKLINE: [char; 8] = ['▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'];
const MAX_BREAKDOWN_ENTRIES: usize = 5;
const MAX_ENVIRONMENT_ALERTS: usize = 2;

#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)]
enum WindowMode {
    LastVisit,
    LastSession,
    Last24h,
    Last7d,
}

impl WindowMode {
    const ALL: [WindowMode; 4] = [WindowMode::LastVisit, WindowMode::LastSession, WindowMode::Last24h, WindowMode::Last7d];

    fn label(&self) -> &'static str {
        match self {
            WindowMode::LastVisit => "SINCE LAST VISIT",
            WindowMode::LastSession => "SINCE LAST SESSION",
            WindowMode::Last24h => "LAST 24 HOURS",
            WindowMode::Last7d => "LAST 7 DAYS",
        }
    }

    fn next(&self) -> WindowMode {
        let i = WindowMode::ALL.iter().position(|m| m == self).unwrap_or(0);
        WindowMode::ALL[(i + 1) % WindowMode::ALL.len()]
    }

    fn is_anchored(&self) -> bool {
        matches!(self, WindowMode::LastVisit | WindowMode::LastSession)
    }
}

#[derive(Clone)]
struct Anchor {
    from: String,
    window: BriefingWindowResponse,
}

struct Selection {
    kind: BriefingType,
    mode: WindowMode,
    anchors: HashMap<(BriefingType, WindowMode), Anchor>,
    units: Option<UnitSystem>,
}

struct TenantBriefing {
    tenant_name: String,
    briefing: BriefingResponse,
}

struct BriefingData {
    kind: BriefingType,
    tenants: Vec<TenantBriefing>,
    skipped_tenants: usize,
    scope_label: String,
    units: UnitSystem,
    window: Option<BriefingWindowResponse>,
    generated_at: Option<String>,
}

pub struct BriefingView {
    ctx: Arc<Context>,
    selection: Arc<Mutex<Selection>>,
    poller: Poller<BriefingData>,
    scroll: usize,
    visible_rows: usize,
    line_count: usize,
    notice: Arc<Mutex<Option<(Instant, String)>>>,
    /// Only the view thread can ask the poller to refresh, so background work sets this instead.
    refresh_requested: Arc<AtomicBool>,
}

impl BriefingView {
    pub fn new(ctx: Arc<Context>) -> Self {
        let selection = Arc::new(Mutex::new(Selection {
            kind: BriefingType::Main, mode: WindowMode::LastVisit, anchors: HashMap::new(), units: None,
        }));

        let interval_ctx = Arc::clone(&ctx);
        let fetch_ctx = Arc::clone(&ctx);
        let fetch_selection = Arc::clone(&selection);
        let poller = Poller::new(
            move || interval_ctx.refresh_seconds().max(MIN_REFRESH_SECONDS),
            move || fetch(&fetch_ctx, &fetch_selection),
        );

        BriefingView {
            ctx, selection, poller, scroll: 0, visible_rows: 1, line_count: 0,
            notice: Arc::new(Mutex::new(None)),
            refresh_requested: Arc::new(AtomicBool::new(false)),
        }
    }

    pub fn title(&self) -> String {
        format!("BRIEFING // {}", lock(&self.selection).kind.display_name().to_uppercase())
    }

    fn set_kind(&mut self, kind: BriefingType) {
        {
            let mut selection = lock(&self.selection);
            if selection.kind == kind {
                return;
            }
            selection.kind = kind;
        }
        self.reload();
    }

    fn cycle_kind(&mut self) {
        let current = lock(&self.selection).kind;
        let i = BriefingType::ALL.iter().position(|k| *k == current).unwrap_or(0);
        self.set_kind(BriefingType::ALL[(i + 1) % BriefingType::ALL.len()]);
    }

    fn cycle_mode(&mut self) {
        {
            let mut selection = lock(&self.selection);
            let next = selection.mode.next();
            selection.mode = next;
        }
        self.reload();
    }

    fn reload(&mut self) {
        self.poller.state().data = None;
        self.scroll = 0;
        self.poller.refresh_now();
    }

    fn mark_seen(&self) {
        let kind = lock(&self.selection).kind;
        let client = Arc::clone(&self.ctx.client);
        let selection = Arc::clone(&self.selection);
        let notice = Arc::clone(&self.notice);
        let refresh_requested = Arc::clone(&self.refresh_requested);

        *lock(&self.notice) = Some((Instant::now(), format!("{} Marking as seen...", spinner_frame())));

        thread::spawn(move || {
            let message = match briefing::mark_seen(&client, kind.api_name()) {
                Ok(()) => {
                    let mut s = lock(&selection);
                    s.anchors.remove(&(kind, WindowMode::LastVisit));
                    s.mode = WindowMode::LastVisit;
                    refresh_requested.store(true, Ordering::Release);
                    format!("{} briefing marked as seen. The window starts fresh from now.", kind.display_name())
                }
                Err(e) => format!("Could not mark as seen: {}", e),
            };
            *lock(&notice) = Some((Instant::now(), message));
        });
    }

    fn current_notice(&self) -> Option<String> {
        let notice = lock(&self.notice);
        match &*notice {
            Some((at, text)) if at.elapsed() < Duration::from_secs(NOTICE_SECONDS) => Some(text.clone()),
            _ => None,
        }
    }

    fn scroll_by(&mut self, delta: i64) {
        let max = self.line_count.saturating_sub(self.visible_rows) as i64;
        self.scroll = (self.scroll as i64 + delta).clamp(0, max) as usize;
    }
}

const DISCOVERY_WAIT: Duration = Duration::from_secs(20);

fn wait_for_pairs(ctx: &Context) -> Result<Vec<crate::apps::api::gui::scope::ScopePair>, String> {
    let deadline = Instant::now() + DISCOVERY_WAIT;
    loop {
        match ctx.pairs() {
            Ok(pairs) => return Ok(pairs),
            Err(_) if ctx.is_discovering() && Instant::now() < deadline => thread::sleep(Duration::from_millis(200)),
            Err(e) => return Err(e),
        }
    }
}

fn fetch(ctx: &Context, selection: &Mutex<Selection>) -> Result<BriefingData, String> {
    let pairs = wait_for_pairs(ctx)?;
    let scope_label = ctx.prefs().scope.label(&lock(&ctx.scope_tree));
    let taps = ctx.prefs().taps.query_value();

    let (kind, mode, mut anchor) = {
        let s = lock(selection);
        (s.kind, s.mode, s.anchors.get(&(s.kind, s.mode)).cloned())
    };

    // The guard of the first lock must be gone before the second one is taken.
    let cached_units = lock(selection).units;
    let units = match cached_units {
        Some(units) => units,
        None => {
            let units = UnitSystem::of_user(&ctx.client);
            lock(selection).units = Some(units);
            units
        }
    };

    let mut tenants = Vec::new();
    let mut skipped_tenants = 0;
    let mut window = None;
    let mut generated_at = None;

    for pair in pairs {
        let (since, time_range) = query(mode, anchor.as_ref());
        let request = BriefingQuery {
            briefing_type: kind.api_name(),
            organization_id: &pair.organization_id,
            tenant_id: &pair.tenant_id,
            taps: Some(&taps),
            since,
            time_range: time_range.as_deref(),
        };

        let response = match briefing::find(&ctx.client, &request) {
            Ok(response) => response.data,
            Err(ApiError::NotFound(_)) => { skipped_tenants += 1; continue; }
            Err(e) => return Err(e.to_string()),
        };

        if window.is_none() {
            window = response.window.clone();
            generated_at = response.generated_at.clone();
        }

        // The first round of an anchored mode fixes the window start for all later rounds. All tenants of the
        // same round share the window anyway, because the server keeps it within its grace period.
        if anchor.is_none() && mode.is_anchored() {
            if let Some(from) = response.window.as_ref().and_then(|w| w.from.clone()) {
                let fixed = Anchor { from, window: response.window.clone().unwrap_or_else(empty_window) };
                lock(selection).anchors.insert((kind, mode), fixed.clone());
                anchor = Some(fixed);
            }
        }

        tenants.push(TenantBriefing { tenant_name: pair.tenant_name.clone(), briefing: response });
    }

    if let (Some(w), Some(a)) = (&mut window, &anchor) {
        w.mode = a.window.mode.clone();
        w.is_first_visit = a.window.is_first_visit;
        w.capped = a.window.capped;
    }

    Ok(BriefingData { kind, tenants, skipped_tenants, scope_label, units, window, generated_at })
}

fn empty_window() -> BriefingWindowResponse {
    BriefingWindowResponse { mode: None, from: None, to: None, duration_ms: None, is_first_visit: None, capped: None }
}

fn query(mode: WindowMode, anchor: Option<&Anchor>) -> (&'static str, Option<String>) {
    match (mode, anchor) {
        (WindowMode::LastVisit, None) => ("LAST_VISIT", None),
        (WindowMode::LastSession, None) => ("LAST_SESSION", None),
        (WindowMode::LastVisit | WindowMode::LastSession, Some(anchor)) => ("TIME_RANGE", Some(
            json!({"type": "absolute", "from": anchor.from, "to": format_iso8601_utc(now_unix())}).to_string()
        )),
        (WindowMode::Last24h, _) => ("TIME_RANGE", Some(json!({"type": "relative", "minutes": 24 * 60}).to_string())),
        (WindowMode::Last7d, _) => ("TIME_RANGE", Some(json!({"type": "relative", "minutes": 7 * 24 * 60}).to_string())),
    }
}

impl View for BriefingView {
    fn on_enter(&mut self) {
        self.poller.start();
    }

    fn on_leave(&mut self) {
        self.poller.pause();
    }

    fn draw(&mut self, f: &mut Frame, area: Rect) {
        if self.refresh_requested.swap(false, Ordering::AcqRel) {
            self.poller.refresh_now();
        }

        let (kind, mode) = {
            let s = lock(&self.selection);
            (s.kind, s.mode)
        };
        let notice = self.current_notice();

        let state = self.poller.state();
        let Some(data) = state.data.as_ref() else {
            // A failure while discovery is still running is only the fetch being early, not a connection problem.
            let error = state.last_error.as_deref().filter(|_| !self.ctx.is_discovering());
            render_splash(f, area, &self.ctx, kind, error);
            return;
        };

        let chunks = Layout::vertical([Constraint::Length(STRIP_HEIGHT), Constraint::Min(5)]).split(area);
        render_strip(f, chunks[0], data, kind, mode, notice.as_deref());

        let block = panel().title_top(panel_title(&format!(
            "{} briefing in {}{}", data.kind.display_name(), data.scope_label, skipped_label(data.skipped_tenants)
        )));
        let inner = block.inner(chunks[1]).inner(Margin { vertical: 0, horizontal: 1 });
        f.render_widget(block, chunks[1]);

        let width = inner.width.saturating_sub(1) as usize;
        let lines = build_document(data, width);

        self.line_count = lines.len();
        self.visible_rows = inner.height as usize;
        self.scroll = self.scroll.min(self.line_count.saturating_sub(self.visible_rows));

        let visible: Vec<Line> = lines.into_iter().skip(self.scroll).take(self.visible_rows).collect();
        f.render_widget(Paragraph::new(visible), inner);

        if self.line_count > self.visible_rows {
            let mut scrollbar_state = ScrollbarState::new(self.line_count.saturating_sub(self.visible_rows))
                .position(self.scroll)
                .viewport_content_length(self.visible_rows);
            f.render_stateful_widget(
                Scrollbar::new(ScrollbarOrientation::VerticalRight)
                    .begin_symbol(Some("▲")).end_symbol(Some("▼"))
                    .thumb_style(Style::default().fg(palette::RED))
                    .track_style(Style::default().fg(palette::FAINT)),
                chunks[1].inner(Margin { vertical: 1, horizontal: 0 }),
                &mut scrollbar_state,
            );
        }
    }

    fn handle_key(&mut self, key: KeyEvent) -> ViewAction {
        let page = self.visible_rows.max(1) as i64;

        match key.code {
            KeyCode::Up | KeyCode::Char('k') => self.scroll_by(-1),
            KeyCode::Down | KeyCode::Char('j') => self.scroll_by(1),
            KeyCode::PageUp => self.scroll_by(-page),
            KeyCode::PageDown | KeyCode::Char(' ') => self.scroll_by(page),
            KeyCode::Home | KeyCode::Char('g') => self.scroll = 0,
            KeyCode::End | KeyCode::Char('G') => self.scroll_by(i64::MAX / 2),
            KeyCode::Tab => self.cycle_kind(),
            KeyCode::Char('1') => self.set_kind(BriefingType::Main),
            KeyCode::Char('2') => self.set_kind(BriefingType::Dot11),
            KeyCode::Char('3') => self.set_kind(BriefingType::Ethernet),
            KeyCode::Char('4') => self.set_kind(BriefingType::Bluetooth),
            KeyCode::Char('w') => self.cycle_mode(),
            KeyCode::Char('n') => self.mark_seen(),
            KeyCode::Char('r') => self.poller.refresh_now(),
            KeyCode::Enter | KeyCode::Char('m') | KeyCode::Esc => return ViewAction::Back,
            KeyCode::Char('q') => return ViewAction::Quit,
            _ => {}
        }

        ViewAction::Continue
    }

    fn footer_keys(&self) -> Vec<(&'static str, &'static str)> {
        vec![
            ("Tab 1-4", "briefing"), ("w", "window"), ("n", "mark seen"), ("↑/↓", "scroll"),
            ("r", "refresh"), ("Enter", "menu"), ("q", "quit"),
        ]
    }

    fn header_status(&self) -> Vec<Span<'static>> {
        self.poller.status_spans()
    }
}

fn render_splash(f: &mut Frame, area: Rect, ctx: &Arc<Context>, kind: BriefingType, error: Option<&str>) {
    let popup = centered_rect(80, 19 + if error.is_some() { 2 } else { 0 }, area);
    let block = panel().title_top(panel_title("Nzyme Terminal"));
    let inner = block.inner(popup);
    f.render_widget(block, popup);

    let logo_style = Style::default().fg(palette::RED).add_modifier(Modifier::BOLD);
    let dim = Style::default().fg(palette::MUTED);
    let faint = Style::default().fg(palette::FAINT);
    let text = Style::default().fg(palette::TEXT);

    let prefs = ctx.prefs();
    let rows = [
        ("LINK", ctx.client.base_url().to_string()),
        ("SCOPE", prefs.scope.label(&lock(&ctx.scope_tree))),
        ("TAPS", prefs.taps.label(&lock(&ctx.taps))),
        ("BRIEFING", kind.display_name().to_uppercase()),
    ];

    let mut lines: Vec<Line> = vec![Line::raw("")];
    lines.extend(LOGO.iter().map(|row| Line::styled(*row, logo_style)));
    lines.push(Line::styled("T E R M I N A L", Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD)));
    lines.push(Line::raw(""));

    const WIDTH: usize = 60;
    lines.push(Line::styled("─".repeat(WIDTH), faint));
    for (label, value) in rows {
        let dots = ".".repeat(10usize.saturating_sub(label.len()));
        let used = 2 + label.len() + 1 + dots.len() + 1 + value.chars().count();
        lines.push(Line::from(vec![
            Span::styled("> ", Style::default().fg(palette::RED)),
            Span::styled(format!("{} {} ", label, dots), dim),
            Span::styled(value, text),
            Span::raw(" ".repeat(WIDTH.saturating_sub(used))),
        ]));
    }
    lines.push(Line::styled("─".repeat(WIDTH), faint));
    lines.push(Line::raw(""));

    match error {
        Some(error) => {
            let max = inner.width.saturating_sub(4) as usize;
            let mut message: String = error.chars().take(max).collect();
            if message.chars().count() < error.chars().count() { message.push('…'); }
            lines.push(Line::from(vec![
                Span::styled("▲ ", Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
                Span::styled(message, Style::default().fg(Color::Red)),
            ]));
            lines.push(Line::styled("Check the server, your network, and that the API key is still valid.", faint));
            lines.push(Line::from(vec![
                Span::styled("PRESS ", dim),
                Span::styled("[R]", Style::default().fg(palette::RED).add_modifier(Modifier::BOLD)),
                Span::styled(" TO RETRY OR ", dim),
                Span::styled("[P]", Style::default().fg(palette::RED).add_modifier(Modifier::BOLD)),
                Span::styled(" TO SELECT A PROFILE", dim),
            ]));
        }
        None => lines.push(Line::styled(format!("{} ACQUIRING BRIEFING", spinner_frame()), Style::default().fg(palette::ORANGE))),
    }

    f.render_widget(Paragraph::new(lines).alignment(Alignment::Center), inner);
}

fn tone_color(tone: VerdictTone) -> Color {
    match tone {
        VerdictTone::Quiet => Color::Green,
        VerdictTone::Note => palette::TEXT,
        VerdictTone::Alert | VerdictTone::Red => Color::Red,
        VerdictTone::Orange => palette::ORANGE,
    }
}

fn aggregate_verdict(data: &BriefingData) -> (VerdictTone, String) {
    let items: Vec<&BriefingAttentionItemResponse> = data.tenants.iter().flat_map(|t| t.briefing.attention.iter()).collect();
    verdict(&items, data.kind == BriefingType::Main)
}

fn render_strip(f: &mut Frame, area: Rect, data: &BriefingData, kind: BriefingType, mode: WindowMode, notice: Option<&str>) {
    let mut tabs: Vec<Span> = Vec::new();
    for (i, t) in BriefingType::ALL.iter().enumerate() {
        let label = format!(" {} {} ", i + 1, t.display_name().to_uppercase());
        tabs.push(if *t == kind {
            Span::styled(label, Style::default().fg(palette::WHITE).bg(palette::RED_GLOW).add_modifier(Modifier::BOLD))
        } else {
            Span::styled(label, Style::default().fg(palette::MUTED))
        });
    }
    tabs.push(Span::raw(" "));

    let block = panel().title_top(panel_title("Briefing")).title_top(Line::from(tabs).right_aligned());
    let inner = block.inner(area);
    f.render_widget(block, area);

    let attention: usize = data.tenants.iter().map(|t| t.briefing.attention.len()).sum();
    let (tone, sentence) = aggregate_verdict(data);
    let accent = tone_color(tone);

    let digits = big_number(attention);
    let digits_width = digits[0].chars().count() as u16;
    let columns = Layout::horizontal([
        Constraint::Length(digits_width + 4),
        Constraint::Length(14),
        Constraint::Min(20),
    ]).split(inner.inner(Margin { vertical: 0, horizontal: 1 }));

    let mut counter: Vec<Line> = vec![Line::raw("")];
    counter.extend(digits.iter()
        .map(|row| Line::from(Span::styled(format!(" {} ", row), Style::default().fg(accent).add_modifier(Modifier::BOLD)))));
    f.render_widget(Paragraph::new(counter), columns[0]);

    let state_word = match tone {
        VerdictTone::Quiet => "● ALL QUIET".to_string(),
        VerdictTone::Note => "● WORTH A LOOK".to_string(),
        _ => if blink_on() { "▲ ATTENTION".to_string() } else { "  ATTENTION".to_string() },
    };
    f.render_widget(Paragraph::new(vec![
        Line::raw(""),
        Line::raw(""),
        Line::from(Span::styled(state_word, Style::default().fg(accent).add_modifier(Modifier::BOLD))),
        Line::from(Span::styled(if attention == 1 { "ITEM" } else { "ITEMS" }, Style::default().fg(palette::MUTED))),
    ]), columns[1]);

    let label = Style::default().fg(palette::MUTED);
    let value = Style::default().fg(palette::WHITE);
    let faint = Style::default().fg(palette::FAINT);

    let window_text = data.window.as_ref().map(describe_window).unwrap_or_default();
    let generated = data.generated_at.as_deref().map(short_time).unwrap_or_else(|| "unknown".to_string());
    let tenants = format!("{} {}", data.tenants.len(), plural(data.tenants.len() as i64, "tenant", "tenants"));

    let last = match notice {
        Some(notice) => Line::from(vec![
            Span::styled("▲ ", Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD)),
            Span::styled(notice.to_string(), Style::default().fg(palette::ORANGE)),
        ]),
        None => Line::from(Span::styled("[Tab] next briefing  ·  [w] next window  ·  [n] mark as seen", faint)),
    };

    f.render_widget(Paragraph::new(vec![
        Line::raw(""),
        Line::from(Span::styled(sentence, Style::default().fg(accent).add_modifier(Modifier::BOLD))),
        Line::from(vec![Span::styled("WINDOW ", label), Span::styled(mode.label().to_string(), value), Span::styled(format!("  {}", window_text), label)]),
        Line::from(vec![
            Span::styled("GENERATED ", label), Span::styled(generated, Style::default().fg(palette::TEXT)),
            Span::styled("  ·  ", faint),
            Span::styled(data.scope_label.clone(), Style::default().fg(palette::TEXT)),
            Span::styled(format!(", {}", tenants), label),
        ]),
        last,
    ]), columns[2]);
}

struct Doc {
    width: usize,
    lines: Vec<Line<'static>>,
}

impl Doc {
    fn blank(&mut self) {
        self.lines.push(Line::raw(""));
    }

    fn push(&mut self, spans: Vec<Span<'static>>) {
        self.lines.push(Line::from(spans));
    }

    fn section(&mut self, title: &str) {
        self.blank();
        let label = format!(" // {} ", title.to_uppercase());
        self.push(vec![
            Span::styled(label.clone(), Style::default().fg(palette::RED).add_modifier(Modifier::BOLD)),
            Span::styled("─".repeat(self.width.saturating_sub(label.chars().count())), Style::default().fg(palette::RED_DIM)),
        ]);
    }

    fn text(&mut self, indent: usize, text: &str, style: Style) {
        for row in wrap(text, self.width.saturating_sub(indent).max(10)) {
            self.push(vec![Span::raw(" ".repeat(indent)), Span::styled(row, style)]);
        }
    }

    fn tagged(&mut self, tag: Span<'static>, prefix: Option<String>, title: &str, title_style: Style) {
        let prefix_len = prefix.as_ref().map(|p| p.chars().count()).unwrap_or(0);
        let rows = wrap(title, self.width.saturating_sub(10 + prefix_len).max(10));
        for (i, row) in rows.into_iter().enumerate() {
            let mut spans = vec![Span::raw("  ")];
            if i == 0 {
                spans.push(tag.clone());
                spans.push(Span::raw("  "));
                if let Some(prefix) = &prefix {
                    spans.push(Span::styled(prefix.clone(), Style::default().fg(palette::ORANGE)));
                }
            } else {
                spans.push(Span::raw(" ".repeat(8 + prefix_len)));
            }
            spans.push(Span::styled(row, title_style));
            self.push(spans);
        }
    }
}

fn severity_tag(severity: Option<&str>) -> Span<'static> {
    match severity {
        Some("RED") => Span::styled("RED   ", Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
        Some("ORANGE") => Span::styled("ORANGE", Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD)),
        Some("ALERT") => Span::styled("ALERT ", Style::default().fg(Color::Red)),
        Some("NOTICE") => Span::styled("NOTICE", Style::default().fg(palette::TEXT)),
        Some("INFO") => Span::styled("INFO  ", Style::default().fg(palette::MUTED)),
        Some(other) => Span::styled(format!("{:<6}", other.chars().take(6).collect::<String>()), Style::default().fg(palette::MUTED)),
        None => Span::raw("      "),
    }
}

fn level_tag(level: &str) -> Span<'static> {
    match level {
        "RED" => Span::styled("RED   ", Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
        "ORANGE" => Span::styled("ORANGE", Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD)),
        "GREEN" => Span::styled("GREEN ", Style::default().fg(Color::Green)),
        other => Span::styled(format!("{:<6}", other.chars().take(6).collect::<String>()), Style::default().fg(palette::MUTED)),
    }
}

fn check_tag(level: Option<&str>) -> Span<'static> {
    match level {
        Some("OK") => Span::styled("OK    ", Style::default().fg(Color::Green)),
        Some("WARN") => Span::styled("WARN  ", Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD)),
        Some("INFO") => Span::styled("INFO  ", Style::default().fg(palette::MUTED)),
        Some(other) => Span::styled(format!("{:<6}", other.chars().take(6).collect::<String>()), Style::default().fg(palette::MUTED)),
        None => Span::raw("      "),
    }
}

fn build_document(data: &BriefingData, width: usize) -> Vec<Line<'static>> {
    let mut doc = Doc { width, lines: Vec::new() };
    let is_main = data.kind == BriefingType::Main;
    let multi = data.tenants.len() > 1;

    if data.tenants.is_empty() {
        doc.blank();
        doc.text(2, "No tenants in the selected scope.", Style::default().fg(palette::MUTED));
        return doc.lines;
    }

    for tenant in &data.tenants {
        let b = &tenant.briefing;

        if multi {
            doc.blank();
            let rule = "═".repeat(width.saturating_sub(tenant.tenant_name.chars().count() + 4));
            doc.push(vec![
                Span::styled("▌ ", Style::default().fg(palette::RED)),
                Span::styled(tenant.tenant_name.to_uppercase(), Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD)),
                Span::styled(format!(" {}", rule), Style::default().fg(palette::RED_DIM)),
            ]);
            let items: Vec<&BriefingAttentionItemResponse> = b.attention.iter().collect();
            let (tone, sentence) = verdict(&items, is_main);
            doc.text(2, &sentence, Style::default().fg(tone_color(tone)).add_modifier(Modifier::BOLD));
        }

        attention_section(&mut doc, &b.attention, is_main);
        if let Some(monitoring) = &b.monitoring {
            health_section(&mut doc, monitoring);
            coverage_section(&mut doc, monitoring);
            taps_section(&mut doc, monitoring);
        }
        locations_section(&mut doc, &b.locations, data.kind, data.units);

        let taps_total = b.monitoring.as_ref().and_then(|m| m.taps_total).unwrap_or(0);
        if taps_total > 0 {
            activity_section(&mut doc, b, data.kind);
        }
        changes_section(&mut doc, &b.changes, b.window.as_ref(), is_main);
    }

    doc.blank();
    doc.lines
}

fn attention_section(doc: &mut Doc, attention: &[BriefingAttentionItemResponse], is_main: bool) {
    if attention.is_empty() {
        doc.section("Needs your attention");
        doc.push(vec![
            Span::raw("  "),
            Span::styled("● ", Style::default().fg(Color::Green)),
            Span::styled("All quiet.", Style::default().fg(Color::Green).add_modifier(Modifier::BOLD)),
            Span::styled(" No new detection alerts, every tap is reporting, and nothing in the monitoring \
                configuration needs fixing.", Style::default().fg(palette::MUTED)),
        ]);
        return;
    }

    doc.section(&format!("Needs your attention ({})", attention.len()));
    for item in attention {
        let prefix = match (&item.subsystem, is_main) {
            (Some(s), true) => Some(format!("[{}] ", subsystem_name(s))),
            _ => None,
        };
        doc.tagged(severity_tag(item.severity.as_deref()), prefix, item.title.as_deref().unwrap_or(""),
                   Style::default().fg(palette::TEXT).add_modifier(Modifier::BOLD));
        if let Some(text) = item.text.as_deref().filter(|t| !t.is_empty()) {
            doc.text(10, text, Style::default().fg(palette::MUTED));
        }
    }
}

fn health_section(doc: &mut Doc, monitoring: &BriefingMonitoringResponse) {
    let Some(indicators) = &monitoring.health_indicators else { return; };

    let count = |level: &str| indicators.values().filter(|l| l.as_str() == level).count();
    let (green, orange, red) = (count("GREEN"), count("ORANGE"), count("RED"));

    doc.section("Cluster health");
    let muted = Style::default().fg(palette::MUTED);
    doc.push(vec![
        Span::raw("  "),
        Span::styled(format!("{} green", green), Style::default().fg(Color::Green)),
        Span::styled("  ·  ", muted),
        Span::styled(format!("{} orange", orange), Style::default().fg(if orange > 0 { palette::ORANGE } else { palette::FAINT })),
        Span::styled("  ·  ", muted),
        Span::styled(format!("{} red", red), Style::default().fg(if red > 0 { Color::Red } else { palette::FAINT })),
    ]);

    let mut not_green: Vec<(&String, &String)> = indicators.iter().filter(|(_, l)| l.as_str() != "GREEN").collect();
    not_green.sort_by_key(|(_, level)| if level.as_str() == "RED" { 0 } else { 1 });

    if not_green.is_empty() {
        doc.text(2, "All indicators are green.", muted);
    }
    for (id, level) in not_green {
        doc.tagged(level_tag(level), None, health_indicator_name(id), Style::default().fg(palette::TEXT));
    }
}

fn coverage_section(doc: &mut Doc, monitoring: &BriefingMonitoringResponse) {
    if monitoring.checks.is_empty() {
        return;
    }

    doc.section("Detection coverage");
    for check in &monitoring.checks {
        let prefix = check.subsystem.as_deref().map(|s| format!("[{}] ", subsystem_name(s)));
        doc.tagged(check_tag(check.level.as_deref()), prefix, check.title.as_deref().unwrap_or(""),
                   Style::default().fg(palette::TEXT));
        if let Some(detail) = check.detail.as_deref().filter(|d| !d.is_empty()) {
            doc.text(10, detail, Style::default().fg(palette::MUTED));
        }
    }
}

fn taps_section(doc: &mut Doc, monitoring: &BriefingMonitoringResponse) {
    if monitoring.taps.is_empty() {
        return;
    }

    doc.section(&format!("Taps ({} of {} online)",
                         monitoring.taps_online.unwrap_or(0), monitoring.taps_total.unwrap_or(monitoring.taps.len() as i64)));

    let name_width = monitoring.taps.iter().map(|t| t.name.as_deref().unwrap_or("<unknown>").chars().count()).max().unwrap_or(0);
    let now = now_unix();
    let muted = Style::default().fg(palette::MUTED);

    for tap in &monitoring.taps {
        let status = match tap.online {
            Some(true) => Span::styled("ONLINE ", Style::default().fg(Color::Green).add_modifier(Modifier::BOLD)),
            Some(false) => Span::styled("OFFLINE", Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
            None => Span::styled("UNKNOWN", muted),
        };

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

        let mut spans = vec![
            Span::raw("  "),
            status,
            Span::raw("  "),
            Span::styled(format!("{:<name_width$}", tap.name.as_deref().unwrap_or("<unknown>"), name_width = name_width),
                         Style::default().fg(palette::TEXT)),
            Span::styled(format!("  {}", tap.location_name.as_deref().unwrap_or("no location")), muted),
            Span::styled(format!("  ·  {}, {}", last_report, captures), muted),
        ];
        if let Some(n) = tap.active_alerts.filter(|n| *n > 0) {
            spans.push(Span::styled(format!(", {} active {}", n, plural(n, "alert", "alerts")), Style::default().fg(Color::Red)));
        }
        doc.push(spans);
    }
}

fn locations_section(doc: &mut Doc, locations: &[BriefingLocationResponse], kind: BriefingType, units: UnitSystem) {
    if locations.is_empty() {
        return;
    }

    let subsystem = if kind == BriefingType::Main { None } else { Some(kind.display_name()) };
    let can_view_alerts = locations.iter().any(|l| l.alert_count.is_some());

    doc.section("Locations");
    doc.text(2, &format!("Taps, active {}detection alerts and weather at each location as of now, not limited to this \
        briefing's window.", subsystem.map(|s| format!("{} ", s)).unwrap_or_default()), Style::default().fg(palette::FAINT));

    let name_width = locations.iter().map(|l| l.name.as_deref().unwrap_or("<unknown>").chars().count()).max().unwrap_or(0);
    let muted = Style::default().fg(palette::MUTED);

    for location in locations {
        let taps = match (location.taps_total, location.taps_online) {
            (Some(0), _) | (None, _) => Span::styled("no taps".to_string(), muted),
            (Some(total), online) => {
                let text = format!("{} of {} {} online", online.unwrap_or(0), total, plural(total, "tap", "taps"));
                if location.offline_taps.is_empty() {
                    Span::styled(text, Style::default().fg(palette::TEXT))
                } else {
                    Span::styled(text, Style::default().fg(Color::Red))
                }
            }
        };

        let alerts = if !can_view_alerts {
            Span::raw("")
        } else {
            match location.alert_count {
                None => Span::styled(", alerts n/a".to_string(), muted),
                Some(0) => Span::styled(", no active alerts".to_string(), muted),
                Some(n) => Span::styled(format!(", {} active {}", n, plural(n, "alert", "alerts")), Style::default().fg(Color::Red)),
            }
        };

        doc.push(vec![
            Span::raw("  "),
            Span::styled(format!("{:<name_width$}", location.name.as_deref().unwrap_or("<unknown>"), name_width = name_width),
                         Style::default().fg(palette::TEXT).add_modifier(Modifier::BOLD)),
            Span::raw("  "),
            taps,
            alerts,
        ]);

        let indent = name_width + 4;
        match &location.environment {
            None => doc.text(indent, "weather: no data", muted),
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
                doc.text(indent, &format!("weather: {}", parts.join(", ")), muted);

                if !env.alerts.is_empty() {
                    let shown: Vec<&str> = env.alerts.iter().take(MAX_ENVIRONMENT_ALERTS).map(String::as_str).collect();
                    let remaining = env.alerts.len().saturating_sub(MAX_ENVIRONMENT_ALERTS);
                    let more = if remaining > 0 {
                        format!(" and {} more {}", remaining, plural(remaining as i64, "alert", "alerts"))
                    } else {
                        String::new()
                    };
                    doc.text(indent, &format!("severe environmental alerts: {}{}", shown.join(", "), more),
                             Style::default().fg(Color::Red));
                }
            }
        }
    }
}

fn activity_section(doc: &mut Doc, b: &BriefingResponse, kind: BriefingType) {
    let series = activity_series(&b.activity, kind);
    if series.is_empty() {
        return;
    }
    let Some(window) = &b.window else { return; };
    let split_at = window.from.as_deref().and_then(parse_iso8601_to_unix).unwrap_or(0);

    doc.section("Activity");
    doc.text(2, &format!("Left of the bar the window before this briefing's, right of it everything {}.", window_phrase(window)),
             Style::default().fg(palette::FAINT));

    let title_width = series.iter().map(|s| s.title.chars().count()).max().unwrap_or(0);
    let available = doc.width.saturating_sub(title_width + 4 + 34).clamp(12, SPARKLINE_MAX_WIDTH);

    for s in &series {
        let mut buckets: Vec<(i64, f64)> = s.buckets.iter()
            .filter_map(|bucket| Some((parse_iso8601_to_unix(bucket.bucket.as_deref()?)?, bucket.value.unwrap_or(0.0))))
            .collect();
        buckets.sort_by_key(|(t, _)| *t);

        let title = Span::styled(format!("  {:<title_width$}  ", s.title, title_width = title_width), Style::default().fg(palette::TEXT));

        if buckets.is_empty() || buckets.iter().all(|(_, v)| *v == 0.0) {
            doc.push(vec![title, Span::styled("no data in this period", Style::default().fg(palette::MUTED))]);
            continue;
        }

        let previous: Vec<f64> = buckets.iter().filter(|(t, _)| *t < split_at).map(|(_, v)| *v).collect();
        let current: Vec<f64> = buckets.iter().filter(|(t, _)| *t >= split_at).map(|(_, v)| *v).collect();
        let max = buckets.iter().map(|(_, v)| *v).fold(0.0f64, f64::max);

        let total = previous.len() + current.len();
        let previous_width = if previous.is_empty() { 0 } else { ((previous.len() * (available - 1)) / total.max(1)).max(1) };
        let current_width = if current.is_empty() { 0 } else { (available - 1 - previous_width).max(1) };

        let peak = |values: &[f64]| values.iter().cloned().fold(0.0f64, f64::max);

        doc.push(vec![
            title,
            Span::styled(sparkline(&previous, previous_width, max), Style::default().fg(palette::MUTED)),
            Span::styled("│", Style::default().fg(palette::RED)),
            Span::styled(sparkline(&current, current_width, max), Style::default().fg(palette::ORANGE)),
            Span::styled(format!("  peak {}, before {}", format_value(peak(&current), s.unit), format_value(peak(&previous), s.unit)),
                         Style::default().fg(palette::MUTED)),
        ]);
    }
}

fn sparkline(values: &[f64], width: usize, max: f64) -> String {
    if width == 0 || values.is_empty() {
        return String::new();
    }

    let columns: Vec<f64> = if values.len() <= width {
        values.to_vec()
    } else {
        (0..width).map(|i| {
            let start = i * values.len() / width;
            let end = ((i + 1) * values.len() / width).max(start + 1);
            values[start..end].iter().cloned().fold(0.0f64, f64::max)
        }).collect()
    };

    columns.iter().map(|v| {
        if max <= 0.0 { SPARKLINE[0] } else { SPARKLINE[((v / max) * 7.0).round().clamp(0.0, 7.0) as usize] }
    }).collect()
}

fn changes_section(doc: &mut Doc, changes: &[BriefingChangeGroupResponse], window: Option<&BriefingWindowResponse>, is_main: bool) {
    if changes.is_empty() {
        return;
    }

    doc.section("Changes");
    if let Some(window) = window {
        doc.text(2, &format!("Everything {}, compared to the {} before that.", window_phrase(window),
                             window.duration_ms.map(format_duration_ms).unwrap_or_else(|| "window".to_string())),
                 Style::default().fg(palette::FAINT));
    }

    let now = now_unix();
    let muted = Style::default().fg(palette::MUTED);

    for group in changes {
        let subsystem = match (&group.subsystem, is_main) {
            (Some(s), true) => format!("{}: ", subsystem_name(s)),
            _ => String::new(),
        };
        doc.blank();
        doc.push(vec![
            Span::raw("  "),
            Span::styled(format!("{}{}", subsystem, group.title.as_deref().unwrap_or("")),
                         Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD)),
        ]);

        let title_width = group.tiles.iter().map(|t| t.title.as_deref().unwrap_or("").chars().count()).max().unwrap_or(0);

        for tile in &group.tiles {
            let unit = tile.unit.as_deref();
            let current = tile.current.unwrap_or(0);

            let delta = match tile.previous {
                None => Span::raw(""),
                Some(previous) if previous == current => Span::styled("same as before", muted),
                Some(previous) => {
                    let diff = current - previous;
                    let color = match tile.higher_is_worse {
                        Some(true) if diff > 0 => Color::Red,
                        Some(true) => Color::Green,
                        Some(false) if diff > 0 => Color::Green,
                        Some(false) => Color::Red,
                        None => palette::TEXT,
                    };
                    Span::styled(format!("{}{} vs {} before", if diff > 0 { "+" } else { "" },
                                         format_value(diff as f64, unit), format_value(previous as f64, unit)),
                                 Style::default().fg(color))
                }
            };

            doc.push(vec![
                Span::styled(format!("  {:>8}  ", format_value(current as f64, unit)),
                             Style::default().fg(if current == 0 { palette::MUTED } else { palette::WHITE }).add_modifier(Modifier::BOLD)),
                Span::styled(format!("{:<title_width$}  ", tile.title.as_deref().unwrap_or(""), title_width = title_width),
                             Style::default().fg(palette::TEXT)),
                delta,
            ]);

            if !tile.breakdown.is_empty() {
                let entries: Vec<String> = tile.breakdown.iter().take(MAX_BREAKDOWN_ENTRIES)
                    .map(|e| format!("{} {}", e.label.as_deref().unwrap_or("?"), e.count.unwrap_or(0)))
                    .collect();
                let more = tile.breakdown.len().saturating_sub(MAX_BREAKDOWN_ENTRIES);
                doc.text(12, &format!("{}{}", entries.join(", "), if more > 0 { format!(", and {} more", more) } else { String::new() }), muted);
            }

            for sample in &tile.samples {
                let detail = sample.detail.as_deref().filter(|d| !d.is_empty()).map(|d| format!(" ({})", d)).unwrap_or_default();
                let age = sample.timestamp.as_deref().and_then(parse_iso8601_to_unix)
                    .map(|t| format!(", {} ago", format_age_seconds(now - t))).unwrap_or_default();
                let mut spans = vec![Span::raw(" ".repeat(12)), Span::styled("· ", Style::default().fg(palette::FAINT))];
                if let Some(severity) = sample.severity.as_deref() {
                    spans.push(severity_tag(Some(severity)));
                    spans.push(Span::raw(" "));
                }
                spans.push(Span::styled(sample.label.clone().unwrap_or_default(), Style::default().fg(palette::TEXT)));
                spans.push(Span::styled(format!("{}{}", detail, age), muted));
                doc.push(spans);
            }

            if !tile.samples.is_empty() && current > tile.samples.len() as i64 {
                doc.text(12, &format!("· and {} more", format_value((current - tile.samples.len() as i64) as f64, unit)), muted);
            }
        }
    }
}

fn wrap(text: &str, width: usize) -> Vec<String> {
    let mut rows = Vec::new();
    let mut row = String::new();

    for word in text.split_whitespace() {
        let word_len = word.chars().count();
        let row_len = row.chars().count();

        if row_len > 0 && row_len + 1 + word_len > width {
            rows.push(std::mem::take(&mut row));
        }

        if word_len > width {
            let mut chars = word.chars().peekable();
            while chars.peek().is_some() {
                if !row.is_empty() {
                    rows.push(std::mem::take(&mut row));
                }
                row = chars.by_ref().take(width).collect();
            }
            continue;
        }

        if !row.is_empty() {
            row.push(' ');
        }
        row.push_str(word);
    }

    if !row.is_empty() || rows.is_empty() {
        rows.push(row);
    }
    rows
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wraps_words() {
        assert_eq!(wrap("one two three four", 9), vec!["one two", "three", "four"]);
        assert_eq!(wrap("", 10), vec![""]);
        assert_eq!(wrap("abcdefghij", 4), vec!["abcd", "efgh", "ij"]);
    }

    #[test]
    fn resamples_sparklines() {
        assert_eq!(sparkline(&[0.0, 4.0, 8.0], 3, 8.0), "▁▅█");
        assert_eq!(sparkline(&[1.0, 8.0, 1.0, 1.0, 8.0, 1.0], 2, 8.0).chars().count(), 2);
        assert_eq!(sparkline(&[1.0, 8.0, 1.0, 1.0, 8.0, 1.0], 2, 8.0), "██");
        assert_eq!(sparkline(&[], 5, 1.0), "");
        assert_eq!(sparkline(&[0.0, 0.0], 2, 0.0), "▁▁");
    }

    #[test]
    fn anchored_modes_fix_the_window_start() {
        assert_eq!(query(WindowMode::LastVisit, None), ("LAST_VISIT", None));
        assert_eq!(query(WindowMode::LastSession, None), ("LAST_SESSION", None));

        let anchor = Anchor { from: "2026-11-03T08:00:00.000Z".to_string(), window: empty_window() };
        let (since, range) = query(WindowMode::LastVisit, Some(&anchor));
        assert_eq!(since, "TIME_RANGE");
        let range: serde_json::Value = serde_json::from_str(&range.unwrap()).unwrap();
        assert_eq!(range["type"], "absolute");
        assert_eq!(range["from"], "2026-11-03T08:00:00.000Z");
        assert!(range["to"].as_str().unwrap().ends_with('Z'));

        let (_, range) = query(WindowMode::Last7d, Some(&anchor));
        assert_eq!(range.unwrap(), r#"{"minutes":10080,"type":"relative"}"#);
    }

    #[test]
    fn window_modes_cycle() {
        assert_eq!(WindowMode::Last7d.next(), WindowMode::LastVisit);
        assert!(WindowMode::LastSession.is_anchored());
        assert!(!WindowMode::Last24h.is_anchored());
    }
}

#[cfg(test)]
mod document_tests {
    use super::*;

    const FIXTURE: &str = r#"{
        "generated_at": "2026-11-03T20:54:17.862Z",
        "window": {"mode": "LAST_VISIT", "from": "2026-11-03T08:00:00.000Z", "to": "2026-11-03T20:00:00.000Z",
                   "duration_ms": 43200000, "is_first_visit": false, "capped": false},
        "attention": [
            {"severity": "RED", "subsystem": null, "title": "Health indicator \"Tap Error\" is red", "text": "A tap reports processing errors.", "count": 1},
            {"severity": "ALERT", "subsystem": "ETHERNET", "title": "Network scan detected, 3 times",
             "text": "3 new alerts of this type. Latest: ARP Sweep from [192.168.0.76] against [184 hosts] detected, which is a rather long sentence that has to wrap.", "count": 3}
        ],
        "monitoring": {"taps_online": 1, "taps_total": 2,
            "taps": [{"name": "Tap 01", "online": true, "last_report": "2026-11-03T19:59:00.000Z", "location_name": "HQ", "active_alerts": 1,
                      "captures": [{"is_running": true, "dropping": true}, {"is_running": false, "dropping": false}]},
                     {"name": "Tap 02", "online": false, "last_report": null, "location_name": null, "active_alerts": 0, "captures": []}],
            "checks": [{"level": "WARN", "subsystem": null, "title": "1 of 2 taps reporting", "detail": "1 offline."}],
            "health_indicators": {"tap_error": "RED", "db_clock": "GREEN"}},
        "changes": [{"subsystem": "DOT11", "title": "Networks", "tiles": [
            {"title": "New access points", "current": 12, "previous": 7, "higher_is_worse": null, "unit": null,
             "breakdown": [{"label": "Foo", "count": 5}], "samples": [{"label": "AA:BB", "detail": "Foo", "severity": null, "timestamp": "2026-11-03T19:00:00.000Z"}]}]}],
        "activity": {"throughput": [{"bucket": "2026-11-03T07:00:00.000Z", "value": 1000.0}, {"bucket": "2026-11-03T09:00:00.000Z", "value": 4000000.0}]},
        "locations": [{"name": "HQ", "taps_online": 1, "taps_total": 2, "offline_taps": ["Tap 02"], "alert_count": 1,
                       "environment": {"condition": {"display_name": "Clear", "severity": 0}, "temperature": 20, "wind_speed": 7, "wind_gust": null, "alerts": ["Flood Warning"]}}]
    }"#;

    fn data() -> BriefingData {
        let briefing: BriefingResponse = serde_json::from_str(FIXTURE).unwrap();
        BriefingData {
            kind: BriefingType::Main,
            window: briefing.window.clone(),
            generated_at: briefing.generated_at.clone(),
            tenants: vec![TenantBriefing { tenant_name: "T1".into(), briefing }],
            skipped_tenants: 0,
            scope_label: "Org / T1".into(),
            units: UnitSystem::Metric,
        }
    }

    fn text(lines: &[Line<'static>]) -> Vec<String> {
        lines.iter().map(|l| l.spans.iter().map(|s| s.content.to_string()).collect::<String>()).collect()
    }

    #[test]
    fn document_has_every_section_in_page_order() {
        let rows = text(&build_document(&data(), 120));
        let position = |needle: &str| rows.iter().position(|r| r.contains(needle)).unwrap_or_else(|| panic!("missing: {}", needle));

        let attention = position("// NEEDS YOUR ATTENTION (2)");
        let health = position("// CLUSTER HEALTH");
        let coverage = position("// DETECTION COVERAGE");
        let taps = position("// TAPS (1 OF 2 ONLINE)");
        let locations = position("// LOCATIONS");
        let activity = position("// ACTIVITY");
        let changes = position("// CHANGES");
        assert!(attention < health && health < coverage && coverage < taps && taps < locations && locations < activity && activity < changes);

        assert!(rows.iter().any(|r| r.contains("RED     Health indicator \"Tap Error\" is red")));
        assert!(rows.iter().any(|r| r.contains("ALERT   [Ethernet] Network scan detected, 3 times")));
        assert!(rows.iter().any(|r| r.contains("1 green  ·  0 orange  ·  1 red")));
        assert!(rows.iter().any(|r| r.contains("ONLINE   Tap 01  HQ") && r.contains("1/2 captures running, dropping packets, 1 active alert")));
        assert!(rows.iter().any(|r| r.contains("OFFLINE  Tap 02  no location  ·  never reported, no captures")));
        assert!(rows.iter().any(|r| r.contains("HQ  1 of 2 taps online, 1 active alert")));
        assert!(rows.iter().any(|r| r.contains("weather: Clear, 20°C, light breeze (7 km/h)")));
        assert!(rows.iter().any(|r| r.contains("severe environmental alerts: Flood Warning")));
        assert!(rows.iter().any(|r| r.contains("Total tap throughput") && r.contains("peak 4.0 Mbit/s, before 1.0 kbit/s")));
        assert!(rows.iter().any(|r| r.contains("WiFi: Networks")));
        assert!(rows.iter().any(|r| r.contains("12  New access points  +5 vs 7 before")));
        assert!(rows.iter().any(|r| r.contains("Foo 5")));
        assert!(rows.iter().any(|r| r.contains("· AA:BB (Foo)")));
        assert!(rows.iter().any(|r| r.contains("· and 11 more")));
    }

    #[test]
    fn long_text_wraps_under_its_tag_and_stays_within_width() {
        let width = 60;
        let rows = text(&build_document(&data(), width));

        let start = rows.iter().position(|r| r.contains("3 new alerts of this type.")).unwrap();
        let wrapped: Vec<&String> = rows[start..].iter().take_while(|r| r.starts_with("          ")).collect();
        assert!(wrapped.len() >= 3);
        assert!(wrapped.iter().all(|r| r.chars().count() <= width));
        assert!(wrapped.last().unwrap().contains("has to wrap."));
    }

    #[test]
    fn several_tenants_get_their_own_heading_and_verdict() {
        let mut data = data();
        let second: BriefingResponse = serde_json::from_str(FIXTURE).unwrap();
        data.tenants.push(TenantBriefing { tenant_name: "T2".into(), briefing: second });

        let rows = text(&build_document(&data, 100));
        assert_eq!(rows.iter().filter(|r| r.starts_with("▌ T1 ") || r.starts_with("▌ T2 ")).count(), 2);
        assert_eq!(rows.iter().filter(|r| r.contains("need your attention.")).count(), 2);
        assert_eq!(aggregate_verdict(&data).0, VerdictTone::Red);
        assert!(aggregate_verdict(&data).1.starts_with("2 red health indicators and 6 detection alerts"));
    }

    #[test]
    fn empty_scope_says_so() {
        let mut data = data();
        data.tenants.clear();
        let rows = text(&build_document(&data, 80));
        assert!(rows.iter().any(|r| r.contains("No tenants in the selected scope.")));
    }
}
