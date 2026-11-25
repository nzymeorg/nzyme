//! Interactive terminal dashboard. The shell owns the terminal, draws the header and footer, and switches between
//! the start screen, the profile selector, the main menu, the settings screens and the views registered in `MENU`.
//! Scope, tap selection and refresh interval are chosen once in the shell, shared with every view through a
//! `Context`, and persisted into the active profile. The command takes no parameters beyond the connection.

mod common;
mod view;
mod scope;
mod prefs;
mod bssids;
mod nodes;
mod taps;
mod alerts;
mod locations;
mod briefing;
mod assets;

use std::collections::BTreeSet;
use std::io;
use std::sync::Arc;
use std::time::Duration;

use ratatui::backend::CrosstermBackend;
use ratatui::crossterm::{
    cursor::{Hide, Show},
    event::{self, Event, KeyCode, KeyEvent, KeyEventKind, KeyModifiers},
    execute,
    terminal::{disable_raw_mode, enable_raw_mode, EnterAlternateScreen, LeaveAlternateScreen},
};
use ratatui::layout::{Alignment, Constraint, Layout, Margin, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Clear, List, ListItem, ListState, Paragraph};
use ratatui::{Frame, Terminal};

use crate::api::client::{is_api_key, ApiClient, ClientConfig};
use crate::apps::api::gui::common::{
    centered_rect, cycle, fill_background, lock, palette, popup_panel, render_key_hints, render_options_popup,
    row_highlight_style, spinner_frame, OptionRow, REFRESH_PRESETS,
};
use crate::apps::api::gui::prefs::{Context, Preferences, TapCatalog, TapSelection};
use crate::apps::api::gui::scope::{Scope, ScopeTree};
use crate::apps::api::gui::view::{View, ViewAction};
use crate::arguments::ConnectionArgs;
use crate::exit_codes::{EX_CONFIG, EX_IOERR};
use crate::profiles::connection::{self, API_KEY_ENV};
use crate::profiles::file::{self as profiles_file, Profile, DEFAULT_PROFILE_NAME};
use crate::tools::{format_clock_utc, now_unix};

const UI_TICK: Duration = Duration::from_millis(250);

/// A view that can be opened from the main menu. Add new views here; the menu renders them grouped by category.
struct MenuEntry {
    category: &'static str,
    name: &'static str,
    open: MenuAction,
}

enum MenuAction {
    Home,
    View(fn(Arc<Context>) -> Box<dyn View>),
}

const MENU: &[MenuEntry] = &[
    MenuEntry { category: "Briefing", name: "Briefing", open: MenuAction::Home },
    MenuEntry { category: "Alerts", name: "Overview", open: MenuAction::View(|ctx| Box::new(alerts::AlertsView::new(ctx))) },
    MenuEntry { category: "Ethernet", name: "Asset lookup", open: MenuAction::View(|ctx| Box::new(assets::AssetsView::new(ctx))) },
    MenuEntry { category: "Locations", name: "Overview", open: MenuAction::View(|ctx| Box::new(locations::LocationsView::new(ctx))) },
    MenuEntry { category: "WiFi", name: "BSSIDs", open: MenuAction::View(|ctx| Box::new(bssids::BssidsView::new(ctx))) },
    MenuEntry { category: "System", name: "Nodes", open: MenuAction::View(|ctx| Box::new(nodes::NodesView::new(ctx))) },
    MenuEntry { category: "System", name: "Taps", open: MenuAction::View(|ctx| Box::new(taps::TapsView::new(ctx))) },
];

enum Screen {
    Start,
    /// Profile selector. `error` shows why the last connection attempt failed, if any.
    Profiles { selected: usize, error: Option<String> },
    Menu { selected: usize },
    View { entry: usize, view: Box<dyn View> },
    /// Settings hub: refresh interval, and entry points to the scope and tap selectors.
    Settings { selected: usize },
    ScopeSelector { list: ListState },
    /// The tap selector edits a working copy and applies it on Enter.
    TapSelector { list: ListState, working: TapSelection },
}

/// The active connection. Views hold the context, so switching profiles drops all open views.
struct Session {
    ctx: Arc<Context>,
    /// Profile name, or `ENV` when the key came from the environment.
    label: String,
    /// Profile the preferences are persisted to. None when connected through the environment.
    profile_name: Option<String>,
    home: briefing::BriefingView,
}

impl Session {
    fn new(client: ApiClient, label: String, profile_name: Option<String>, prefs: Preferences) -> Self {
        let ctx = Context::new(Arc::new(client), prefs);
        ctx.discover();
        let home = briefing::BriefingView::new(Arc::clone(&ctx));
        Session { ctx, label, profile_name, home }
    }
}

struct Shell {
    args: ConnectionArgs,
    profiles: Vec<(String, Profile)>,
    session: Option<Session>,
    screen: Screen,
    /// Where to go back to when a settings screen closes.
    return_to: Option<Screen>,
    /// Outcome of the last attempt to persist preferences, shown in the settings screens.
    last_save: Option<Result<String, String>>,
}

