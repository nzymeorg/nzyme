use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use ratatui::crossterm::event::{KeyCode, KeyEvent, KeyModifiers};
use ratatui::layout::{Alignment, Constraint, Layout, Margin, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Clear, Paragraph, Scrollbar, ScrollbarOrientation, ScrollbarState, Wrap};
use ratatui::Frame;

use crate::apps::api::assets::doc::{self, Section};
use crate::apps::api::assets::labels::normalize_mac;
use crate::apps::api::assets::profile::{self, AssetProfile, LookupScope, ProfileOptions};
use crate::apps::api::gui::common::{
    centered_rect, lock, palette, panel, panel_title, popup_panel, spinner_frame, ScrollableTable,
};
use crate::apps::api::gui::prefs::Context;
use crate::apps::api::gui::view::{View, ViewAction};
use crate::tools::now_unix;

const DISCOVERY_WAIT: Duration = Duration::from_secs(20);
const WINDOW_PRESETS: &[u32] = &[1440, 10_080, 43_200, 129_600];
const MAX_MATCHES: usize = 5;
const HEADER_HEIGHT: u16 = 4;

type LoadResult = Result<Vec<AssetProfile>, String>;

enum Phase {
    Prompt,
    Loading { mac: String, started: Instant, result: Arc<Mutex<Option<LoadResult>>> },
    Loaded { profiles: Vec<AssetProfile>, selected: usize },
}

pub struct AssetsView {
    ctx: Arc<Context>,
    phase: Phase,
    input: String,
    error: Option<String>,
    section: usize,
    scroll: usize,
    visible_rows: usize,
    line_count: usize,
    window_minutes: u32,
}

impl AssetsView {
    pub fn new(ctx: Arc<Context>) -> Self {
        AssetsView {
            ctx, phase: Phase::Prompt, input: String::new(), error: None, section: 0, scroll: 0,
            visible_rows: 1, line_count: 0, window_minutes: WINDOW_PRESETS[1],
        }
    }

    fn start_lookup(&mut self) {
        let Some(mac) = normalize_mac(&self.input) else {
            self.error = Some("That is not a MAC address. Use six hex pairs like 94:83:C4:CE:D3:B0.".to_string());
            return;
        };

        self.error = None;
        self.section = 0;
        self.scroll = 0;

        let result = Arc::new(Mutex::new(None));
        let ctx = Arc::clone(&self.ctx);
        let window = self.window_minutes;
        let shared = Arc::clone(&result);
        let lookup_mac = mac.clone();
        thread::spawn(move || {
            let outcome = load(&ctx, &lookup_mac, window);
            *lock(&shared) = Some(outcome);
        });

        self.phase = Phase::Loading { mac, started: Instant::now(), result };
    }

    fn poll_loading(&mut self) {
        let Phase::Loading { result, .. } = &self.phase else { return; };
        let outcome = lock(result).take();
        match outcome {
            None => {}
            Some(Ok(profiles)) => self.phase = Phase::Loaded { profiles, selected: 0 },
            Some(Err(e)) => {
                self.error = Some(e);
                self.phase = Phase::Prompt;
            }
        }
    }

    fn scroll_by(&mut self, delta: i64) {
        let max = self.line_count.saturating_sub(self.visible_rows) as i64;
        self.scroll = (self.scroll as i64 + delta).clamp(0, max) as usize;
    }

    fn set_section(&mut self, index: usize) {
        if index < Section::ALL.len() && index != self.section {
            self.section = index;
            self.scroll = 0;
        }
    }

    fn cycle_window(&mut self) {
        let index = WINDOW_PRESETS.iter().position(|w| *w == self.window_minutes).unwrap_or(1);
        self.window_minutes = WINDOW_PRESETS[(index + 1) % WINDOW_PRESETS.len()];
        if matches!(self.phase, Phase::Loaded { .. }) {
            self.start_lookup();
        }
    }
}

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

