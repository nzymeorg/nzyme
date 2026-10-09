//! `nzyme-util tap top`: a top-like live view of what a tap is doing, fed by the tap's loopback telemetry
//! socket. The same screen shows a live feed or, with `--replay`, a recording of one.

mod protocol;
mod source;
mod state;
mod ui;

use std::fs::File;
use std::io;
use std::time::{Duration, Instant};

use ratatui::backend::CrosstermBackend;
use ratatui::crossterm::{
    cursor::{Hide, Show},
    event::{self, Event, KeyCode, KeyEvent, KeyEventKind, KeyModifiers},
    execute,
    terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen},
};
use ratatui::Terminal;

use crate::apps::tap::source::{LinkState, LiveSource, ReplaySource, SourceEvent};
use crate::apps::tap::state::TopState;
use crate::exit_codes::{EX_DATAERR, EX_IOERR};

/// Redraw interval. Short, so LEDs and flashes feel immediate.
const UI_TICK: Duration = Duration::from_millis(40);
const LOG_HEIGHTS: &[u16] = &[8, 14, 0];
const SEEK_STEP_MS: f64 = 10_000.0;
const SEEK_STEP_LARGE_MS: f64 = 60_000.0;

pub enum Mode {
    Live(LiveSource),
    Replay(ReplaySource),
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Page {
    Overview,
    Leader,
    Detail,
    Log,
}

impl Page {
    const ORDER: [Page; 4] = [Page::Overview, Page::Leader, Page::Detail, Page::Log];

    pub fn label(&self) -> &'static str {
        match self {
            Page::Overview => "OVERVIEW",
            Page::Leader => "LEADER // STATE",
            Page::Detail => "GAUGES // TIMERS",
            Page::Log => "LOG",
        }
    }

    fn index(&self) -> usize {
        Self::ORDER.iter().position(|p| p == self).unwrap_or(0)
    }

    fn next(&self) -> Page {
        Self::ORDER[(self.index() + 1) % Self::ORDER.len()]
    }

    fn previous(&self) -> Page {
        Self::ORDER[(self.index() + Self::ORDER.len() - 1) % Self::ORDER.len()]
    }
}

pub struct App {
    pub mode: Mode,
    pub state: TopState,
    pub link: LinkState,
    pub page: Page,
    /// Live only: stop applying frames so the screen holds still. Frames that arrive meanwhile are dropped.
    pub frozen: bool,
    log_height_index: usize,
    pub channel_scroll: usize,
    pub leader_scroll: usize,
    pub detail_scroll: usize,
    pub log_scroll: usize,
    pub hide_idle: bool,
    pub bad_lines: usize,
}

enum Action {
    Continue,
    Quit,
}

pub fn run_top(address: String, record: Option<String>) {
    let record = match record {
        Some(path) => match File::create(&path) {
            Ok(file) => Some((path, file)),
            Err(e) => {
                eprintln!("\x1b[31m[x] ERROR:\x1b[0m Could not create recording file [{}]: {}", path, e);
                std::process::exit(EX_IOERR);
            }
        },
        None => None,
    };

    let source = LiveSource::start(address, record);
    let app = App::new(Mode::Live(source));
    run(app);
}

pub fn run_replay(file: String, speed: f64) {
    let speed = if speed > 0.0 { speed } else { 1.0 };
    let replay = match ReplaySource::load(&file, speed) {
        Ok(replay) => replay,
        Err(e) => {
            eprintln!("\x1b[31m[x] ERROR:\x1b[0m {}", e);
            std::process::exit(EX_DATAERR);
        }
    };

    let app = App::new(Mode::Replay(replay));
    run(app);
}

fn run(mut app: App) {
    if let Err(e) = run_terminal(&mut app) {
        eprintln!("Terminal error: {}", e);
        std::process::exit(EX_IOERR);
    }

    if let Mode::Live(source) = &app.mode {
        if let Some(path) = &source.recording {
            println!("Recording written to [{}]. Play it back with: nzyme-util tap top --replay {}", path, path);
        }
    }
}