pub fn run(connection: &ConnectionArgs) {
    let profiles: Vec<(String, Profile)> = profiles_file::load().ok().flatten()
        .map(|f| f.profiles.into_iter().collect())
        .unwrap_or_default();

    // Resolve the connection like every other command does. If that fails but there are profiles to pick from
    // and the user did not ask for a specific server or profile, start on the profile selector instead.
    let explicit = connection.server.is_some() || connection.profile.is_some();
    let from_env = std::env::var(API_KEY_ENV).map(|k| !k.trim().is_empty()).unwrap_or(false);

    let (session, screen) = match connection::resolve(connection).and_then(|r| ApiClient::new(r.config).map_err(|e| e.to_string())) {
        Ok(client) => {
            let profile_name = if from_env { None } else {
                Some(connection.profile.clone().unwrap_or_else(|| DEFAULT_PROFILE_NAME.to_string()))
            };
            let label = profile_name.clone().unwrap_or_else(|| "ENV".to_string());
            let prefs = profile_name.as_ref()
                .and_then(|name| profiles.iter().find(|(n, _)| n == name))
                .map(|(_, p)| Preferences::from_gui_settings(p.gui.as_ref()))
                .unwrap_or_default();
            (Some(Session::new(client, label, profile_name, prefs)), Screen::Start)
        }
        Err(_) if !explicit && !profiles.is_empty() => (None, Screen::Profiles {
            selected: 0,
            error: Some("No default profile configured. Select a profile to connect.".to_string()),
        }),
        Err(e) => {
            eprintln!("\x1b[31m[x] ERROR:\x1b[0m {}", e);
            std::process::exit(EX_CONFIG);
        }
    };

    let mut shell = Shell { args: connection.clone(), profiles, session, screen, return_to: None, last_save: None };
    shell.enter_current();

    if let Err(e) = run_shell(&mut shell) {
        eprintln!("Terminal error: {}", e);
        std::process::exit(EX_IOERR);
    }
}

impl Shell {
    /// Connects with a stored profile. Command line `--insecure` and `--ca-file` still apply on top of it.
    fn connect_profile(&mut self, index: usize) -> Result<(), String> {
        let (name, profile) = self.profiles.get(index).cloned().ok_or_else(|| "No such profile.".to_string())?;

        if !is_api_key(&profile.api_key) {
            return Err(format!("Profile [{}] does not contain a valid API key.", name));
        }

        let client = ApiClient::new(ClientConfig {
            server: profile.server.clone(),
            api_key: profile.api_key.clone(),
            insecure: profile.insecure || self.args.insecure,
            ca_file: self.args.ca_file.clone().or_else(|| profile.ca_file.clone()).map(std::path::PathBuf::from),
        }).map_err(|e| e.to_string())?;

        // Drop any open view before replacing the context it was created with.
        self.leave_current();
        self.return_to = None;

        let prefs = Preferences::from_gui_settings(profile.gui.as_ref());
        self.session = Some(Session::new(client, name.clone(), Some(name.clone()), prefs));
        self.last_save = None;
        Ok(())
    }

    fn ctx(&self) -> Option<&Arc<Context>> {
        self.session.as_ref().map(|s| &s.ctx)
    }

    /// Applies a user's preference change and persists it into the active profile.
    fn update_prefs(&mut self, change: impl FnOnce(&mut Preferences)) {
        let Some(session) = &self.session else { return; };
        change(&mut lock(&session.ctx.prefs));
        *lock(&session.ctx.notice) = None;
        self.persist_prefs();
    }

    /// Persists the current preferences into the active profile.
    fn persist_prefs(&mut self) {
        let Some(session) = &self.session else { return; };
        let prefs = session.ctx.prefs();

        self.last_save = Some(match &session.profile_name {
            Some(name) => match profiles_file::save_gui_settings(name, prefs.to_gui_settings()) {
                Ok(true) => {
                    // Keep the in-memory profile list in sync for the selector.
                    if let Some((_, p)) = self.profiles.iter_mut().find(|(n, _)| n == name) {
                        p.gui = Some(prefs.to_gui_settings());
                    }
                    Ok(format!("Saved to profile {}.", name.to_uppercase()))
                }
                Ok(false) => Err(format!("Profile {} no longer exists in the profiles file. Settings apply to this session only.", name.to_uppercase())),
                Err(e) => Err(format!("Could not save settings: {}", e)),
            },
            None => Err("Connected through the environment. Settings apply to this session only; use a profile to persist them.".to_string()),
        });
    }

    fn leave_current(&mut self) {
        match &mut self.screen {
            Screen::View { view, .. } => view.on_leave(),
            Screen::Start => if let Some(session) = &mut self.session { session.home.on_leave() },
            _ => {}
        }
    }

    fn enter_current(&mut self) {
        match &mut self.screen {
            Screen::View { view, .. } => view.on_enter(),
            Screen::Start => if let Some(session) = &mut self.session { session.home.on_enter() },
            _ => {}
        }
    }

