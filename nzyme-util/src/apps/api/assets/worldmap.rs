use std::collections::{HashMap, HashSet};

use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};

use crate::apps::api::assets::worldmap_data::{COUNTRIES, MAP_HEIGHT, MAP_WIDTH, PIXELS};
use crate::tui::theme::palette;

const LAT_TOP: f64 = 84.0;
const LAT_BOTTOM: f64 = -58.0;

pub const MIN_WIDTH: usize = 40;

#[derive(Clone, Copy, PartialEq)]
pub enum MarkerKind {
    Origin,
    Peer,
}

pub struct Marker {
    pub lat: f64,
    pub lng: f64,
    pub weight: f64,
    pub kind: MarkerKind,
}

pub fn traffic_color(weight: f64) -> Color {
    let stops: [(u8, u8, u8); 5] = [(0x25, 0x63, 0xeb), (0x38, 0xbd, 0xf8), (0xe0, 0xf2, 0xfe), (0xfd, 0xe6, 0x8a), (0xfb, 0x92, 0x3c)];
    let t = weight.clamp(0.0, 1.0) * (stops.len() - 1) as f64;
    let i = (t.floor() as usize).min(stops.len() - 2);
    let f = t - i as f64;
    let mix = |a: u8, b: u8| (a as f64 + (b as f64 - a as f64) * f).round() as u8;
    Color::Rgb(mix(stops[i].0, stops[i + 1].0), mix(stops[i].1, stops[i + 1].1), mix(stops[i].2, stops[i + 1].2))
}

fn pixel(x: usize, y: usize) -> u16 {
    PIXELS[y * MAP_WIDTH + x]
}

fn country_code(index: u16) -> Option<&'static str> {
    if index == 0 { None } else { COUNTRIES.get(index as usize - 1).map(|c| c.0) }
}

pub fn render(width: usize, markers: &[Marker], active_countries: &HashSet<String>) -> Vec<Line<'static>> {
    let cols = width.clamp(MIN_WIDTH, MAP_WIDTH);
    let scale = MAP_WIDTH as f64 / cols as f64;

    let y_top = ((90.0 - LAT_TOP) / 180.0 * MAP_HEIGHT as f64).floor() as usize;
    let y_bottom = ((90.0 - LAT_BOTTOM) / 180.0 * MAP_HEIGHT as f64).ceil() as usize;
    let px_rows = ((y_bottom - y_top) as f64 / scale).round() as usize;
    let rows = px_rows.div_ceil(2);

    let mut marker_cells: HashMap<(usize, usize), &Marker> = HashMap::new();
    for marker in markers {
        let x = ((marker.lng + 180.0) / 360.0 * cols as f64).floor().clamp(0.0, cols as f64 - 1.0) as usize;
        let py = ((90.0 - marker.lat) / 180.0 * MAP_HEIGHT as f64 - y_top as f64) / scale;
        if py < 0.0 || py >= px_rows as f64 {
            continue;
        }
        let row = (py / 2.0).floor() as usize;
        let replace = match marker_cells.get(&(x, row)) {
            None => true,
            Some(existing) => existing.kind != MarkerKind::Origin && (marker.kind == MarkerKind::Origin || marker.weight > existing.weight),
        };
        if replace {
            marker_cells.insert((x, row), marker);
        }
    }

    let land = Style::default().fg(palette::FAINT);
    let active = Style::default().fg(Color::Rgb(0x8a, 0x5a, 0x1c));
    let water = Style::default().fg(palette::FAINT);

    let mut lines = Vec::with_capacity(rows);
    for row in 0..rows {
        let mut spans: Vec<Span<'static>> = Vec::with_capacity(cols);
        for x in 0..cols {
            if let Some(marker) = marker_cells.get(&(x, row)) {
                spans.push(match marker.kind {
                    MarkerKind::Origin => Span::styled("★", Style::default().fg(Color::Rgb(0x22, 0xd3, 0xee)).add_modifier(Modifier::BOLD)),
                    MarkerKind::Peer => Span::styled(
                        if marker.weight > 0.66 { "●" } else if marker.weight > 0.33 { "◉" } else { "○" },
                        Style::default().fg(traffic_color(marker.weight)).add_modifier(Modifier::BOLD),
                    ),
                });
                continue;
            }

            let sx = ((x as f64 + 0.5) * scale).floor() as usize;
            let sample = |py: usize| -> u16 {
                let sy = y_top + ((py as f64 + 0.5) * scale).floor() as usize;
                if sy >= MAP_HEIGHT { 0 } else { pixel(sx.min(MAP_WIDTH - 1), sy) }
            };
            let top = sample(row * 2);
            let bottom = if row * 2 + 1 < px_rows { sample(row * 2 + 1) } else { 0 };

            let is_active = |index: u16| country_code(index).map(|c| active_countries.contains(c)).unwrap_or(false);
            let style_of = |index: u16| if is_active(index) { active } else { land };

            spans.push(match (top > 0, bottom > 0) {
                (true, true) => Span::styled("█", if is_active(top) || is_active(bottom) { active } else { land }),
                (true, false) => Span::styled("▀", style_of(top)),
                (false, true) => Span::styled("▄", style_of(bottom)),
                (false, false) => Span::styled(" ", water),
            });
        }
        lines.push(Line::from(spans));
    }

    lines
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn renders_at_any_width() {
        for width in [40, 80, 120, 160, 300] {
            let lines = render(width, &[], &HashSet::new());
            assert!(!lines.is_empty());
            let expected = width.clamp(MIN_WIDTH, MAP_WIDTH);
            for line in &lines {
                assert_eq!(line.spans.iter().map(|s| s.content.chars().count()).sum::<usize>(), expected);
            }
        }
    }

    #[test]
    fn places_markers() {
        let markers = [Marker { lat: 52.5, lng: 13.4, weight: 1.0, kind: MarkerKind::Origin }];
        let lines = render(160, &markers, &HashSet::new());
        assert!(lines.iter().any(|l| l.spans.iter().any(|s| s.content == "★")));
    }

    #[test]
    fn ramp_avoids_green() {
        for i in 0..=20 {
            if let Color::Rgb(r, g, b) = traffic_color(i as f64 / 20.0) {
                let (r, g, b) = (r as i32, g as i32, b as i32);
                assert!(!(g > r + 40 && g > b + 40), "green at {}", i);
            }
        }
    }
}
