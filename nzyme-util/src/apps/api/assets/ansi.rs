use ratatui::style::{Color, Modifier, Style};
use ratatui::text::Line;

pub fn line_to_ansi(line: &Line<'_>, color: bool) -> String {
    let mut spans: Vec<(String, Style)> = line.spans.iter().map(|s| (s.content.to_string(), s.style)).collect();
    while let Some((content, _)) = spans.last_mut() {
        let trimmed = content.trim_end().to_string();
        if trimmed.is_empty() {
            spans.pop();
        } else {
            *content = trimmed;
            break;
        }
    }

    let mut out = String::new();
    for (content, style) in spans {
        if color {
            out.push_str(&style_prefix(&style));
            out.push_str(&content);
            out.push_str("\x1b[0m");
        } else {
            out.push_str(&content);
        }
    }
    out
}

fn style_prefix(style: &Style) -> String {
    let mut codes: Vec<String> = Vec::new();
    if style.add_modifier.contains(Modifier::BOLD) {
        codes.push("1".to_string());
    }
    if style.add_modifier.contains(Modifier::DIM) {
        codes.push("2".to_string());
    }
    if let Some(fg) = style.fg {
        if let Some(code) = color_code(fg, 38) {
            codes.push(code);
        }
    }
    if let Some(bg) = style.bg {
        if let Some(code) = color_code(bg, 48) {
            codes.push(code);
        }
    }
    if codes.is_empty() { String::new() } else { format!("\x1b[{}m", codes.join(";")) }
}

fn color_code(color: Color, base: u8) -> Option<String> {
    let basic = |n: u8| Some(format!("{}", base - 8 + n));
    match color {
        Color::Rgb(r, g, b) => Some(format!("{};2;{};{};{}", base, r, g, b)),
        Color::Indexed(i) => Some(format!("{};5;{}", base, i)),
        Color::Black => basic(0),
        Color::Red => basic(1),
        Color::Green => basic(2),
        Color::Yellow => basic(3),
        Color::Blue => basic(4),
        Color::Magenta => basic(5),
        Color::Cyan => basic(6),
        Color::Gray | Color::White => basic(7),
        Color::DarkGray => Some(format!("{}", base - 8 + 60)),
        Color::LightRed => Some(format!("{}", base - 8 + 61)),
        Color::LightGreen => Some(format!("{}", base - 8 + 62)),
        Color::LightYellow => Some(format!("{}", base - 8 + 63)),
        Color::LightBlue => Some(format!("{}", base - 8 + 64)),
        Color::LightMagenta => Some(format!("{}", base - 8 + 65)),
        Color::LightCyan => Some(format!("{}", base - 8 + 66)),
        Color::Reset => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use ratatui::text::Span;

    #[test]
    fn converts_styles() {
        let line = Line::from(vec![
            Span::styled("a", Style::default().fg(Color::Rgb(1, 2, 3)).add_modifier(Modifier::BOLD)),
            Span::raw("b  "),
        ]);
        assert_eq!(line_to_ansi(&line, true), "\x1b[1;38;2;1;2;3ma\x1b[0mb\x1b[0m");
        assert_eq!(line_to_ansi(&line, false), "ab");
    }
}