fn load(ctx: &Context, mac: &str, window_minutes: u32) -> LoadResult {
    let pairs = wait_for_pairs(ctx)?;
    let scopes: Vec<LookupScope> = pairs.into_iter().map(|p| LookupScope {
        organization_id: p.organization_id, organization_name: None, tenant_id: p.tenant_id, tenant_name: Some(p.tenant_name),
    }).collect();

    if scopes.is_empty() {
        return Err("No tenants in the selected scope.".to_string());
    }

    let located = profile::lookup(&ctx.client, &scopes, mac).map_err(|e| e.to_string())?;
    if located.is_empty() {
        return Err(format!("No asset with MAC address {} in {}.", mac, ctx.prefs().scope.label(&lock(&ctx.scope_tree))));
    }

    let options = ProfileOptions::last_minutes(window_minutes);
    Ok(located.into_iter().take(MAX_MATCHES).map(|asset| profile::load(&ctx.client, asset, &options)).collect())
}

impl View for AssetsView {
    fn on_enter(&mut self) {}

    fn on_leave(&mut self) {}

    fn draw(&mut self, f: &mut Frame, area: Rect) {
        self.poll_loading();

        match &self.phase {
            Phase::Prompt => render_prompt(f, area, &self.input, self.error.as_deref(), self.window_minutes),
            Phase::Loading { mac, started, .. } => render_loading(f, area, mac, started.elapsed()),
            Phase::Loaded { profiles, selected } => {
                let Some(profile) = profiles.get(*selected) else { return; };
                let now = now_unix();

                let chunks = Layout::vertical([Constraint::Length(HEADER_HEIGHT), Constraint::Length(1), Constraint::Min(5)]).split(area);
                render_header(f, chunks[0], profile, profiles.len(), *selected, now);
                render_tabs(f, chunks[1], profile, self.section);

                let section = Section::ALL[self.section];
                let block = panel().title_top(panel_title(section.title()));
                let inner = block.inner(chunks[2]).inner(Margin { vertical: 0, horizontal: 1 });
                f.render_widget(block, chunks[2]);

                let lines = doc::build(profile, section, inner.width.saturating_sub(1) as usize, now);
                self.line_count = lines.len();
                self.visible_rows = inner.height as usize;
                self.scroll = self.scroll.min(self.line_count.saturating_sub(self.visible_rows));

                let visible: Vec<Line> = lines.into_iter().skip(self.scroll).take(self.visible_rows).collect();
                f.render_widget(Paragraph::new(visible), inner);

                if self.line_count > self.visible_rows {
                    let mut state = ScrollbarState::new(self.line_count.saturating_sub(self.visible_rows))
                        .position(self.scroll)
                        .viewport_content_length(self.visible_rows);
                    f.render_stateful_widget(
                        Scrollbar::new(ScrollbarOrientation::VerticalRight)
                            .begin_symbol(Some("▲")).end_symbol(Some("▼"))
                            .thumb_style(Style::default().fg(palette::RED))
                            .track_style(Style::default().fg(palette::FAINT)),
                        chunks[2].inner(Margin { vertical: 1, horizontal: 0 }),
                        &mut state,
                    );
                }
            }
        }
    }