    fn switch_to(&mut self, next: Screen) {
        self.leave_current();
        self.screen = next;
        self.enter_current();
    }

    /// Opens a settings screen and remembers where to return to.
    fn open(&mut self, screen: Screen) {
        self.leave_current();
        if !matches!(self.screen, Screen::Settings { .. } | Screen::ScopeSelector { .. } | Screen::TapSelector { .. }) {
            self.return_to = Some(std::mem::replace(&mut self.screen, Screen::Start));
        }
        self.screen = screen;
    }

    fn close_settings(&mut self) {
        let target = self.return_to.take().unwrap_or(Screen::Start);
        self.screen = target;
        self.enter_current();
    }
}

fn run_shell(shell: &mut Shell) -> io::Result<()> {
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

    let res = (|| -> io::Result<()> {
        loop {
            terminal.draw(|f| draw(f, shell))?;

            // Housekeeping: persist corrections made by discovery, and rediscover periodically.
            if let Some(ctx) = shell.session.as_ref().map(|s| Arc::clone(&s.ctx)) {
                if ctx.take_persist_request() {
                    shell.persist_prefs();
                }
                if ctx.discovery_due() {
                    ctx.discover();
                }
            }

            if !event::poll(UI_TICK)? {
                continue;
            }

            let Event::Key(key) = event::read()? else { continue; };
            if key.kind != KeyEventKind::Press {
                continue;
            }

            if key.code == KeyCode::Char('c') && key.modifiers.contains(KeyModifiers::CONTROL) {
                break;
            }

            if key.code == KeyCode::Char('r') && matches!(shell.screen, Screen::View { .. } | Screen::Start) {
                if let Some(ctx) = shell.ctx() {
                    ctx.discover();
                }
            }

            // Global shortcut into the settings hub from the start screen, the menu and the views.
            if key.code == KeyCode::Char('s') && shell.session.is_some()
                && matches!(shell.screen, Screen::Start | Screen::Menu { .. } | Screen::View { .. }) {
                shell.open(Screen::Settings { selected: 0 });
                continue;
            }

            let next = match &mut shell.screen {
                Screen::Start => handle_start_key(key, shell.session.as_mut()),
                Screen::Profiles { selected, .. } => {
                    let selected = *selected;
                    handle_profiles_key(key, selected, shell)
                }
                Screen::Menu { selected } => {
                    let ctx = shell.session.as_ref().map(|s| Arc::clone(&s.ctx));
                    handle_menu_key(key, selected, ctx)
                }
                Screen::View { view, .. } => match view.handle_key(key) {
                    ViewAction::Continue => None,
                    ViewAction::Back => {
                        view.on_leave();
                        Some(Transition::To(Screen::Menu { selected: current_entry(&shell.screen) }))
                    }
                    ViewAction::Quit => Some(Transition::Quit),
                },
                Screen::Settings { selected } => {
                    let selected = *selected;
                    handle_settings_key(key, selected, shell);
                    None
                }
                Screen::ScopeSelector { .. } => { handle_scope_key(key, shell); None }
                Screen::TapSelector { .. } => { handle_taps_key(key, shell); None }
            };

            match next {
                Some(Transition::Quit) => break,
                Some(Transition::To(next)) => shell.switch_to(next),
                None => {}
            }
        }
        Ok(())
    })();

    shell.leave_current();

    disable_raw_mode()?;
    execute!(terminal.backend_mut(), LeaveAlternateScreen, Show)?;
    terminal.show_cursor()?;
    res
}

enum Transition {
    To(Screen),
    Quit,
}

fn current_entry(screen: &Screen) -> usize {
    match screen {
        Screen::View { entry, .. } => *entry,
        Screen::Menu { selected } => *selected,
        _ => 0,
    }
}

fn handle_start_key(key: KeyEvent, session: Option<&mut Session>) -> Option<Transition> {
    let Some(session) = session else {
        return Some(Transition::To(Screen::Profiles { selected: 0, error: None }));
    };

    if key.code == KeyCode::Char('p') {
        return Some(Transition::To(Screen::Profiles { selected: 0, error: None }));
    }

    match session.home.handle_key(key) {
        ViewAction::Continue => None,
        ViewAction::Back => Some(Transition::To(Screen::Menu { selected: 0 })),
        ViewAction::Quit => Some(Transition::Quit),
    }
}

fn handle_profiles_key(key: KeyEvent, selected: usize, shell: &mut Shell) -> Option<Transition> {
    let n = shell.profiles.len();

    match key.code {
        KeyCode::Char('q') => Some(Transition::Quit),
        KeyCode::Esc if shell.session.is_some() => Some(Transition::To(Screen::Start)),
        KeyCode::Esc => None,
        KeyCode::Up | KeyCode::Char('k') => Some(Transition::To(Screen::Profiles { selected: selected.saturating_sub(1), error: None })),
        KeyCode::Down | KeyCode::Char('j') => Some(Transition::To(Screen::Profiles { selected: (selected + 1).min(n.saturating_sub(1)), error: None })),
        KeyCode::Enter | KeyCode::Char(' ') if n > 0 => match shell.connect_profile(selected) {
            Ok(()) => Some(Transition::To(Screen::Start)),
            Err(e) => Some(Transition::To(Screen::Profiles { selected, error: Some(e) })),
        },
        _ => None,
    }
}