impl App {
    fn new(mode: Mode) -> Self {
        App {
            mode,
            state: TopState::new(),
            link: LinkState::Connecting { attempt: 0, last_error: None },
            page: Page::Overview,
            frozen: false,
            log_height_index: 0,
            channel_scroll: 0,
            leader_scroll: 0,
            detail_scroll: 0,
            log_scroll: 0,
            hide_idle: false,
            bad_lines: 0,
        }
    }

    pub fn log_height(&self) -> u16 {
        LOG_HEIGHTS[self.log_height_index]
    }

    /// Pulls everything that arrived since the last redraw into the state.
    fn ingest(&mut self, now: Instant) {
        let App { mode, state, link, frozen, bad_lines, .. } = self;

        match mode {
            Mode::Live(source) => {
                for event in source.drain() {
                    match event {
                        SourceEvent::Link(new_link) => {
                            if matches!(new_link, LinkState::Connected { .. }) {
                                *bad_lines = 0;
                            }
                            *link = new_link;
                        }
                        SourceEvent::Frame(frame) => {
                            if !*frozen {
                                state.apply(frame, now);
                            }
                        }
                        SourceEvent::BadLine => *bad_lines += 1,
                    }
                }
            }
            Mode::Replay(replay) => {
                if !matches!(link, LinkState::Connected { .. }) {
                    *link = LinkState::Connected { since: now };
                }
                for frame in replay.advance(now) {
                    state.apply(frame, now);
                }
            }
        }
    }

    /// Rebuilds the state from the start of a recording up to the new position. Frames are applied with an
    /// old wall time so nothing lights up as if it just happened.
    fn rebuild_from(&mut self, frames: Vec<protocol::Frame>, now: Instant) {
        let past = now.checked_sub(Duration::from_secs(3600)).unwrap_or(now);
        self.state = TopState::new();
        for frame in frames {
            self.state.apply(frame, past);
        }
    }

    fn handle_key(&mut self, key: KeyEvent, now: Instant) -> Action {
        if key.code == KeyCode::Char('c') && key.modifiers.contains(KeyModifiers::CONTROL) {
            return Action::Quit;
        }

        match key.code {
            KeyCode::Char('q') | KeyCode::Esc => return Action::Quit,
            KeyCode::Tab => self.page = self.page.next(),
            KeyCode::BackTab => self.page = self.page.previous(),
            KeyCode::Char('1') => self.page = Page::Overview,
            KeyCode::Char('2') => self.page = Page::Leader,
            KeyCode::Char('3') => self.page = Page::Detail,
            KeyCode::Char('4') => self.page = Page::Log,
            KeyCode::Char('l') => self.log_height_index = (self.log_height_index + 1) % LOG_HEIGHTS.len(),
            KeyCode::Char('z') => {
                self.hide_idle = !self.hide_idle;
                self.channel_scroll = 0;
            }
            KeyCode::Up | KeyCode::Char('k') => self.scroll(-1),
            KeyCode::Down | KeyCode::Char('j') => self.scroll(1),
            KeyCode::PageUp => self.scroll(-10),
            KeyCode::PageDown => self.scroll(10),
            KeyCode::Home | KeyCode::Char('g') => self.scroll(i64::MIN / 2),
            KeyCode::End | KeyCode::Char('G') => self.scroll(i64::MAX / 2),
            _ => {}
        }

        match &mut self.mode {
            Mode::Live(_) => match key.code {
                KeyCode::Char('p') | KeyCode::Char(' ') => self.frozen = !self.frozen,
                _ => {}
            },
            Mode::Replay(replay) => match key.code {
                KeyCode::Char('p') | KeyCode::Char(' ') => replay.toggle_pause(),
                KeyCode::Char('+') | KeyCode::Char('=') | KeyCode::Char('.') | KeyCode::Char('>') => replay.change_speed(1),
                KeyCode::Char('-') | KeyCode::Char(',') | KeyCode::Char('<') => replay.change_speed(-1),
                KeyCode::Left => {
                    let step = if key.modifiers.contains(KeyModifiers::SHIFT) { SEEK_STEP_LARGE_MS } else { SEEK_STEP_MS };
                    let frames = replay.seek_by(-step);
                    self.rebuild_from(frames, now);
                }
                KeyCode::Right => {
                    let step = if key.modifiers.contains(KeyModifiers::SHIFT) { SEEK_STEP_LARGE_MS } else { SEEK_STEP_MS };
                    let frames = replay.seek_by(step);
                    self.rebuild_from(frames, now);
                }
                KeyCode::Char('r') => {
                    let frames = replay.seek_to(0.0);
                    self.rebuild_from(frames, now);
                }
                _ => {}
            },
        }

        Action::Continue
    }