    fn handle_key(&mut self, key: KeyEvent) -> ViewAction {
        match &mut self.phase {
            Phase::Prompt => match key.code {
                KeyCode::Esc => ViewAction::Back,
                KeyCode::Enter => { self.start_lookup(); ViewAction::Continue }
                KeyCode::Backspace => { self.input.pop(); self.error = None; ViewAction::Continue }
                KeyCode::Char('u') if key.modifiers.contains(KeyModifiers::CONTROL) => { self.input.clear(); ViewAction::Continue }
                KeyCode::Char(c) if c.is_ascii_hexdigit() || matches!(c, ':' | '-' | '.') => {
                    if self.input.chars().count() < 17 {
                        self.input.push(c.to_ascii_uppercase());
                    }
                    ViewAction::Continue
                }
                KeyCode::Tab => { self.cycle_window(); ViewAction::Continue }
                _ => ViewAction::Continue,
            },

            Phase::Loading { .. } => match key.code {
                KeyCode::Esc | KeyCode::Char('q') => { self.phase = Phase::Prompt; ViewAction::Continue }
                _ => ViewAction::Continue,
            },

            Phase::Loaded { profiles, selected } => {
                let page = self.visible_rows.max(1) as i64;
                match key.code {
                    KeyCode::Char('q') => return ViewAction::Back,
                    KeyCode::Esc | KeyCode::Char('m') => { self.phase = Phase::Prompt; return ViewAction::Continue; }
                    KeyCode::Char('r') => { self.start_lookup(); return ViewAction::Continue; }
                    KeyCode::Char('w') => { self.cycle_window(); return ViewAction::Continue; }
                    KeyCode::Char('n') if profiles.len() > 1 => {
                        *selected = (*selected + 1) % profiles.len();
                        self.scroll = 0;
                        return ViewAction::Continue;
                    }
                    KeyCode::Tab | KeyCode::Right | KeyCode::Char('l') => { let next = (self.section + 1) % Section::ALL.len(); self.set_section(next); }
                    KeyCode::BackTab | KeyCode::Left | KeyCode::Char('h') => { let next = (self.section + Section::ALL.len() - 1) % Section::ALL.len(); self.set_section(next); }
                    KeyCode::Char(c) if c.is_ascii_digit() && c != '0' => { self.set_section((c as u8 - b'1') as usize); }
                    KeyCode::Up | KeyCode::Char('k') => self.scroll_by(-1),
                    KeyCode::Down | KeyCode::Char('j') => self.scroll_by(1),
                    KeyCode::PageUp => self.scroll_by(-page),
                    KeyCode::PageDown | KeyCode::Char(' ') => self.scroll_by(page),
                    KeyCode::Home | KeyCode::Char('g') => self.scroll = 0,
                    KeyCode::End | KeyCode::Char('G') => self.scroll_by(i64::MAX / 2),
                    _ => {}
                }
                ViewAction::Continue
            }
        }
    }

    fn footer_keys(&self) -> Vec<(&'static str, &'static str)> {
        match &self.phase {
            Phase::Prompt => vec![("Enter", "look up"), ("Tab", "time window"), ("Esc", "menu")],
            Phase::Loading { .. } => vec![("Esc", "cancel")],
            Phase::Loaded { profiles, .. } => {
                let mut keys = vec![("Tab 1-8", "section"), ("↑/↓", "scroll")];
                keys.extend(ScrollableTable::KEYS.iter().skip(1).copied());
                if profiles.len() > 1 {
                    keys.push(("n", "next match"));
                }
                keys.extend([("w", "time window"), ("r", "reload"), ("m", "new lookup"), ("q", "menu")]);
                keys
            }
        }
    }

    fn header_status(&self) -> Vec<Span<'static>> {
        match &self.phase {
            Phase::Prompt => vec![Span::styled(format!("window {}", profile::describe_minutes(self.window_minutes)), Style::default().fg(palette::MUTED))],
            Phase::Loading { mac, .. } => vec![
                Span::styled(format!("{} LOADING ", spinner_frame()), Style::default().fg(palette::ORANGE)),
                Span::styled(mac.clone(), Style::default().fg(palette::MUTED)),
            ],
            Phase::Loaded { profiles, selected } => {
                let mut spans = vec![
                    Span::styled("● LOADED", Style::default().fg(Color::Green).add_modifier(Modifier::BOLD)),
                    Span::styled(format!(" {}", profile::describe_minutes(self.window_minutes)), Style::default().fg(palette::MUTED)),
                ];
                if let Some(warning) = profiles.get(*selected).and_then(doc::load_warning) {
                    spans.push(Span::styled(format!("  ▲ {}", warning), Style::default().fg(palette::ORANGE)));
                }
                spans
            }
        }
    }
}