fn handle_menu_key(key: KeyEvent, selected: &mut usize, ctx: Option<Arc<Context>>) -> Option<Transition> {
    let Some(ctx) = ctx else { return Some(Transition::To(Screen::Start)); };

    match key.code {
        KeyCode::Char('q') => Some(Transition::Quit),
        KeyCode::Esc => Some(Transition::To(Screen::Start)),
        KeyCode::Up | KeyCode::Char('k') => { *selected = selected.saturating_sub(1); None }
        KeyCode::Down | KeyCode::Char('j') => { *selected = (*selected + 1).min(MENU.len() - 1); None }
        KeyCode::Home | KeyCode::Char('g') => { *selected = 0; None }
        KeyCode::End | KeyCode::Char('G') => { *selected = MENU.len() - 1; None }
        KeyCode::Enter | KeyCode::Char(' ') | KeyCode::Right | KeyCode::Char('l') => {
            let entry = *selected;
            match MENU[entry].open {
                MenuAction::Home => Some(Transition::To(Screen::Start)),
                MenuAction::View(open) => Some(Transition::To(Screen::View { entry, view: open(ctx) })),
            }
        }
        _ => None,
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Settings hub and selectors
// ---------------------------------------------------------------------------------------------------------------

const SETTING_REFRESH: usize = 0;
const SETTING_SCOPE: usize = 1;
const SETTING_TAPS: usize = 2;
const SETTING_COUNT: usize = 3;

fn handle_settings_key(key: KeyEvent, selected: usize, shell: &mut Shell) {
    match key.code {
        KeyCode::Esc | KeyCode::Char('q') | KeyCode::Char('s') => shell.close_settings(),
        KeyCode::Up | KeyCode::Char('k') => shell.screen = Screen::Settings { selected: selected.saturating_sub(1) },
        KeyCode::Down | KeyCode::Char('j') => shell.screen = Screen::Settings { selected: (selected + 1).min(SETTING_COUNT - 1) },
        KeyCode::Left | KeyCode::Char('h') if selected == SETTING_REFRESH => adjust_refresh(shell, -1),
        KeyCode::Right | KeyCode::Char('l') if selected == SETTING_REFRESH => adjust_refresh(shell, 1),
        KeyCode::Enter | KeyCode::Char(' ') | KeyCode::Right | KeyCode::Char('l') => match selected {
            SETTING_REFRESH => adjust_refresh(shell, 1),
            SETTING_SCOPE => open_scope_selector(shell),
            SETTING_TAPS => open_tap_selector(shell),
            _ => {}
        },
        _ => {}
    }
}

fn adjust_refresh(shell: &mut Shell, direction: i32) {
    shell.update_prefs(|p| p.refresh_seconds = cycle(REFRESH_PRESETS, p.refresh_seconds, direction));
}

/// Flat list of selectable scopes: everything, then each organization followed by its tenants.
fn scope_entries(tree: &ScopeTree) -> Vec<(Scope, String, u8)> {
    let mut entries = vec![(Scope::all(), "All organizations and tenants".to_string(), 0u8)];
    if let ScopeTree::Available(orgs) = tree {
        for org in orgs {
            entries.push((Scope::organization(&org.id), format!("{}  (all {} tenants)", org.name, org.tenants.len()), 1));
            for tenant in &org.tenants {
                entries.push((Scope::tenant(&org.id, &tenant.id), tenant.name.clone(), 2));
            }
        }
    }
    entries
}

fn open_scope_selector(shell: &mut Shell) {
    let Some(ctx) = shell.ctx() else { return; };
    ctx.discover();
    let tree = lock(&ctx.scope_tree).clone();
    let current = ctx.prefs().scope;

    let position = scope_entries(&tree).iter().position(|(s, _, _)| *s == current).unwrap_or(0);
    let mut list = ListState::default();
    list.select(Some(position));
    shell.open(Screen::ScopeSelector { list });
}

fn handle_scope_key(key: KeyEvent, shell: &mut Shell) {
    let Some(ctx) = shell.ctx() else { shell.close_settings(); return; };
    let entries = scope_entries(&lock(&ctx.scope_tree));
    let Screen::ScopeSelector { list } = &mut shell.screen else { return; };
    let cursor = list.selected().unwrap_or(0);

    match key.code {
        KeyCode::Esc | KeyCode::Char('q') => shell.screen = Screen::Settings { selected: SETTING_SCOPE },
        KeyCode::Up | KeyCode::Char('k') => list.select(Some(cursor.saturating_sub(1))),
        KeyCode::Down | KeyCode::Char('j') => list.select(Some((cursor + 1).min(entries.len().saturating_sub(1)))),
        KeyCode::Home | KeyCode::Char('g') => list.select(Some(0)),
        KeyCode::End | KeyCode::Char('G') => list.select(Some(entries.len().saturating_sub(1))),
        KeyCode::Enter | KeyCode::Char(' ') => {
            if let Some((scope, _, _)) = entries.get(cursor).cloned() {
                shell.update_prefs(|p| {
                    if p.scope != scope {
                        p.scope = scope;
                        // Selected taps may fall outside the new scope.
                        p.taps = TapSelection::All;
                    }
                });
            }
            shell.screen = Screen::Settings { selected: SETTING_SCOPE };
        }
        _ => {}
    }
}

fn open_tap_selector(shell: &mut Shell) {
    let Some(ctx) = shell.ctx() else { return; };
    ctx.discover();
    let working = ctx.prefs().taps;
    let mut list = ListState::default();
    list.select(Some(0));
    shell.open(Screen::TapSelector { list, working });
}

fn handle_taps_key(key: KeyEvent, shell: &mut Shell) {
    let Some(ctx) = shell.ctx() else { shell.close_settings(); return; };
    let taps = ctx.taps_in_scope();
    let Screen::TapSelector { list, working } = &mut shell.screen else { return; };

    // Row 0 is "All taps", rows 1.. are the taps in scope.
    let rows = taps.len() + 1;
    let cursor = list.selected().unwrap_or(0);

    match key.code {
        KeyCode::Esc | KeyCode::Char('q') => shell.screen = Screen::Settings { selected: SETTING_TAPS },
        KeyCode::Up | KeyCode::Char('k') => list.select(Some(cursor.saturating_sub(1))),
        KeyCode::Down | KeyCode::Char('j') => list.select(Some((cursor + 1).min(rows - 1))),
        KeyCode::Home | KeyCode::Char('g') => list.select(Some(0)),
        KeyCode::End | KeyCode::Char('G') => list.select(Some(rows - 1)),
        KeyCode::Char('a') => *working = TapSelection::All,
        KeyCode::Char(' ') => {
            if cursor == 0 {
                *working = TapSelection::All;
            } else if let Some(tap) = taps.get(cursor - 1) {
                let mut selected = match working {
                    TapSelection::All => BTreeSet::new(),
                    TapSelection::Selected(s) => s.clone(),
                };
                if !selected.remove(&tap.uuid) {
                    selected.insert(tap.uuid.clone());
                }
                *working = if selected.is_empty() { TapSelection::All } else { TapSelection::Selected(selected) };
            }
        }
        KeyCode::Enter => {
            let working = working.clone();
            shell.update_prefs(|p| p.taps = working);
            shell.screen = Screen::Settings { selected: SETTING_TAPS };
        }
        _ => {}
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------------------------------------------

fn draw(f: &mut Frame, shell: &mut Shell) {
    fill_background(f);

    let chunks = Layout::vertical([Constraint::Length(1), Constraint::Min(5), Constraint::Length(1)]).split(f.area());

    let server = shell.session.as_ref().map(|s| s.ctx.client.base_url().to_string()).unwrap_or_else(|| "NOT CONNECTED".to_string());
    let profile = shell.session.as_ref().map(|s| s.label.clone()).unwrap_or_default();
    let context_label = shell.ctx().map(|ctx| {
        let prefs = ctx.prefs();
        format!("{} · {}{}", prefs.scope.label(&lock(&ctx.scope_tree)), prefs.taps.label(&lock(&ctx.taps)),
                if ctx.is_discovering() { format!(" {}", spinner_frame()) } else { String::new() })
    }).unwrap_or_default();

    let (title, status): (String, Vec<Span<'static>>) = match &shell.screen {
        Screen::Start => match &shell.session {
            Some(session) => (session.home.title(), session.home.header_status()),
            None => ("NOT CONNECTED".to_string(), Vec::new()),
        },
        Screen::Profiles { .. } => ("PROFILES".to_string(), Vec::new()),
        Screen::Menu { .. } => ("MENU".to_string(), Vec::new()),
        Screen::Settings { .. } => ("SETTINGS".to_string(), Vec::new()),
        Screen::ScopeSelector { .. } => ("SETTINGS // SCOPE".to_string(), Vec::new()),
        Screen::TapSelector { .. } => ("SETTINGS // TAPS".to_string(), Vec::new()),
        Screen::View { entry, view } => (format!("{} // {}", MENU[*entry].category, MENU[*entry].name).to_uppercase(), view.header_status()),
    };
    render_header(f, chunks[0], &server, &profile, &context_label, &title, status);

    match &mut shell.screen {
        Screen::Start => {
            if let Some(session) = &mut shell.session {
                session.home.draw(f, chunks[1]);
                let mut keys = session.home.footer_keys();
                keys.extend([("s", "settings"), ("p", "profiles")]);
                render_key_hints(f, chunks[2], &keys);
            } else {
                render_key_hints(f, chunks[2], &[("p", "profiles"), ("q", "quit")]);
            }
        }
        Screen::Profiles { selected, error } => {
            render_profiles(f, chunks[1], &shell.profiles, *selected, error.as_deref(), shell.session.as_ref().map(|s| s.label.as_str()));
            render_key_hints(f, chunks[2], &[("↑/↓", "select"), ("Enter", "connect"), ("Esc", "back"), ("q", "quit")]);
        }
        Screen::Menu { selected } => {
            render_menu(f, chunks[1], *selected);
            render_key_hints(f, chunks[2], &[("↑/↓", "select"), ("Enter", "open"), ("s", "settings"), ("Esc", "standby"), ("q", "quit")]);
        }
        Screen::View { view, .. } => {
            view.draw(f, chunks[1]);
            let mut keys = view.footer_keys();
            keys.push(("s", "settings"));
            render_key_hints(f, chunks[2], &keys);
        }
        Screen::Settings { selected } => {
            let selected = *selected;
            if let Some(session) = &shell.session {
                render_settings(f, chunks[1], session, selected, shell.last_save.as_ref());
            }
            render_key_hints(f, chunks[2], &[("↑/↓", "select"), ("←/→", "change"), ("Enter", "open"), ("Esc", "close")]);
        }
        Screen::ScopeSelector { list } => {
            if let Some(ctx) = shell.session.as_ref().map(|s| &s.ctx) {
                render_scope_selector(f, chunks[1], ctx, list);
            }
            render_key_hints(f, chunks[2], &[("↑/↓", "select"), ("Enter", "apply"), ("Esc", "back")]);
        }
        Screen::TapSelector { list, working } => {
            if let Some(ctx) = shell.session.as_ref().map(|s| &s.ctx) {
                render_tap_selector(f, chunks[1], ctx, list, working);
            }
            render_key_hints(f, chunks[2], &[("↑/↓", "move"), ("Space", "toggle"), ("a", "all taps"), ("Enter", "apply"), ("Esc", "cancel")]);
        }
    }
}

fn render_header(f: &mut Frame, area: Rect, server: &str, profile: &str, context: &str, title: &str, status: Vec<Span<'static>>) {
    let mut spans = vec![
        Span::styled("▐", Style::default().fg(palette::RED)),
        Span::styled("NZYME", Style::default().fg(palette::WHITE).bg(palette::RED).add_modifier(Modifier::BOLD)),
        Span::styled("▌", Style::default().fg(palette::RED)),
        Span::styled(" ░▒▓ ", Style::default().fg(palette::RED_DIM)),
        Span::styled(server.to_string(), Style::default().fg(palette::MUTED)),
        Span::styled(if profile.is_empty() { String::new() } else { format!(" [{}]", profile.to_uppercase()) }, Style::default().fg(palette::FAINT)),
    ];

    if !context.is_empty() {
        spans.push(Span::styled("  │  ", Style::default().fg(palette::FAINT)));
        spans.push(Span::styled(context.to_string(), Style::default().fg(palette::MUTED)));
    }

    spans.push(Span::styled("  ▸ ", Style::default().fg(palette::RED_DIM)));
    spans.push(Span::styled(title.to_string(), Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD)));

    if !status.is_empty() {
        spans.push(Span::styled("  │  ", Style::default().fg(palette::FAINT)));
        spans.extend(status);
    }

    // Right-aligned UTC clock.
    let clock = format!("{} UTC ", format_clock_utc(now_unix()));
    let used: usize = spans.iter().map(|s| s.content.chars().count()).sum();
    let pad = (area.width as usize).saturating_sub(used + clock.chars().count()).max(1);
    spans.push(Span::raw(" ".repeat(pad)));
    spans.push(Span::styled(clock, Style::default().fg(palette::MUTED)));

    f.render_widget(Paragraph::new(Line::from(spans)).style(Style::default().bg(palette::BG)), area);
}


fn render_menu(f: &mut Frame, area: Rect, selected: usize) {
    let categories = MENU.iter().map(|e| e.category).collect::<BTreeSet<_>>().len() as u16;
    let height = (MENU.len() as u16 + categories * 2 + 3).min(area.height);
    let popup = centered_rect(area.width.saturating_sub(4).min(60), height, area);

    let block = popup_panel("Main Menu");
    let inner = block.inner(popup);
    f.render_widget(block, popup);

    let category_style = Style::default().fg(palette::RED).add_modifier(Modifier::BOLD);
    let rule_style = Style::default().fg(palette::RED_DIM);
    let index_style = Style::default().fg(palette::FAINT);
    let item_style = Style::default().fg(palette::TEXT);
    let selected_style = row_highlight_style();

    let width = inner.width.saturating_sub(2) as usize;
    let mut lines: Vec<Line> = vec![Line::raw("")];
    let mut last_category: Option<&str> = None;

    for (i, entry) in MENU.iter().enumerate() {
        if last_category != Some(entry.category) {
            if last_category.is_some() {
                lines.push(Line::raw(""));
            }
            let label = format!(" // {} ", entry.category.to_uppercase());
            lines.push(Line::from(vec![
                Span::styled(label.clone(), category_style),
                Span::styled("─".repeat(width.saturating_sub(label.chars().count())), rule_style),
            ]));
            last_category = Some(entry.category);
        }

        let is_selected = i == selected;
        let marker = if is_selected { "▶" } else { " " };
        let body = format!(" {} {:02}  {}", marker, i + 1, entry.name.to_uppercase());
        if is_selected {
            lines.push(Line::from(Span::styled(format!("{:<width$}", body, width = width), selected_style)));
        } else {
            lines.push(Line::from(vec![
                Span::styled(format!(" {} ", marker), item_style),
                Span::styled(format!("{:02}  ", i + 1), index_style),
                Span::styled(entry.name.to_uppercase(), item_style),
            ]));
        }
    }

    f.render_widget(Paragraph::new(lines), inner.inner(Margin { vertical: 0, horizontal: 1 }));
}

fn render_profiles(f: &mut Frame, area: Rect, profiles: &[(String, Profile)], selected: usize, error: Option<&str>, active: Option<&str>) {
    let height = (profiles.len() as u16 + 7).min(area.height);
    let popup = centered_rect(area.width.saturating_sub(4).min(96), height, area);
    f.render_widget(Clear, popup);

    let block = popup_panel("Profiles");
    let inner = block.inner(popup);
    f.render_widget(block, popup);

    let selected_style = row_highlight_style();
    let muted = Style::default().fg(palette::MUTED);
    let faint = Style::default().fg(palette::FAINT);
    let width = inner.width.saturating_sub(2) as usize;

    let mut lines: Vec<Line> = vec![Line::raw("")];

    if profiles.is_empty() {
        lines.push(Line::styled("  No profiles stored. Create one with: nzyme-util api profiles add --server <URL>", muted));
    }

    for (i, (name, profile)) in profiles.iter().enumerate() {
        let is_selected = i == selected;
        let is_active = active == Some(name.as_str());
        let marker = if is_selected { "▶" } else { " " };
        let flags = format!("{}{}",
            if profile.insecure { " INSECURE" } else { "" },
            if is_active { " ● ACTIVE" } else { "" });
        let body = format!(" {} {:02}  {:<16} {:<38} {}{}", marker, i + 1, name.to_uppercase(), profile.server, short_mask(&profile.api_key), flags);

        if is_selected {
            lines.push(Line::from(Span::styled(format!("{:<width$}", body, width = width), selected_style)));
        } else {
            lines.push(Line::from(vec![
                Span::styled(format!(" {} ", marker), Style::default().fg(palette::TEXT)),
                Span::styled(format!("{:02}  ", i + 1), faint),
                Span::styled(format!("{:<16} ", name.to_uppercase()), Style::default().fg(palette::TEXT)),
                Span::styled(format!("{:<38} ", profile.server), muted),
                Span::styled(short_mask(&profile.api_key), faint),
                Span::styled(flags, Style::default().fg(if is_active { Color::Green } else { palette::ORANGE })),
            ]));
        }
    }

    lines.push(Line::raw(""));
    match error {
        Some(e) => lines.push(Line::from(vec![
            Span::styled(" ▲ ", Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
            Span::styled(e.to_string(), Style::default().fg(Color::Red)),
        ])),
        None => lines.push(Line::styled(" Command line: nzyme-util api --profile <NAME> gui", faint)),
    }

    f.render_widget(Paragraph::new(lines).wrap(ratatui::widgets::Wrap { trim: false }), inner.inner(Margin { vertical: 0, horizontal: 1 }));
}

fn render_settings(f: &mut Frame, area: Rect, session: &Session, selected: usize, last_save: Option<&Result<String, String>>) {
    let ctx = &session.ctx;
    let prefs = ctx.prefs();
    let tree = lock(&ctx.scope_tree).clone();
    let catalog = lock(&ctx.taps).clone();

    let scope_value = match &tree {
        ScopeTree::Pending => format!("{} discovering...", spinner_frame()),
        ScopeTree::Unavailable(_) => "unavailable".to_string(),
        ScopeTree::Available(_) => format!("{}  ▶", prefs.scope.label(&tree)),
    };
    let taps_value = match &catalog {
        TapCatalog::Pending => format!("{} discovering...", spinner_frame()),
        TapCatalog::Unavailable(_) => "unavailable".to_string(),
        TapCatalog::Available(_) => format!("{}  ▶", prefs.taps.label(&catalog)),
    };

    let rows = [
        OptionRow { label: "Refresh interval".to_string(), value: format!("{} s", prefs.refresh_seconds), cyclable: true },
        OptionRow { label: "Scope".to_string(), value: scope_value, cyclable: false },
        OptionRow { label: "Taps".to_string(), value: taps_value, cyclable: false },
    ];

    let notice = lock(&ctx.notice).clone();
    let hint = match (&tree, last_save) {
        (ScopeTree::Unavailable(message), _) => message.clone(),
        _ if notice.is_some() => notice.unwrap_or_default(),
        (_, Some(Ok(message))) => message.clone(),
        (_, Some(Err(message))) => message.clone(),
        (_, None) => match &session.profile_name {
            Some(name) => format!("Changes apply to all views immediately and are saved to profile {}.", name.to_uppercase()),
            None => "Changes apply to all views immediately. Connected through the environment, so they are not saved.".to_string(),
        },
    };

    render_options_popup(f, area, "Settings", &rows, selected, &hint);
}

fn render_scope_selector(f: &mut Frame, area: Rect, ctx: &Arc<Context>, list: &mut ListState) {
    let tree = lock(&ctx.scope_tree).clone();
    let current = ctx.prefs().scope;
    let entries = scope_entries(&tree);

    let height = (entries.len() as u16 + 5).clamp(7, area.height.saturating_sub(2).max(7));
    let popup = centered_rect(80, height, area);
    f.render_widget(Clear, popup);
    let block = popup_panel("Select scope");

    if !matches!(tree, ScopeTree::Available(_)) {
        let message = match &tree {
            ScopeTree::Pending => format!("{} Discovering organizations and tenants...", spinner_frame()),
            ScopeTree::Unavailable(m) => m.clone(),
            _ => String::new(),
        };
        f.render_widget(Paragraph::new(Span::styled(message, Style::default().fg(palette::MUTED)))
            .block(block).alignment(Alignment::Center).wrap(ratatui::widgets::Wrap { trim: true }), popup);
        return;
    }

    let items: Vec<ListItem> = entries.iter().map(|(scope, label, depth)| {
        let marker = if *scope == current { "● " } else { "  " };
        let indent = "    ".repeat(*depth as usize);
        let style = match depth {
            0 => Style::default().fg(palette::TEXT).add_modifier(Modifier::BOLD),
            1 => Style::default().fg(palette::ORANGE),
            _ => Style::default().fg(palette::TEXT),
        };
        ListItem::new(Line::from(vec![
            Span::styled(marker, Style::default().fg(Color::Green)),
            Span::raw(indent),
            Span::styled(label.clone(), style),
        ]))
    }).collect();

    let widget = List::new(items).block(block).highlight_style(row_highlight_style()).highlight_symbol("▶ ");
    f.render_stateful_widget(widget, popup, list);
}

fn render_tap_selector(f: &mut Frame, area: Rect, ctx: &Arc<Context>, list: &mut ListState, working: &TapSelection) {
    let catalog = lock(&ctx.taps).clone();
    let taps = ctx.taps_in_scope();

    let height = (taps.len() as u16 + 5).clamp(7, area.height.saturating_sub(2).max(7));
    let popup = centered_rect(96, height, area);
    f.render_widget(Clear, popup);
    let block = popup_panel("Select taps");

    if !matches!(catalog, TapCatalog::Available(_)) {
        let message = match &catalog {
            TapCatalog::Pending => format!("{} Discovering taps...", spinner_frame()),
            TapCatalog::Unavailable(m) => m.clone(),
            _ => String::new(),
        };
        f.render_widget(Paragraph::new(Span::styled(message, Style::default().fg(palette::MUTED)))
            .block(block).alignment(Alignment::Center).wrap(ratatui::widgets::Wrap { trim: true }), popup);
        return;
    }

    let checked = |on: bool| if on { "[x] " } else { "[ ] " };

    let mut items: Vec<ListItem> = Vec::with_capacity(taps.len() + 1);
    items.push(ListItem::new(Line::from(vec![
        Span::raw(checked(*working == TapSelection::All)),
        Span::styled(format!("All taps in scope ({})", taps.len()), Style::default().add_modifier(Modifier::BOLD)),
    ])));

    for tap in &taps {
        let on = match working {
            TapSelection::All => false,
            TapSelection::Selected(s) => s.contains(&tap.uuid),
        };
        let status = match tap.online {
            Some(true) => Span::styled(format!("{:<8}", "online"), Style::default().fg(Color::Green)),
            Some(false) => Span::styled(format!("{:<8}", "offline"), Style::default().fg(Color::Red)),
            None => Span::raw(format!("{:<8}", "")),
        };
        items.push(ListItem::new(Line::from(vec![
            Span::raw(checked(on)),
            Span::raw(format!("{:<28}", tap.name)),
            status,
            Span::styled(format!("  {}", tap.tenant_name), Style::default().fg(palette::MUTED)),
        ])));
    }

    let widget = List::new(items).block(block).highlight_style(row_highlight_style()).highlight_symbol("▶ ");
    f.render_stateful_widget(widget, popup, list);
}

/// `nzk_…p6mk`: enough to recognize a key, short enough for a table row.
fn short_mask(key: &str) -> String {
    if key.len() <= 8 {
        return "•".repeat(key.len());
    }
    format!("{}…{}", &key[..4], &key[key.len() - 4..])
}
