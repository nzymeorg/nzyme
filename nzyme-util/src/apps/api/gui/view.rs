//! The contract between the dashboard shell and its views. A view owns its own data fetching and rendering. The
//! shell draws the header line and the footer with the view's key hints, and routes keys.

use ratatui::crossterm::event::KeyEvent;
use ratatui::layout::Rect;
use ratatui::Frame;

pub enum ViewAction {
    /// Keep showing the view.
    Continue,
    /// Return to the main menu.
    Back,
    /// Quit the application. No current view uses it: q returns to the menu and quitting happens there.
    #[allow(dead_code)]
    Quit,
}

pub trait View {
    /// Called every time the view becomes visible. Views start or resume their data fetching here.
    fn on_enter(&mut self);

    /// Called when the shell switches away from the view. Views pause their data fetching here.
    fn on_leave(&mut self);

    /// Draws the view into `area`. The shell has already drawn the header above and draws the footer below.
    fn draw(&mut self, f: &mut Frame, area: Rect);

    /// Handles a key press. Ctrl-C is handled by the shell and never reaches the view.
    fn handle_key(&mut self, key: KeyEvent) -> ViewAction;

    /// Key hints for the footer, as (key, description) pairs.
    fn footer_keys(&self) -> Vec<(&'static str, &'static str)>;

    /// Status text for the right side of the header line, for example refresh state and active settings.
    fn header_status(&self) -> Vec<ratatui::text::Span<'static>>;
}
