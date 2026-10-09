//! Cyberdeck theme shared by every terminal application in nzyme-util: brand palette, panel frames, key hint
//! footer, spinner and blink helpers. Applications compose these so they all look like one tool.

use std::sync::{Mutex, MutexGuard};

use ratatui::layout::Rect;
use ratatui::style::{Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, BorderType, Borders, Paragraph};
use ratatui::Frame;

/// Cyberdeck theme on the Nzyme brand colors. The red is the logo and nzyme.org accent, the orange is the
/// secondary accent of the web interface. Everything else is a dark, low-contrast backdrop so the red glows.
pub mod palette {
    use ratatui::style::Color;

    pub const RED: Color = Color::Rgb(0xff, 0x14, 0x3f);
    pub const ORANGE: Color = Color::Rgb(0xf5, 0x73, 0x28);

    /// Dimmed red for inactive frame lines.
    pub const RED_DIM: Color = Color::Rgb(0x8a, 0x0e, 0x26);
    /// Selection glow.
    pub const RED_GLOW: Color = Color::Rgb(0x4a, 0x08, 0x18);

    pub const BG: Color = Color::Rgb(0x08, 0x09, 0x0c);
    pub const PANEL: Color = Color::Rgb(0x0e, 0x10, 0x15);
    pub const TEXT: Color = Color::Rgb(0xc8, 0xcb, 0xd2);
    pub const MUTED: Color = Color::Rgb(0x6c, 0x71, 0x7c);
    pub const FAINT: Color = Color::Rgb(0x3a, 0x3e, 0x48);
    pub const WHITE: Color = Color::Rgb(0xf4, 0xf4, 0xf6);
}

pub const SPINNER: &[&str] = &["⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"];

/// Spinner frame derived from wall time so every widget animates in sync without a shared tick.
pub fn spinner_frame() -> &'static str {
    let millis = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis())
        .unwrap_or(0);
    SPINNER[(millis / 100) as usize % SPINNER.len()]
}

/// True on alternating half seconds, for blinking elements.
pub fn blink_on() -> bool {
    let millis = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis())
        .unwrap_or(0);
    (millis / 500) % 2 == 0
}

/// A segmented progress bar like `▰▰▰▱▱▱▱▱`.
pub fn segment_bar(fraction: f64, width: usize) -> String {
    let filled = (fraction.clamp(0.0, 1.0) * width as f64).round() as usize;
    format!("{}{}", "▰".repeat(filled), "▱".repeat(width.saturating_sub(filled)))
}

/// Paints the whole frame with the theme background.
pub fn fill_background(f: &mut Frame) {
    f.render_widget(Block::default().style(Style::default().bg(palette::BG).fg(palette::TEXT)), f.area());
}

pub fn lock<T>(m: &Mutex<T>) -> MutexGuard<'_, T> {
    m.lock().unwrap_or_else(|e| e.into_inner())
}

pub fn centered_rect(width: u16, height: u16, area: Rect) -> Rect {
    let width = width.min(area.width);
    let height = height.min(area.height);
    Rect {
        x: area.x + (area.width - width) / 2,
        y: area.y + (area.height - height) / 2,
        width,
        height,
    }
}

/// Steps through a list of presets, clamping at both ends.
pub fn cycle<T: Copy + PartialEq>(presets: &[T], current: T, direction: i32) -> T {
    let index = presets.iter().position(|p| *p == current).unwrap_or(0) as i32;
    let next = (index + direction).clamp(0, presets.len() as i32 - 1) as usize;
    presets[next]
}

/// Renders a footer line of keycaps like `[Q] QUIT`.
pub fn render_key_hints(f: &mut Frame, area: Rect, keys: &[(&str, &str)]) {
    let bracket = Style::default().fg(palette::RED_DIM);
    let key = Style::default().fg(palette::RED).add_modifier(Modifier::BOLD);
    let text = Style::default().fg(palette::MUTED);

    let mut spans = vec![Span::styled(" ", text)];
    for (k, t) in keys {
        spans.push(Span::styled("[", bracket));
        spans.push(Span::styled((*k).to_string(), key));
        spans.push(Span::styled("]", bracket));
        spans.push(Span::styled(format!(" {}   ", t.to_uppercase()), text));
    }

    f.render_widget(Paragraph::new(Line::from(spans)).style(Style::default().bg(palette::BG)), area);
}

/// Renders a centered message inside a bordered block, for loading and empty states.
pub fn render_message(f: &mut Frame, area: Rect, block: Block, text: &str) {
    let text = if text == "Loading..." { format!("{} {}", spinner_frame(), text.to_uppercase()) } else { text.to_string() };
    f.render_widget(
        Paragraph::new(Span::styled(text, Style::default().fg(palette::MUTED)))
            .block(block)
            .alignment(ratatui::layout::Alignment::Center),
        area,
    );
}

/// A themed panel: thick dim-red frame, uppercase title in a bright red tab.
pub fn titled_block(title: String) -> Block<'static> {
    panel().title(panel_title(&title))
}

pub fn panel() -> Block<'static> {
    Block::default()
        .borders(Borders::ALL)
        .border_type(BorderType::Thick)
        .border_style(Style::default().fg(palette::RED_DIM))
        .style(Style::default().bg(palette::PANEL).fg(palette::TEXT))
}

/// A popup panel: brighter frame so it stands out from the panels behind it.
pub fn popup_panel(title: &str) -> Block<'static> {
    Block::default()
        .borders(Borders::ALL)
        .border_type(BorderType::Double)
        .border_style(Style::default().fg(palette::RED))
        .style(Style::default().bg(palette::PANEL).fg(palette::TEXT))
        .title(panel_title(title))
}

pub fn panel_title(title: &str) -> Line<'static> {
    Line::from(vec![
        Span::styled("▌", Style::default().fg(palette::RED)),
        Span::styled(
            format!(" {} ", title.trim().to_uppercase()),
            Style::default().fg(palette::WHITE).bg(palette::RED_GLOW).add_modifier(Modifier::BOLD),
        ),
        Span::styled("▐", Style::default().fg(palette::RED)),
    ])
}

pub fn row_highlight_style() -> Style {
    Style::default().bg(palette::RED_GLOW).fg(palette::WHITE).add_modifier(Modifier::BOLD)
}

pub fn table_header_style() -> Style {
    Style::default().fg(palette::RED).add_modifier(Modifier::BOLD)
}

/// Builds a table header cell: uppercase, red, bold.
pub fn header_cell(text: &str) -> ratatui::widgets::Cell<'static> {
    ratatui::widgets::Cell::from(Span::styled(text.to_uppercase(), table_header_style()))
}

pub const HIGHLIGHT_SYMBOL: &str = "▶ ";