fn render_prompt(f: &mut Frame, area: Rect, input: &str, error: Option<&str>, window_minutes: u32) {
    let popup = centered_rect(area.width.saturating_sub(4).min(72), 9, area);
    f.render_widget(Clear, popup);
    let block = popup_panel("Ethernet asset lookup");
    let inner = block.inner(popup);
    f.render_widget(block, popup);

    let cursor = if crate::apps::api::gui::common::blink_on() { "▌" } else { " " };
    let lines = vec![
        Line::raw(""),
        Line::from(vec![
            Span::styled("  MAC ADDRESS  ", Style::default().fg(palette::MUTED)),
            Span::styled(format!(" {:<17}", input), Style::default().fg(palette::WHITE).bg(palette::RED_GLOW).add_modifier(Modifier::BOLD)),
            Span::styled(cursor, Style::default().fg(palette::RED).bg(palette::RED_GLOW)),
        ]),
        Line::raw(""),
        Line::from(vec![
            Span::styled("  Time window  ", Style::default().fg(palette::MUTED)),
            Span::styled(profile::describe_minutes(window_minutes), Style::default().fg(palette::TEXT)),
            Span::styled("  (Tab to change)", Style::default().fg(palette::FAINT)),
        ]),
        Line::raw(""),
        match error {
            Some(e) => Line::from(vec![
                Span::styled(" ▲ ", Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
                Span::styled(e.to_string(), Style::default().fg(Color::Red)),
            ]),
            None => Line::styled("  Looks in every tenant of the current scope. Colons, dashes and dots are all fine.", Style::default().fg(palette::FAINT)),
        },
    ];
    f.render_widget(Paragraph::new(lines).wrap(Wrap { trim: false }), inner.inner(Margin { vertical: 0, horizontal: 1 }));
}

fn render_loading(f: &mut Frame, area: Rect, mac: &str, elapsed: Duration) {
    let popup = centered_rect(60, 5, area);
    f.render_widget(Clear, popup);
    let block = popup_panel("Ethernet asset lookup");
    let text = format!("{} LOADING {}  {}s", spinner_frame(), mac, elapsed.as_secs());
    f.render_widget(Paragraph::new(Span::styled(text, Style::default().fg(palette::ORANGE))).block(block).alignment(Alignment::Center), popup);
}

fn render_header(f: &mut Frame, area: Rect, profile: &AssetProfile, matches: usize, selected: usize, now: i64) {
    let block = panel();
    let inner = block.inner(area);
    f.render_widget(block, area);

    let mut lines = doc::header(profile, inner.width as usize, now);
    if matches > 1 {
        lines.push(Line::from(Span::styled(
            format!("    match {} of {} with this MAC address, n for the next", selected + 1, matches),
            Style::default().fg(palette::ORANGE),
        )));
    }
    f.render_widget(Paragraph::new(lines), inner);
}

fn render_tabs(f: &mut Frame, area: Rect, profile: &AssetProfile, current: usize) {
    let mut spans = vec![Span::raw(" ")];
    for (i, section) in Section::ALL.iter().enumerate() {
        let active = i == current;
        let badge = doc::badge(profile, *section).map(|b| format!(" {}", b)).unwrap_or_default();
        let label = format!(" {} {}{} ", i + 1, section.title().to_uppercase(), badge);
        spans.push(Span::styled(label, if active {
            Style::default().fg(palette::WHITE).bg(palette::RED).add_modifier(Modifier::BOLD)
        } else {
            Style::default().fg(palette::MUTED)
        }));
        spans.push(Span::raw(" "));
    }
    f.render_widget(Paragraph::new(Line::from(spans)).style(Style::default().bg(palette::BG)), area);
}