    fn scroll(&mut self, delta: i64) {
        let apply = |current: usize, max: usize| -> usize {
            (current as i64).saturating_add(delta).clamp(0, max.saturating_sub(1) as i64) as usize
        };

        match self.page {
            Page::Overview => {
                let now = Instant::now();
                let visible = self.state.channels.iter().filter(|c| !self.hide_idle || !c.is_idle(now)).count();
                let buses = self.state.channels.iter().map(|c| c.bus.as_str()).collect::<std::collections::BTreeSet<_>>().len();
                self.channel_scroll = apply(self.channel_scroll, visible + buses);
            }
            Page::Leader => {
                let rows = self.state.links.len().max(self.state.cycle.tables.len()).max(self.state.gauges.len());
                self.leader_scroll = apply(self.leader_scroll, rows);
            }
            Page::Detail => {
                let rows = self.state.gauges.len().max(self.state.timers.len());
                self.detail_scroll = apply(self.detail_scroll, rows);
            }
            Page::Log => {
                // The offset counts from the newest line, so scrolling up (negative) increases it.
                let max = self.state.logs.len();
                self.log_scroll = (self.log_scroll as i64).saturating_sub(delta).clamp(0, max.saturating_sub(1) as i64) as usize;
            }
        }
    }

    pub fn footer_keys(&self) -> Vec<(&'static str, &'static str)> {
        let mut keys: Vec<(&'static str, &'static str)> = vec![("Tab 1-4", "page"), ("↑/↓", "scroll")];
        if self.page == Page::Overview {
            keys.push(("l", "log size"));
            keys.push(("z", if self.hide_idle { "show idle" } else { "hide idle" }));
        }
        match &self.mode {
            Mode::Live(_) => keys.push(("p", if self.frozen { "resume" } else { "freeze" })),
            Mode::Replay(replay) => {
                keys.push(("Space", if replay.paused { "play" } else { "pause" }));
                keys.push(("+/-", "speed"));
                keys.push(("←/→", "seek 10s"));
                keys.push(("r", "restart"));
            }
        }
        keys.push(("q", "quit"));
        keys
    }
}

fn run_terminal(app: &mut App) -> io::Result<()> {
    enable_raw_mode()?;
    let mut stdout = io::stdout();
    execute!(stdout, EnterAlternateScreen, Hide)?;

    // Restore the terminal even if something panics.
    let prev = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        let _ = disable_raw_mode();
        let _ = execute!(io::stdout(), LeaveAlternateScreen, Show);
        prev(info);
    }));

    let backend = CrosstermBackend::new(stdout);
    let mut terminal = Terminal::new(backend)?;

    let result = (|| -> io::Result<()> {
        loop {
            let now = Instant::now();
            app.ingest(now);
            terminal.draw(|f| ui::draw(f, app, now))?;

            if !event::poll(UI_TICK)? {
                continue;
            }

            let Event::Key(key) = event::read()? else { continue; };
            if key.kind != KeyEventKind::Press {
                continue;
            }

            if let Action::Quit = app.handle_key(key, Instant::now()) {
                break;
            }
        }
        Ok(())
    })();

    disable_raw_mode()?;
    execute!(terminal.backend_mut(), LeaveAlternateScreen, Show)?;
    terminal.show_cursor()?;
    result
}
