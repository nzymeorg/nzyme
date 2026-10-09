//! Rendering of the tap top dashboard. Everything animates from wall time: LEDs fade by the age of the last
//! activity, error flashes by the age of the last error, and sparklines show the newest ticks on the right.

use std::time::Instant;

use ratatui::layout::{Constraint, Layout, Margin, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Cell, Paragraph, Row, Table, TableState};
use ratatui::Frame;

use crate::apps::tap::protocol::SUPPORTED_PROTOCOL;
use crate::apps::tap::source::LinkState;
use crate::apps::tap::state::{ChannelState, TopState};
use crate::apps::tap::{App, Mode, Page};
use crate::tools::{format_bytes, format_clock_utc, format_count, now_unix};
use crate::tui::theme::{blink_on, centered_rect, fill_background, header_cell, palette, panel, panel_title,
                        render_key_hints, segment_bar, spinner_frame};

const BLOCKS: [&str; 9] = [" ", "▁", "▂", "▃", "▄", "▅", "▆", "▇", "█"];

pub fn draw(f: &mut Frame, app: &App, now: Instant) {
    fill_background(f);

    let has_data = app.state.hello.is_some();
    let log_height = if app.page == Page::Overview && has_data { app.log_height() } else { 0 };

    let chunks = Layout::vertical([
        Constraint::Length(1),
        Constraint::Min(5),
        Constraint::Length(log_height),
        Constraint::Length(1),
    ]).split(f.area());

    render_header(f, chunks[0], app, now);

    if !has_data {
        render_waiting(f, chunks[1], app, now);
    } else {
        match app.page {
            Page::Overview => render_overview(f, chunks[1], app, now),
            Page::Leader => render_leader(f, chunks[1], app, now),
            Page::Detail => render_detail(f, chunks[1], app, now),
            Page::Log => render_log(f, chunks[1], app, now, app.log_scroll, true),
        }
        if log_height > 0 {
            render_log(f, chunks[2], app, now, 0, false);
        }
    }

    render_key_hints(f, chunks[3], &app.footer_keys());
}

// ---------------------------------------------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------------------------------------------

fn render_header(f: &mut Frame, area: Rect, app: &App, now: Instant) {
    let muted = Style::default().fg(palette::MUTED);
    let faint = Style::default().fg(palette::FAINT);
    let white = Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD);
    let separator = || Span::styled("  │  ", faint);

    // Groups in priority order. Lower priority groups are dropped first when the terminal is narrow.
    let mut groups: Vec<Vec<Span<'static>>> = Vec::new();

    let mut brand = vec![
        Span::styled("▐", Style::default().fg(palette::RED)),
        Span::styled("NZYME", Style::default().fg(palette::WHITE).bg(palette::RED).add_modifier(Modifier::BOLD)),
        Span::styled("▌", Style::default().fg(palette::RED)),
        Span::styled(" ░▒▓ ", Style::default().fg(palette::RED_DIM)),
        Span::styled("TAP TOP", white),
        Span::styled("  ▸ ", Style::default().fg(palette::RED_DIM)),
    ];
    match &app.mode {
        Mode::Live(source) => {
            brand.push(Span::styled("LIVE ", white));
            brand.push(Span::styled(source.address.clone(), muted));
        }
        Mode::Replay(replay) => {
            brand.push(Span::styled("REPLAY ", white));
            brand.push(Span::styled(basename(&replay.path), muted));
        }
    }
    groups.push(brand);

    match &app.mode {
        Mode::Live(_) => {
            let mut link = vec![separator()];
            link.extend(link_spans(app, now));
            if app.frozen {
                link.push(separator());
                link.push(Span::styled("‖ FROZEN", Style::default().fg(if blink_on() { palette::ORANGE } else { palette::WHITE }).add_modifier(Modifier::BOLD)));
            }
            groups.push(link);
        }
        Mode::Replay(replay) => {
            let state = if replay.paused { "‖" } else if replay.finished() { "■" } else { "▶" };
            let fraction = if replay.duration_ms > 0 { replay.position_ms / replay.duration_ms as f64 } else { 1.0 };
            groups.push(vec![
                separator(),
                Span::styled(
                    format!("{} {}×", state, trim_float(replay.speed)),
                    Style::default().fg(if replay.paused { palette::ORANGE } else { palette::WHITE }).add_modifier(Modifier::BOLD),
                ),
                Span::styled(format!(" {} / {} ", hms(replay.position_ms as i64), hms(replay.duration_ms)), muted),
                Span::styled(segment_bar(fraction, 12), Style::default().fg(palette::RED)),
            ]);
        }
    }

    groups.push(vec![separator(), Span::styled(app.page.label(), white)]);

    if let Mode::Live(source) = &app.mode {
        if let Some(path) = &source.recording {
            groups.push(vec![
                separator(),
                Span::styled(if blink_on() { "● REC " } else { "○ REC " }, Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
                Span::styled(basename(path), muted),
            ]);
        }
    }

    if let Some(hello) = &app.state.hello {
        let mut info = vec![
            separator(),
            Span::styled(format!("nzyme-tap {}", hello.version), muted),
            Span::styled(format!(" @ {}", hello.hostname), faint),
        ];
        if let Some(uptime) = app.state.uptime_ms() {
            info.push(Span::styled(format!("  up {}", hms(uptime)), muted));
        }
        if hello.protocol > SUPPORTED_PROTOCOL {
            info.push(Span::styled(format!("  ▲ PROTOCOL v{} > v{}", hello.protocol, SUPPORTED_PROTOCOL), Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD)));
        }
        groups.push(info);
    }

    let clock = match &app.mode {
        Mode::Replay(_) if app.state.now_t > 0 => format!("{} UTC ", format_clock_utc(app.state.now_t / 1000)),
        _ => format!("{} UTC ", format_clock_utc(now_unix())),
    };

    // Add groups while they fit, keeping room for the clock.
    let available = (area.width as usize).saturating_sub(clock.chars().count() + 1);
    let mut spans: Vec<Span<'static>> = Vec::new();
    let mut used = 0;
    for group in groups {
        let width: usize = group.iter().map(|s| s.content.chars().count()).sum();
        if used + width > available && !spans.is_empty() {
            break;
        }
        used += width;
        spans.extend(group);
    }

    let pad = (area.width as usize).saturating_sub(used + clock.chars().count()).max(1);
    spans.push(Span::raw(" ".repeat(pad)));
    spans.push(Span::styled(clock, muted));

    f.render_widget(Paragraph::new(Line::from(spans)).style(Style::default().bg(palette::BG)), area);
}

fn basename(path: &str) -> String {
    std::path::Path::new(path).file_name().map(|f| f.to_string_lossy().to_string()).unwrap_or_else(|| path.to_string())
}

fn link_spans(app: &App, now: Instant) -> Vec<Span<'static>> {
    let bold = Modifier::BOLD;
    match &app.link {
        LinkState::Connecting { attempt, .. } => vec![
            Span::styled(format!("{} CONNECTING", spinner_frame()), Style::default().fg(palette::ORANGE).add_modifier(bold)),
            Span::styled(format!(" #{}", attempt), Style::default().fg(palette::MUTED)),
        ],
        LinkState::Lost { since, .. } => vec![
            Span::styled("▲ LINK LOST", Style::default().fg(palette::WHITE).bg(if blink_on() { Color::Red } else { palette::RED_DIM }).add_modifier(bold)),
            Span::styled(format!(" {}s", now.duration_since(*since).as_secs()), Style::default().fg(palette::MUTED)),
        ],
        LinkState::Connected { .. } => {
            let tick_ms = app.state.tick_ms();
            match app.state.tick_age(now) {
                None => vec![Span::styled(format!("{} ACQUIRING", spinner_frame()), Style::default().fg(palette::ORANGE).add_modifier(bold))],
                Some(age) if !app.frozen && age * 1000.0 > (tick_ms * 4) as f64 => vec![
                    Span::styled("▲ STALL", Style::default().fg(if blink_on() { palette::WHITE } else { palette::ORANGE }).add_modifier(bold)),
                    Span::styled(format!(" no tick for {:.1}s", age), Style::default().fg(palette::ORANGE)),
                ],
                Some(_) => vec![
                    Span::styled("● LINK", Style::default().fg(Color::Green).add_modifier(bold)),
                    Span::styled(format!(" tick {}ms", tick_ms), Style::default().fg(palette::MUTED)),
                    if app.bad_lines > 0 {
                        Span::styled(format!("  {} unparsed", app.bad_lines), Style::default().fg(palette::ORANGE))
                    } else {
                        Span::raw("")
                    },
                ],
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Waiting screen
// ---------------------------------------------------------------------------------------------------------------

fn render_waiting(f: &mut Frame, area: Rect, app: &App, now: Instant) {
    let popup = centered_rect(80, 14, area);
    let block = panel().title(panel_title("Tap Top"));
    let inner = block.inner(popup);
    f.render_widget(block, popup);

    let orange = Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD);
    let muted = Style::default().fg(palette::MUTED);
    let faint = Style::default().fg(palette::FAINT);
    let red = Style::default().fg(Color::Red);

    let mut lines: Vec<Line> = vec![Line::raw("")];
    match &app.mode {
        Mode::Live(source) => {
            match &app.link {
                LinkState::Connecting { attempt, last_error } => {
                    lines.push(Line::from(vec![
                        Span::styled(format!("  {} CONNECTING TO ", spinner_frame()), orange),
                        Span::styled(source.address.clone(), Style::default().fg(palette::WHITE)),
                        Span::styled(format!("   attempt {}", attempt), muted),
                    ]));
                    if let Some(error) = last_error {
                        lines.push(Line::from(vec![Span::styled("  ▲ ", red), Span::styled(error.clone(), red)]));
                    }
                }
                LinkState::Connected { since } => {
                    lines.push(Line::from(vec![
                        Span::styled(format!("  {} CONNECTED, WAITING FOR HELLO", spinner_frame()), orange),
                        Span::styled(format!("   {:.0}s", now.duration_since(*since).as_secs_f64()), muted),
                    ]));
                }
                LinkState::Lost { error, .. } => {
                    lines.push(Line::from(vec![Span::styled("  ▲ LINK LOST  ", red), Span::styled(error.clone(), red)]));
                }
            }
            lines.push(Line::raw(""));
            lines.push(Line::styled("  The tap only streams telemetry when the feed is enabled in nzyme-tap.conf:", muted));
            lines.push(Line::raw(""));
            lines.push(Line::styled("    [telemetry]", faint));
            lines.push(Line::styled("    enabled = true", faint));
            lines.push(Line::styled("    listen = \"127.0.0.1:22910\"", faint));
            lines.push(Line::raw(""));
            lines.push(Line::styled("  Remote tap: ssh -L 22910:127.0.0.1:22910 tap-host, then connect to 127.0.0.1.", muted));
        }
        Mode::Replay(replay) => {
            lines.push(Line::styled(format!("  {} WAITING FOR THE FIRST HELLO FRAME IN {}", spinner_frame(), replay.path), orange));
            lines.push(Line::styled(format!("  {} frames loaded, {} unparsed lines", replay.frame_count(), replay.bad_lines), muted));
        }
    }

    f.render_widget(Paragraph::new(lines), inner.inner(Margin { vertical: 0, horizontal: 1 }));
}

// ---------------------------------------------------------------------------------------------------------------
// Overview
// ---------------------------------------------------------------------------------------------------------------

fn render_overview(f: &mut Frame, area: Rect, app: &App, now: Instant) {
    let state = &app.state;
    let has_wifi = !state.wifi.is_empty();
    // Wide terminals get the WiFi panel next to the captures, narrow ones get it as its own row.
    let wifi_beside = has_wifi && area.width >= 130;
    let wifi_row_height = if has_wifi && !wifi_beside { (state.wifi.len() as u16 * 2 + 2).min(10) } else { 0 };

    // Leader row: submissions, cycle timings and table sizes beside each other on wide terminals; on narrower
    // ones the submissions go on top and the other two share a row. Sized to the longest list, capped so the
    // channels keep room.
    let leader_beside = area.width >= 200;
    let link_rows = state.links.len() as u16;
    let table_rows = table_names(state).len() as u16;
    let size_rows = table_size_gauges(state).len() as u16;
    let panel_height = |rows: u16| if rows == 0 { 3 } else { (rows + 3).min(16) };
    let mut leader_height = if leader_beside {
        panel_height(link_rows.max(table_rows).max(size_rows))
    } else {
        panel_height(link_rows) + panel_height(table_rows.max(size_rows))
    };
    // On short terminals the row would squeeze the channels to nothing; the leader page still has it.
    let fixed_height = 5 + 11 + wifi_row_height + 8;
    if area.height < fixed_height + leader_height {
        leader_height = 0;
    }

    let rows = Layout::vertical([
        Constraint::Length(5),
        Constraint::Length(11),
        Constraint::Length(wifi_row_height),
        Constraint::Min(6),
        Constraint::Length(leader_height),
    ]).split(area);

    render_pulse(f, rows[0], state);

    let middle = if wifi_beside {
        Layout::horizontal([Constraint::Length(48), Constraint::Min(30), Constraint::Percentage(34)]).split(rows[1])
    } else {
        Layout::horizontal([Constraint::Length(48), Constraint::Min(30)]).split(rows[1])
    };
    render_system(f, middle[0], state, now);
    render_captures(f, middle[1], state, now);
    if wifi_beside {
        render_wifi(f, middle[2], state, now);
    } else if wifi_row_height > 0 {
        render_wifi(f, rows[2], state, now);
    }

    render_channels(f, rows[3], app, now);

    if leader_height == 0 {
        return;
    }
    let link_scroll = app.channel_scroll.min(state.links.len().saturating_sub(1));
    let table_scroll = app.channel_scroll.min(table_names(state).len().saturating_sub(1));
    let size_scroll = app.channel_scroll.min(table_size_gauges(state).len().saturating_sub(1));
    if leader_beside {
        let leader = Layout::horizontal([Constraint::Min(96), Constraint::Length(50), Constraint::Length(62)]).split(rows[4]);
        render_leader_link(f, leader[0], app, now, link_scroll);
        render_cycle(f, leader[1], app, table_scroll);
        render_table_sizes(f, leader[2], app, now, size_scroll);
    } else {
        let leader = Layout::vertical([
            Constraint::Length(panel_height(link_rows)), Constraint::Length(panel_height(table_rows.max(size_rows))),
        ]).split(rows[4]);
        render_leader_link(f, leader[0], app, now, link_scroll);
        let second = Layout::horizontal([Constraint::Length(50), Constraint::Min(40)]).split(leader[1]);
        render_cycle(f, second[0], app, table_scroll);
        render_table_sizes(f, second[1], app, now, size_scroll);
    }
}

fn render_pulse(f: &mut Frame, area: Rect, state: &TopState) {
    let mean = state.byte_rate.mean_last(10);
    let msgs = state.msg_rate.mean_last(10);

    let title_right = Line::from(vec![
        Span::styled(format!("{}/s ", format_bytes(mean as i64)), Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD)),
        Span::styled(format!("· {} msg/s · peak {}/s · total {} ", rate_label(msgs), format_bytes(state.peak_byte_rate as i64), format_bytes(state.bytes_total as i64)), Style::default().fg(palette::MUTED)),
    ]).right_aligned();

    let block = panel().title(panel_title("Pulse // Throughput")).title_top(title_right);
    let inner = block.inner(area);
    f.render_widget(block, area);

    let width = inner.width as usize;
    let values = state.byte_rate.last(width);
    let max = state.byte_rate.max().max(1.0);
    let lines = spark_lines(&values, width, inner.height as usize, max);
    f.render_widget(Paragraph::new(lines), inner);
}

fn render_system(f: &mut Frame, area: Rect, state: &TopState, now: Instant) {
    let block = panel().title(panel_title("System"));
    let inner = block.inner(area);
    f.render_widget(block, area);

    let label = Style::default().fg(palette::RED).add_modifier(Modifier::BOLD);
    let text = Style::default().fg(palette::TEXT);
    let muted = Style::default().fg(palette::MUTED);
    let white = Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD);

    let mut lines: Vec<Line> = Vec::new();
    let bar_width = 12;

    match &state.sys {
        Some(sys) => {
            let cpu = (sys.cpu as f64 / 100.0).clamp(0.0, 1.0);
            let mut cores: Vec<Span> = Vec::new();
            for core in sys.cores.iter().take(16) {
                let fraction = (*core as f64 / 100.0).clamp(0.0, 1.0);
                cores.push(Span::styled(BLOCKS[(fraction * 8.0).round() as usize], heat_style(fraction)));
            }
            let mut cpu_line = vec![
                Span::styled("CPU  ", label),
                bar_span(cpu, bar_width),
                Span::styled(format!(" {:>5.1}%  ", sys.cpu), white),
            ];
            cpu_line.extend(cores);
            lines.push(Line::from(cpu_line));

            let used = sys.mem_total.saturating_sub(sys.mem_available.unwrap_or(sys.mem_free));
            let mem = if sys.mem_total > 0 { used as f64 / sys.mem_total as f64 } else { 0.0 };
            lines.push(Line::from(vec![
                Span::styled("MEM  ", label),
                bar_span(mem, bar_width),
                Span::styled(format!(" {:>5.1}%  ", mem * 100.0), white),
                Span::styled(format!("{} / {}", format_bytes(used as i64), format_bytes(sys.mem_total as i64)), muted),
            ]));

            let proc_cpu = sys.proc_cpu.unwrap_or(0.0);
            let cores_n = state.hello.as_ref().map(|h| h.cores).unwrap_or(0).max(1) as f64;
            lines.push(Line::from(vec![
                Span::styled("TAP  ", label),
                bar_span((proc_cpu as f64 / 100.0 / cores_n).clamp(0.0, 1.0), bar_width),
                Span::styled(format!(" {:>5.1}%  ", proc_cpu), white),
                Span::styled(format!("rss {}", sys.proc_rss.map(|r| format_bytes(r as i64)).unwrap_or_else(|| "?".into())), muted),
            ]));

            lines.push(Line::from(vec![
                Span::styled("     ", label),
                Span::styled(format!("thr {}  fds {}  ", sys.proc_threads.map(|t| t.to_string()).unwrap_or_else(|| "?".into()),
                                     sys.proc_fds.map(|t| t.to_string()).unwrap_or_else(|| "?".into())), text),
                Span::styled(sys.load.map(|l| format!("load {:.2} {:.2} {:.2}", l[0], l[1], l[2])).unwrap_or_default(), muted),
            ]));
        }
        None => {
            lines.push(Line::styled(format!("{} waiting for system sample", spinner_frame()), muted));
            lines.push(Line::raw(""));
            lines.push(Line::raw(""));
            lines.push(Line::raw(""));
        }
    }

    // Lock contention and scheduling lag of the tap's metrics mutex, straight from the producer.
    let lock_style = if state.lock_us >= 5000 { Style::default().fg(Color::Red).add_modifier(Modifier::BOLD) }
                     else if state.lock_us >= 500 { Style::default().fg(palette::ORANGE) } else { text };
    let lock_values = state.lock_history.last(14);
    let lock_max = state.lock_history.max().max(1.0);
    let mut lock_line = vec![
        Span::styled("LOCK ", label),
        Span::styled(format!("{:>7} µs", format_count(state.lock_us as i64)), lock_style),
        Span::styled(format!("  max {} µs  ", format_count(state.max_lock_us as i64)), muted),
    ];
    lock_line.extend(spark_spans(&lock_values, 14, lock_max));
    lines.push(Line::from(lock_line));

    let lag_style = if state.lag_ms >= 50 { Style::default().fg(Color::Red).add_modifier(Modifier::BOLD) }
                    else if state.lag_ms >= 10 { Style::default().fg(palette::ORANGE) } else { text };
    lines.push(Line::from(vec![
        Span::styled("LAG  ", label),
        Span::styled(format!("{:>7} ms", state.lag_ms), lag_style),
        Span::styled(format!("  max {} ms", state.max_lag_ms), muted),
    ]));

    let mut misc = vec![Span::styled("HIST ", label)];
    misc.extend(spark_spans(&state.cpu_history.last(24), 24, 100.0));
    if let Some(temp) = state.sys.as_ref().and_then(|s| s.temp) {
        misc.push(Span::styled(format!("  {:.1}°C", temp), if temp >= 75.0 { Style::default().fg(Color::Red) } else { text }));
    }
    if let Some(rpi) = state.hello.as_ref().and_then(|h| h.rpi.clone()) {
        misc.push(Span::styled(format!("  {}", rpi), muted));
    }
    lines.push(Line::from(misc));

    lines.push(leader_line(state, now));

    let age = state.tick_age(now).map(|a| format!("{:.1}s", a)).unwrap_or_else(|| "-".into());
    lines.push(Line::from(vec![
        Span::styled("TICK ", label),
        Span::styled(format!("#{}  age {}  frames {}", format_count(state.ticks as i64), age, format_count(state.frames as i64)), muted),
    ]));

    f.render_widget(Paragraph::new(lines), inner.inner(Margin { vertical: 0, horizontal: 1 }));
}

fn render_captures(f: &mut Frame, area: Rect, state: &TopState, now: Instant) {
    let block = panel().title(panel_title("Captures"));
    let inner = block.inner(area);
    f.render_widget(block, area);

    let muted = Style::default().fg(palette::MUTED);
    let text = Style::default().fg(palette::TEXT);
    let white = Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD);

    if state.captures.is_empty() {
        f.render_widget(Paragraph::new(Line::styled("no captures configured", muted)), inner.inner(Margin { vertical: 0, horizontal: 1 }));
        return;
    }

    let two_lines = state.captures.len() * 2 <= inner.height as usize;
    let mut lines: Vec<Line> = Vec::new();
    let spark_width = 12usize;

    for capture in &state.captures {
        let status = if !capture.seen {
            Span::styled("◌ ", Style::default().fg(palette::FAINT))
        } else if !capture.running {
            Span::styled(if blink_on() { "▲ " } else { "△ " }, Style::default().fg(Color::Red).add_modifier(Modifier::BOLD))
        } else {
            led(capture.last_activity, now)
        };

        let rate = capture.rate.mean_last(10);
        let unit = if capture.kind.contains("WiFi") { "fr/s" } else if capture.kind == "Bluetooth" { "dev/s" } else { "pk/s" };
        let mut line = vec![
            status,
            Span::styled(format!("{:<18}", truncate(&capture.name, 18)), if capture.running { white } else { Style::default().fg(Color::Red) }),
            Span::styled(format!("{:<9}", capture.kind.to_uppercase()), muted),
            Span::styled(format!("{:>8} {:<5}", rate_label(rate), unit), text),
        ];
        line.extend(spark_spans(&capture.rate.last(spark_width), spark_width, capture.rate.max().max(1.0)));

        let drops = capture.dropped_buffer + capture.dropped_interface;
        let drop_style = if capture.last_drop.map_or(false, |t| now.duration_since(t).as_millis() < 800) {
            Style::default().fg(palette::WHITE).bg(Color::Red).add_modifier(Modifier::BOLD)
        } else if drops > 0 {
            Style::default().fg(palette::ORANGE)
        } else {
            Style::default().fg(palette::FAINT)
        };

        if two_lines {
            lines.push(Line::from(line));
            lines.push(Line::from(vec![
                Span::styled(format!("  rx {:<14}", format_count(capture.received as i64)), muted),
                Span::styled(" drop ", muted),
                Span::styled(format!("buf {} · if {}", format_count(capture.dropped_buffer as i64), format_count(capture.dropped_interface as i64)), drop_style),
                if capture.new_drops > 0 { Span::styled(format!("  +{}", capture.new_drops), drop_style) } else { Span::raw("") },
            ]));
        } else {
            line.push(Span::styled("  drop ", muted));
            line.push(Span::styled(format_count(drops as i64), drop_style));
            lines.push(Line::from(line));
        }
    }

    f.render_widget(Paragraph::new(lines), inner.inner(Margin { vertical: 0, horizontal: 1 }));
}

fn render_wifi(f: &mut Frame, area: Rect, state: &TopState, now: Instant) {
    let block = panel().title(panel_title("WiFi // Hopper"));
    let inner = block.inner(area);
    f.render_widget(block, area);

    let muted = Style::default().fg(palette::MUTED);
    let faint = Style::default().fg(palette::FAINT);
    let white = Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD);
    let width = inner.width.saturating_sub(2) as usize;

    let mut lines: Vec<Line> = Vec::new();
    for wifi in &state.wifi {
        let (freq, channel_label, width_label) = match &wifi.current {
            Some((freq, width)) => {
                let (band, ch) = freq_to_channel(*freq);
                (*freq, format!("ch {} {}", ch, band), width.clone())
            }
            None => (0, "no hop yet".to_string(), String::new()),
        };

        let mut head = vec![
            led(wifi.last_hop, now),
            Span::styled(format!("{:<17}", truncate(&wifi.name, 17)), white),
        ];
        if freq > 0 {
            head.push(Span::styled(format!("{} MHz ", freq), Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD)));
            head.push(Span::styled(format!("{} ", channel_label), muted));
            head.push(Span::styled(format!("{}MHz ", width_label), faint));
        } else {
            head.push(Span::styled(if wifi.hopper { channel_label } else { "hopper disabled".to_string() }, muted));
        }
        head.push(Span::styled(format!(" {:.1} hop/s", wifi.hop_rate(state.now_t)), muted));
        lines.push(Line::from(head));

        // Channel strip: every assigned channel, the current one lit.
        let current = wifi.current.as_ref().map(|(f, _)| freq_to_channel(*f));
        let mut strip: Vec<Span> = vec![Span::raw("  ")];
        let mut used = 2;
        let bands: [(&str, &Vec<u16>); 3] = [("2g", &wifi.channels_2g), ("5g", &wifi.channels_5g), ("6g", &wifi.channels_6g)];
        'outer: for (band, channels) in bands {
            if channels.is_empty() {
                continue;
            }
            if used > 2 {
                strip.push(Span::styled("│", faint));
                used += 1;
            }
            for ch in channels {
                let label = format!("{}", ch);
                if used + label.len() + 1 > width {
                    strip.push(Span::styled("…", faint));
                    break 'outer;
                }
                let is_current = current.as_ref().map_or(false, |(b, c)| *b == band && c == ch);
                let style = if is_current {
                    Style::default().fg(palette::WHITE).bg(palette::RED).add_modifier(Modifier::BOLD)
                } else {
                    Style::default().fg(palette::MUTED)
                };
                strip.push(Span::styled(label.clone(), style));
                strip.push(Span::raw(" "));
                used += label.len() + 1;
            }
        }
        lines.push(Line::from(strip));
    }

    f.render_widget(Paragraph::new(lines), inner.inner(Margin { vertical: 0, horizontal: 1 }));
}

fn render_channels(f: &mut Frame, area: Rect, app: &App, now: Instant) {
    let state = &app.state;
    let idle_hidden = app.hide_idle;

    let visible: Vec<&ChannelState> = state.channels.iter()
        .filter(|c| !idle_hidden || !c.is_idle(now))
        .collect();
    let hidden = state.channels.len() - visible.len();

    let title_right = Line::from(vec![
        Span::styled(
            if idle_hidden { format!("{} channels · {} idle hidden ", visible.len(), hidden) } else { format!("{} channels ", visible.len()) },
            Style::default().fg(palette::MUTED),
        ),
    ]).right_aligned();
    let block = panel().title(panel_title("Bus // Channels")).title_top(title_right);
    let inner = block.inner(area);
    f.render_widget(block, area);

    let spark_width = inner.width.saturating_sub(2 + 26 + 10 + 12 + 30 + 9 + 7).max(8) as usize;
    let header = Row::new(vec![
        Cell::from(""), header_cell("channel"), header_cell("msg/s"), header_cell("bytes/s"),
        header_cell("watermark"), header_cell("errors"), header_cell("activity"),
    ]).height(1);

    let mut rows: Vec<Row> = Vec::new();
    let mut last_bus: Option<&str> = None;
    for channel in visible {
        if last_bus != Some(channel.bus.as_str()) {
            last_bus = Some(channel.bus.as_str());
            rows.push(Row::new(vec![
                Cell::from(""),
                Cell::from(Span::styled(format!("── {} BUS", channel.bus.to_uppercase()), Style::default().fg(palette::RED_DIM))),
            ]));
        }
        rows.push(channel_row(channel, now, spark_width));
    }

    let widths = [
        Constraint::Length(2), Constraint::Length(26), Constraint::Length(10), Constraint::Length(12),
        Constraint::Length(30), Constraint::Length(9), Constraint::Min(8),
    ];
    let table = Table::new(rows, widths).header(header).column_spacing(1);
    let mut table_state = TableState::default().with_offset(app.channel_scroll);
    f.render_stateful_widget(table, inner.inner(Margin { vertical: 0, horizontal: 1 }), &mut table_state);
}

fn channel_row(channel: &ChannelState, now: Instant, spark_width: usize) -> Row<'static> {
    let fill = channel.fill_fraction();
    let error_flash = channel.last_error.map_or(false, |t| now.duration_since(t).as_millis() < 600);
    let msg_rate = channel.msg_rate.mean_last(10);

    let name_style = if error_flash {
        Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD)
    } else if fill >= 0.8 {
        Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD)
    } else if channel.last_activity.is_some() {
        Style::default().fg(palette::TEXT)
    } else {
        Style::default().fg(palette::MUTED)
    };

    let number_style = if msg_rate > 0.0 { Style::default().fg(palette::WHITE) } else { Style::default().fg(palette::FAINT) };

    let watermark = Line::from(vec![
        bar_span(fill, 12),
        Span::styled(
            format!(" {:>7}/{}", format_count(channel.watermark as i64), compact_count(channel.capacity)),
            if fill >= 0.8 { Style::default().fg(palette::ORANGE) } else if channel.watermark > 0 { Style::default().fg(palette::TEXT) } else { Style::default().fg(palette::FAINT) },
        ),
    ]);

    let errors = if error_flash {
        Line::from(vec![Span::styled(
            format!("+{} ", channel.new_errors.max(1)),
            Style::default().fg(palette::WHITE).bg(Color::Red).add_modifier(Modifier::BOLD),
        ), Span::styled(format_count(channel.errors as i64), Style::default().fg(Color::Red))])
    } else if channel.errors > 0 {
        Line::from(Span::styled(format_count(channel.errors as i64), Style::default().fg(palette::ORANGE)))
    } else {
        Line::from(Span::styled("0", Style::default().fg(palette::FAINT)))
    };

    let spark = Line::from(spark_spans(&channel.msg_rate.last(spark_width), spark_width, channel.msg_rate.max().max(1.0)));

    let row = Row::new(vec![
        Cell::from(Line::from(led(channel.last_activity, now))),
        Cell::from(Span::styled(truncate(&channel.name, 26), name_style)),
        Cell::from(Span::styled(format!("{:>9}", rate_label(msg_rate)), number_style)),
        Cell::from(Span::styled(format!("{:>11}", bytes_rate(channel.byte_rate.mean_last(10))), number_style)),
        Cell::from(watermark),
        Cell::from(errors),
        Cell::from(spark),
    ]);

    if error_flash {
        row.style(Style::default().bg(Color::Rgb(0x4a, 0x12, 0x08)))
    } else if fill >= 0.8 && blink_on() {
        row.style(Style::default().bg(palette::RED_GLOW))
    } else {
        row
    }
}

/// One line summary of the leader link and report cycle for the system panel.
fn leader_line(state: &TopState, now: Instant) -> Line<'static> {
    let label = Style::default().fg(palette::RED).add_modifier(Modifier::BOLD);
    let muted = Style::default().fg(palette::MUTED);
    let text = Style::default().fg(palette::TEXT);

    let mut spans = vec![Span::styled("LEAD ", label)];
    if state.links.is_empty() {
        spans.push(Span::styled("no submissions yet", muted));
        return Line::from(spans);
    }

    let failure_flash = state.last_submission_failure().map_or(false, |t| now.duration_since(t).as_millis() < 1500);
    spans.push(led(state.last_submission(), now));

    let failures = state.link_failures();
    let ok: u64 = state.links.values().map(|l| l.ok).sum();
    spans.push(Span::styled(format!("{} ok ", format_count(ok as i64)), text));
    spans.push(Span::styled(
        format!("{} fail", format_count(failures as i64)),
        if failure_flash { Style::default().fg(palette::WHITE).bg(Color::Red).add_modifier(Modifier::BOLD) }
        else if failures > 0 { Style::default().fg(palette::ORANGE) } else { Style::default().fg(palette::FAINT) },
    ));

    let last_rtt = state.links.values().filter(|l| l.last_at.is_some())
        .max_by_key(|l| l.last_at).map(|l| l.last_rtt_ms).unwrap_or(0);
    spans.push(Span::styled(format!("  rtt {} ms", last_rtt), rtt_style(last_rtt)));

    if state.cycle.count > 0 {
        spans.push(Span::styled(format!("  cycle {} ms", state.cycle.last_total_ms), cycle_style(state.cycle.last_total_ms)));
    }
    Line::from(spans)
}

fn rtt_style(ms: u64) -> Style {
    if ms >= 5000 { Style::default().fg(Color::Red).add_modifier(Modifier::BOLD) }
    else if ms >= 1000 { Style::default().fg(palette::ORANGE) }
    else { Style::default().fg(palette::TEXT) }
}

fn cycle_style(ms: u64) -> Style {
    if ms >= 5000 { Style::default().fg(Color::Red).add_modifier(Modifier::BOLD) }
    else if ms >= 1000 { Style::default().fg(palette::ORANGE) }
    else { Style::default().fg(palette::TEXT) }
}

// ---------------------------------------------------------------------------------------------------------------
// Leader page: submissions, report cycle, state tables
// ---------------------------------------------------------------------------------------------------------------

fn render_leader(f: &mut Frame, area: Rect, app: &App, now: Instant) {
    let rows = Layout::vertical([Constraint::Percentage(45), Constraint::Min(8)]).split(area);
    let bottom = Layout::horizontal([Constraint::Length(50), Constraint::Min(60), Constraint::Length(54)]).split(rows[1]);

    render_leader_link(f, rows[0], app, now, app.leader_scroll);
    render_cycle(f, bottom[0], app, app.leader_scroll);
    render_table_sizes(f, bottom[1], app, now, app.leader_scroll);
    render_state_gauges(f, bottom[2], app, now);
}

fn render_leader_link(f: &mut Frame, area: Rect, app: &App, now: Instant, scroll: usize) {
    let state = &app.state;
    let failures = state.link_failures();
    let total_bytes: u64 = state.links.values().map(|l| l.total_bytes).sum();

    let title_right = Line::from(vec![
        Span::styled(format!("{} paths · {} uploaded · ", state.links.len(), format_bytes(total_bytes as i64)), Style::default().fg(palette::MUTED)),
        Span::styled(format!("{} failed ", format_count(failures as i64)), if failures > 0 { Style::default().fg(palette::ORANGE) } else { Style::default().fg(palette::MUTED) }),
    ]).right_aligned();
    let block = panel().title(panel_title("Leader Link // Submissions")).title_top(title_right);
    let inner = block.inner(area);
    f.render_widget(block, area);

    if state.links.is_empty() {
        f.render_widget(
            Paragraph::new(Line::styled(format!("{} no submissions seen yet. Tables report every 10 seconds.", spinner_frame()), Style::default().fg(palette::MUTED))),
            inner.inner(Margin { vertical: 0, horizontal: 1 }),
        );
        return;
    }

    let spark_width = 16usize;
    let error_width = inner.width.saturating_sub(2 + 22 + 8 + 8 + 9 + 11 + 11 + spark_width as u16 + 8).max(10) as usize;
    let header = Row::new(vec![
        Cell::from(""), header_cell("path"), header_cell("ok"), header_cell("fail"), header_cell("status"),
        header_cell("rtt ms"), header_cell("bytes"), header_cell("rtt trend"), header_cell("last error"),
    ]);

    let rows: Vec<Row> = state.links.iter().map(|(path, link)| {
        let failure_flash = link.last_failure_at.map_or(false, |t| now.duration_since(t).as_millis() < 1500);
        let disabled = link.last_status == Some(403);

        let status = match link.last_status {
            Some(403) => Span::styled("403 off", Style::default().fg(palette::FAINT)),
            Some(s) if (200..300).contains(&s) => Span::styled(format!("{}", s), Style::default().fg(palette::TEXT)),
            Some(s) => Span::styled(format!("{}", s), Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD)),
            None if link.last_ok => Span::styled("-", Style::default().fg(palette::FAINT)),
            None => Span::styled("no resp", Style::default().fg(Color::Red).add_modifier(Modifier::BOLD)),
        };

        let fail = if failure_flash {
            Span::styled(format!("{:>5}", link.failed), Style::default().fg(palette::WHITE).bg(Color::Red).add_modifier(Modifier::BOLD))
        } else if link.failed > 0 {
            Span::styled(format!("{:>5}", link.failed), Style::default().fg(palette::ORANGE))
        } else {
            Span::styled("    0", Style::default().fg(palette::FAINT))
        };

        let name_style = if failure_flash { Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD) }
                         else if disabled { Style::default().fg(palette::FAINT) }
                         else { Style::default().fg(palette::TEXT) };

        let row = Row::new(vec![
            Cell::from(Line::from(led(link.last_at, now))),
            Cell::from(Span::styled(truncate(path, 22), name_style)),
            Cell::from(Span::styled(format!("{:>7}", format_count(link.ok as i64)), Style::default().fg(palette::TEXT))),
            Cell::from(fail),
            Cell::from(status),
            Cell::from(Span::styled(format!("{:>5} /{:<5}", link.last_rtt_ms, link.max_rtt_ms), rtt_style(link.last_rtt_ms))),
            Cell::from(Span::styled(format!("{:>10}", format_bytes(link.last_bytes as i64)), Style::default().fg(palette::MUTED))),
            Cell::from(Line::from(spark_spans(&link.rtt_history.last(spark_width), spark_width, link.rtt_history.max().max(1.0)))),
            Cell::from(Span::styled(
                link.last_error.as_deref().map(|e| truncate(e, error_width)).unwrap_or_default(),
                Style::default().fg(if link.last_ok { palette::FAINT } else { Color::Red }),
            )),
        ]);
        if failure_flash { row.style(Style::default().bg(Color::Rgb(0x4a, 0x12, 0x08))) } else { row }
    }).collect();

    let widths = [
        Constraint::Length(2), Constraint::Length(22), Constraint::Length(8), Constraint::Length(6), Constraint::Length(8),
        Constraint::Length(12), Constraint::Length(10), Constraint::Length(spark_width as u16), Constraint::Min(10),
    ];
    let table = Table::new(rows, widths).header(header).column_spacing(1);
    f.render_stateful_widget(table, inner.inner(Margin { vertical: 0, horizontal: 1 }), &mut TableState::default().with_offset(scroll));
}

/// Per table: how long its last report step took, its share of the cycle, and the slowest it has been.
fn render_cycle(f: &mut Frame, area: Rect, app: &App, scroll: usize) {
    let state = &app.state;
    let cycle = &state.cycle;

    // Short, so it fits beside the panel name on the home dashboard. Per-table maxima are in the rows.
    let mut title: Vec<Span> = Vec::new();
    if cycle.count > 0 {
        title.push(Span::styled(format!("#{} · ", cycle.count), Style::default().fg(palette::MUTED)));
        title.push(Span::styled(format!("{} ms", cycle.last_total_ms), cycle_style(cycle.last_total_ms)));
        title.push(Span::styled(format!(" · max {} ", cycle.max_total_ms), Style::default().fg(palette::MUTED)));
    }
    let block = panel().title(panel_title("Report Cycle")).title_top(Line::from(title).right_aligned());
    let inner = block.inner(area);
    f.render_widget(block, area);

    let names = table_names(state);
    if names.is_empty() {
        f.render_widget(
            Paragraph::new(Line::styled(format!("{} waiting for the first report cycle", spinner_frame()), Style::default().fg(palette::MUTED))),
            inner.inner(Margin { vertical: 0, horizontal: 1 }),
        );
        return;
    }
    let bar_width = 14usize;
    let reference = cycle.last_total_ms.max(1) as f64;
    let header = Row::new(vec![
        header_cell("table"), header_cell("time"), header_cell("share of cycle"), header_cell("max"),
    ]);

    let rows: Vec<Row> = names.iter().map(|name| {
        let timing = cycle.tables.get(name);
        let (time, max, share) = match timing {
            Some(t) => (
                Span::styled(format!("{:>5} ms", t.last_ms), if t.last_ms >= 1000 { Style::default().fg(palette::ORANGE) } else { Style::default().fg(palette::TEXT) }),
                Span::styled(format!("{:>5} ms", t.max_ms), Style::default().fg(palette::MUTED)),
                { let fraction = t.last_ms as f64 / reference; Span::styled(segment_bar(fraction, bar_width), heat_style(fraction)) },
            ),
            None => (
                Span::styled("       -", Style::default().fg(palette::FAINT)),
                Span::styled("", Style::default()),
                Span::styled(segment_bar(0.0, bar_width), Style::default().fg(palette::FAINT)),
            ),
        };

        Row::new(vec![
            Cell::from(Span::styled(truncate(name, 10), Style::default().fg(palette::TEXT))),
            Cell::from(time),
            Cell::from(share),
            Cell::from(max),
        ])
    }).collect();

    let widths = [
        Constraint::Length(10), Constraint::Length(8), Constraint::Length(bar_width as u16), Constraint::Length(8),
    ];
    let table = Table::new(rows, widths).header(header).column_spacing(1);
    f.render_stateful_widget(table, inner.inner(Margin { vertical: 0, horizontal: 1 }), &mut TableState::default().with_offset(scroll));
}

/// Every table that reported a cycle timing, slowest first.
fn table_names(state: &TopState) -> Vec<String> {
    let cycle = &state.cycle;
    let mut names: Vec<String> = cycle.tables.keys().cloned().collect();
    names.sort_by(|a, b| {
        let ta = cycle.tables.get(a).map(|t| t.last_ms).unwrap_or(0);
        let tb = cycle.tables.get(b).map(|t| t.last_ms).unwrap_or(0);
        tb.cmp(&ta).then(a.cmp(b))
    });
    names
}

/// Every `tables.*` gauge: what each table currently holds between reports.
fn table_size_gauges(state: &TopState) -> Vec<(&String, &crate::apps::tap::state::GaugeState)> {
    state.gauges.iter().filter(|(name, _)| name.starts_with("tables.")).collect()
}

/// Table sizes with a bar against each gauge's own peak and a trend, so growth between the 10 second clears
/// and a table that stopped shrinking are both visible.
fn render_table_sizes(f: &mut Frame, area: Rect, app: &App, now: Instant, scroll: usize) {
    let state = &app.state;
    let gauges = table_size_gauges(state);

    let title_right = Line::from(Span::styled(format!("{} gauges ", gauges.len()), Style::default().fg(palette::MUTED))).right_aligned();
    let block = panel().title(panel_title("Table Sizes")).title_top(title_right);
    let inner = block.inner(area);
    f.render_widget(block, area);

    if gauges.is_empty() {
        f.render_widget(
            Paragraph::new(Line::styled(format!("{} waiting for table gauges", spinner_frame()), Style::default().fg(palette::MUTED))),
            inner.inner(Margin { vertical: 0, horizontal: 1 }),
        );
        return;
    }

    let spark_width = 10usize;
    let bar_width = inner.width.saturating_sub(2 + 24 + 11 + spark_width as u16 + 4).clamp(4, 20) as usize;
    let header = Row::new(vec![header_cell("table"), header_cell("size"), header_cell("vs peak"), header_cell("trend")]);

    let rows: Vec<Row> = gauges.iter().map(|(name, gauge)| {
        let fresh = now.duration_since(gauge.updated).as_millis() < 400;
        let short = name.trim_start_matches("tables.").trim_end_matches(".size").replace('.', " ");
        let peak = gauge.history.max().max(1.0);
        let fraction = (gauge.value / peak).clamp(0.0, 1.0);
        let value = if name.ends_with(".bytes") { format_bytes(gauge.value as i64) } else { format_count(gauge.value as i64) };
        Row::new(vec![
            Cell::from(Span::styled(truncate(&short, 24), Style::default().fg(palette::TEXT))),
            Cell::from(Span::styled(format!("{:>11}", value), if fresh { Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD) } else { Style::default().fg(palette::TEXT) })),
            Cell::from(Span::styled(segment_bar(fraction, bar_width), heat_style(fraction))),
            Cell::from(Line::from(spark_spans(&gauge.history.last(spark_width), spark_width, peak))),
        ])
    }).collect();

    let widths = [Constraint::Length(24), Constraint::Length(11), Constraint::Length(bar_width as u16), Constraint::Min(6)];
    let table = Table::new(rows, widths).header(header).column_spacing(1);
    f.render_stateful_widget(table, inner.inner(Margin { vertical: 0, horizontal: 1 }), &mut TableState::default().with_offset(scroll));
}

/// Sizes of state that is not tied to one table: every gauge under `state.` or `context.`.
fn render_state_gauges(f: &mut Frame, area: Rect, app: &App, now: Instant) {
    let state = &app.state;
    let gauges: Vec<(&String, &crate::apps::tap::state::GaugeState)> = state.gauges.iter()
        .filter(|(name, _)| name.starts_with("state.") || name.starts_with("context."))
        .collect();

    let title_right = Line::from(Span::styled(format!("{} gauges ", gauges.len()), Style::default().fg(palette::MUTED))).right_aligned();
    let block = panel().title(panel_title("State // Context")).title_top(title_right);
    let inner = block.inner(area);
    f.render_widget(block, area);

    if gauges.is_empty() {
        f.render_widget(
            Paragraph::new(Line::styled(format!("{} waiting for gauges", spinner_frame()), Style::default().fg(palette::MUTED))),
            inner.inner(Margin { vertical: 0, horizontal: 1 }),
        );
        return;
    }

    let spark_width = 10usize;
    let header = Row::new(vec![header_cell("gauge"), header_cell("size"), header_cell("trend")]);
    let rows: Vec<Row> = gauges.iter().map(|(name, gauge)| {
        let fresh = now.duration_since(gauge.updated).as_millis() < 400;
        let short = name.trim_end_matches(".size");
        Row::new(vec![
            Cell::from(Span::styled(truncate(short, 28), Style::default().fg(palette::TEXT))),
            Cell::from(Span::styled(format!("{:>10}", format_count(gauge.value as i64)), if fresh { Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD) } else { Style::default().fg(palette::TEXT) })),
            Cell::from(Line::from(spark_spans(&gauge.history.last(spark_width), spark_width, gauge.history.max().max(1.0)))),
        ])
    }).collect();

    let widths = [Constraint::Length(28), Constraint::Length(10), Constraint::Min(6)];
    let table = Table::new(rows, widths).header(header).column_spacing(1);
    f.render_stateful_widget(table, inner.inner(Margin { vertical: 0, horizontal: 1 }), &mut TableState::default().with_offset(app.leader_scroll));
}

// ---------------------------------------------------------------------------------------------------------------
// Detail page: gauges and timers
// ---------------------------------------------------------------------------------------------------------------

fn render_detail(f: &mut Frame, area: Rect, app: &App, now: Instant) {
    let state = &app.state;
    let halves = Layout::horizontal([Constraint::Percentage(50), Constraint::Percentage(50)]).split(area);

    // Gauges.
    {
        let block = panel().title(panel_title("Gauges"))
            .title_top(Line::from(Span::styled(format!("{} gauges ", state.gauges.len()), Style::default().fg(palette::MUTED))).right_aligned());
        let inner = block.inner(halves[0]);
        f.render_widget(block, halves[0]);

        let spark_width = inner.width.saturating_sub(2 + 40 + 14 + 2).max(6) as usize;
        let rows: Vec<Row> = state.gauges.iter().map(|(name, gauge)| {
            let fresh = now.duration_since(gauge.updated).as_millis() < 400;
            let value = if gauge.is_float { format!("{:.2}", gauge.value) } else { format_count(gauge.value as i64) };
            Row::new(vec![
                Cell::from(Span::styled(truncate(name, 40), Style::default().fg(palette::TEXT))),
                Cell::from(Span::styled(format!("{:>14}", value), if fresh { Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD) } else { Style::default().fg(palette::TEXT) })),
                Cell::from(Line::from(spark_spans(&gauge.history.last(spark_width), spark_width, gauge.history.max().max(1.0)))),
            ])
        }).collect();

        let table = Table::new(rows, [Constraint::Length(40), Constraint::Length(14), Constraint::Min(6)])
            .header(Row::new(vec![header_cell("gauge"), header_cell("value"), header_cell("trend")]))
            .column_spacing(1);
        if state.gauges.is_empty() {
            f.render_widget(Paragraph::new(Line::styled(format!("{} waiting for stats frame", spinner_frame()), Style::default().fg(palette::MUTED))), inner.inner(Margin { vertical: 0, horizontal: 1 }));
        } else {
            f.render_stateful_widget(table, inner.inner(Margin { vertical: 0, horizontal: 1 }), &mut TableState::default().with_offset(app.detail_scroll));
        }
    }

    // Timers.
    {
        let block = panel().title(panel_title("Timers"))
            .title_top(Line::from(Span::styled(format!("{} timers · µs over last 60s ", state.timers.len()), Style::default().fg(palette::MUTED))).right_aligned());
        let inner = block.inner(halves[1]);
        f.render_widget(block, halves[1]);

        let max_p99 = state.timers.values().map(|t| t.p99).fold(0.0, f64::max).max(1.0);
        let bar_width = inner.width.saturating_sub(2 + 40 + 11 + 11 + 3).max(6) as usize;
        let rows: Vec<Row> = state.timers.iter().map(|(name, timer)| {
            let fresh = now.duration_since(timer.updated).as_millis() < 400;
            let fraction = (timer.p99 / max_p99).clamp(0.0, 1.0);
            Row::new(vec![
                Cell::from(Span::styled(truncate(name, 40), Style::default().fg(palette::TEXT))),
                Cell::from(Span::styled(format!("{:>11}", format_count(timer.mean as i64)), if fresh { Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD) } else { Style::default().fg(palette::TEXT) })),
                Cell::from(Span::styled(format!("{:>11}", format_count(timer.p99 as i64)), Style::default().fg(if timer.p99 >= 100_000.0 { palette::ORANGE } else { palette::TEXT }))),
                Cell::from(Span::styled(segment_bar(fraction, bar_width), heat_style(fraction))),
            ])
        }).collect();

        let table = Table::new(rows, [Constraint::Length(40), Constraint::Length(11), Constraint::Length(11), Constraint::Min(6)])
            .header(Row::new(vec![header_cell("timer"), header_cell("mean"), header_cell("p99"), header_cell("p99 relative")]))
            .column_spacing(1);
        if state.timers.is_empty() {
            f.render_widget(Paragraph::new(Line::styled(format!("{} timers arrive every few seconds", spinner_frame()), Style::default().fg(palette::MUTED))), inner.inner(Margin { vertical: 0, horizontal: 1 }));
        } else {
            f.render_stateful_widget(table, inner.inner(Margin { vertical: 0, horizontal: 1 }), &mut TableState::default().with_offset(app.detail_scroll));
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Log
// ---------------------------------------------------------------------------------------------------------------

fn render_log(f: &mut Frame, area: Rect, app: &App, now: Instant, scroll_from_bottom: usize, full: bool) {
    let state = &app.state;
    let counts = state.log_counts;

    let mut title: Vec<Span> = Vec::new();
    let badge = |label: &str, n: u64, style: Style| -> Vec<Span<'static>> {
        if n == 0 { return Vec::new(); }
        vec![Span::styled(format!(" {} {} ", format_count(n as i64), label), style)]
    };
    title.extend(badge("ERR", counts[0], Style::default().fg(palette::WHITE).bg(Color::Red).add_modifier(Modifier::BOLD)));
    title.extend(badge("WRN", counts[1], Style::default().fg(palette::ORANGE)));
    title.extend(badge("INF", counts[2], Style::default().fg(palette::MUTED)));
    title.extend(badge("DBG", counts[3], Style::default().fg(palette::FAINT)));
    if scroll_from_bottom > 0 {
        title.push(Span::styled(format!(" ▲ {} ", scroll_from_bottom), Style::default().fg(palette::ORANGE)));
    }
    title.push(Span::raw(" "));

    let block = panel().title(panel_title(if full { "Log // Full" } else { "Log" })).title_top(Line::from(title).right_aligned());
    let inner = block.inner(area);
    f.render_widget(block, area);

    let height = inner.height as usize;
    let total = state.logs.len();
    let end = total.saturating_sub(scroll_from_bottom.min(total));
    let start = end.saturating_sub(height);

    let lines: Vec<Line> = state.logs.iter().skip(start).take(end - start).map(|entry| {
        let fresh = now.duration_since(entry.at).as_millis() < 700;
        let (badge, badge_style, text_style) = match entry.level.as_str() {
            "ERROR" => ("ERR", Style::default().fg(palette::WHITE).bg(Color::Red).add_modifier(Modifier::BOLD), Style::default().fg(Color::Red)),
            "WARN" => ("WRN", Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD), Style::default().fg(palette::ORANGE)),
            "INFO" => ("INF", Style::default().fg(palette::MUTED), Style::default().fg(palette::TEXT)),
            "DEBUG" => ("DBG", Style::default().fg(palette::FAINT), Style::default().fg(palette::MUTED)),
            _ => ("TRC", Style::default().fg(palette::FAINT), Style::default().fg(palette::FAINT)),
        };
        let text_style = if fresh { text_style.fg(palette::WHITE).add_modifier(Modifier::BOLD) } else { text_style };
        Line::from(vec![
            Span::styled(format!("{} ", clock_ms(entry.t)), Style::default().fg(palette::FAINT)),
            Span::styled(badge, badge_style),
            Span::styled(format!(" {}", entry.message), text_style),
        ])
    }).collect();

    f.render_widget(Paragraph::new(lines), inner.inner(Margin { vertical: 0, horizontal: 1 }));
}

// ---------------------------------------------------------------------------------------------------------------
// Drawing primitives
// ---------------------------------------------------------------------------------------------------------------

/// Color for a 0..1 intensity relative to the series' own peak: dim red for low, red for normal, orange near
/// the peak. Nothing else in a sparkline is colored, so orange always means "close to the highest seen".
fn heat_style(fraction: f64) -> Style {
    let color = if fraction <= 0.0 { palette::FAINT }
                else if fraction < 0.3 { palette::RED_DIM }
                else if fraction < 0.75 { palette::RED }
                else { palette::ORANGE };
    Style::default().fg(color)
}

/// Single-row sparkline, oldest left, newest right, padded on the left when there are fewer samples than
/// columns.
fn spark_spans(values: &[f64], width: usize, max: f64) -> Vec<Span<'static>> {
    let mut spans = Vec::with_capacity(width);
    let pad = width.saturating_sub(values.len());
    for _ in 0..pad {
        spans.push(Span::styled("▁", Style::default().fg(palette::FAINT)));
    }
    let shown = &values[values.len().saturating_sub(width)..];
    for v in shown {
        let fraction = if max > 0.0 { (v / max).clamp(0.0, 1.0) } else { 0.0 };
        let level = if *v > 0.0 { ((fraction * 8.0).round() as usize).max(1) } else { 0 };
        let glyph = if level == 0 { "▁" } else { BLOCKS[level] };
        let style = if level == 0 { Style::default().fg(palette::FAINT) } else { heat_style(fraction) };
        spans.push(Span::styled(glyph, style));
    }
    spans
}

/// Multi-row sparkline for the pulse panel. Rows are stacked, bottom row first to fill.
fn spark_lines(values: &[f64], width: usize, rows: usize, max: f64) -> Vec<Line<'static>> {
    let rows = rows.max(1);
    let pad = width.saturating_sub(values.len());
    let shown = &values[values.len().saturating_sub(width)..];

    let mut lines: Vec<Vec<Span>> = vec![Vec::with_capacity(width); rows];
    for row in 0..rows {
        for _ in 0..pad {
            lines[row].push(Span::styled(if row + 1 == rows { "▁" } else { " " }, Style::default().fg(palette::FAINT)));
        }
    }

    for v in shown {
        let fraction = if max > 0.0 { (v / max).clamp(0.0, 1.0) } else { 0.0 };
        let total = (fraction * (rows * 8) as f64).round() as usize;
        for row in 0..rows {
            // Row 0 is the top row.
            let from_bottom = rows - 1 - row;
            let level = total.saturating_sub(from_bottom * 8).min(8);
            let (glyph, style) = if level == 0 {
                (if from_bottom == 0 { "▁" } else { " " }, Style::default().fg(palette::FAINT))
            } else {
                // Shade by the height of the column, not the row, so each column has one color.
                (BLOCKS[level], heat_style(fraction))
            };
            lines[row].push(Span::styled(glyph, style));
        }
    }

    lines.into_iter().map(Line::from).collect()
}

/// Activity LED that fades with the age of the last event.
fn led(last: Option<Instant>, now: Instant) -> Span<'static> {
    let age = last.map(|t| now.duration_since(t).as_millis());
    let (glyph, style) = match age {
        Some(a) if a < 150 => ("◉ ", Style::default().fg(palette::WHITE).add_modifier(Modifier::BOLD)),
        Some(a) if a < 400 => ("● ", Style::default().fg(palette::ORANGE).add_modifier(Modifier::BOLD)),
        Some(a) if a < 1000 => ("● ", Style::default().fg(palette::RED)),
        Some(a) if a < 3000 => ("● ", Style::default().fg(palette::RED_DIM)),
        _ => ("○ ", Style::default().fg(palette::FAINT)),
    };
    Span::styled(glyph, style)
}

/// A segment bar colored by fill level: calm when empty, loud when nearly full.
fn bar_span(fraction: f64, width: usize) -> Span<'static> {
    let fraction = fraction.clamp(0.0, 1.0);
    let style = if fraction >= 0.8 {
        Style::default().fg(if blink_on() { palette::WHITE } else { palette::ORANGE }).add_modifier(Modifier::BOLD)
    } else if fraction >= 0.5 {
        Style::default().fg(palette::ORANGE)
    } else if fraction > 0.0 {
        Style::default().fg(palette::RED)
    } else {
        Style::default().fg(palette::FAINT)
    };
    Span::styled(segment_bar(fraction, width), style)
}

/// `12.3k`, `1.20M` style compact rate.
pub fn rate_label(v: f64) -> String {
    if v >= 1_000_000.0 { format!("{:.2}M", v / 1_000_000.0) }
    else if v >= 10_000.0 { format!("{:.1}k", v / 1000.0) }
    else if v >= 1000.0 { format!("{:.2}k", v / 1000.0) }
    else if v >= 100.0 { format!("{:.0}", v) }
    else if v > 0.0 { format!("{:.1}", v) }
    else { "0".to_string() }
}

fn bytes_rate(v: f64) -> String {
    format!("{}/s", format_bytes(v as i64))
}

fn compact_count(n: u64) -> String {
    if n >= 1_000_000 { format!("{:.1}M", n as f64 / 1_000_000.0) }
    else if n >= 10_000 { format!("{:.0}k", n as f64 / 1000.0) }
    else { n.to_string() }
}

fn truncate(s: &str, max: usize) -> String {
    if s.chars().count() <= max { s.to_string() } else { format!("{}…", s.chars().take(max.saturating_sub(1)).collect::<String>()) }
}

fn trim_float(v: f64) -> String {
    if (v - v.round()).abs() < f64::EPSILON { format!("{}", v as i64) } else { format!("{}", v) }
}

/// `HH:MM:SS` of a duration in milliseconds.
pub fn hms(ms: i64) -> String {
    let s = (ms / 1000).max(0);
    format!("{:02}:{:02}:{:02}", s / 3600, (s % 3600) / 60, s % 60)
}

/// `HH:MM:SS.mmm` UTC of a unix millisecond timestamp.
fn clock_ms(t: i64) -> String {
    format!("{}.{:03}", format_clock_utc(t.div_euclid(1000)), t.rem_euclid(1000))
}

/// Maps a frequency in MHz to a band label and channel number.
pub fn freq_to_channel(freq: u32) -> (&'static str, u16) {
    match freq {
        2412..=2472 => ("2g", ((freq - 2407) / 5) as u16),
        2484 => ("2g", 14),
        5000..=5900 => ("5g", ((freq - 5000) / 5) as u16),
        5935 => ("6g", 2),
        5955..=7115 => ("6g", ((freq - 5950) / 5) as u16),
        _ => ("?", 0),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maps_frequencies_to_channels() {
        assert_eq!(freq_to_channel(2412), ("2g", 1));
        assert_eq!(freq_to_channel(2437), ("2g", 6));
        assert_eq!(freq_to_channel(2484), ("2g", 14));
        assert_eq!(freq_to_channel(5180), ("5g", 36));
        assert_eq!(freq_to_channel(5825), ("5g", 165));
        assert_eq!(freq_to_channel(5955), ("6g", 1));
        assert_eq!(freq_to_channel(6115), ("6g", 33));
    }

    #[test]
    fn formats_rates() {
        assert_eq!(rate_label(0.0), "0");
        assert_eq!(rate_label(12.34), "12.3");
        assert_eq!(rate_label(999.0), "999");
        assert_eq!(rate_label(1234.0), "1.23k");
        assert_eq!(rate_label(12345.0), "12.3k");
        assert_eq!(rate_label(1_234_567.0), "1.23M");
    }

    #[test]
    fn sparklines_have_exact_width() {
        let spans = spark_spans(&[1.0, 2.0], 5, 2.0);
        assert_eq!(spans.len(), 5);
        let lines = spark_lines(&[0.0, 4.0, 8.0], 4, 2, 8.0);
        assert_eq!(lines.len(), 2);
        assert!(lines.iter().all(|l| l.spans.len() == 4));
    }

    /// Renders every page in both modes through a test backend. Catches layout panics (for example a
    /// constraint that does not fit) at a few terminal sizes, and prints the frames for eyeballing with
    /// `cargo test renders_all_pages -- --nocapture`.
    #[test]
    fn renders_all_pages_at_several_sizes() {
        use ratatui::backend::TestBackend;
        use ratatui::Terminal;
        use std::io::Write;
        use crate::apps::tap::source::{LiveSource, ReplaySource};
        use crate::apps::tap::{App, Mode, Page};

        let dir = std::env::temp_dir().join(format!("nzyme-util-render-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("render.ndjson");
        {
            let mut file = std::fs::File::create(&path).unwrap();
            for line in synthetic_feed() {
                writeln!(file, "{}", line).unwrap();
            }
        }
        let path = path.to_string_lossy().to_string();

        let mut replay = ReplaySource::load(&path, 1.0).unwrap();
        let frames = replay.seek_to(replay.duration_ms as f64);
        let mut app = App::new(Mode::Replay(replay));
        let now = Instant::now();
        for frame in frames {
            app.state.apply(frame, now);
        }
        app.link = LinkState::Connected { since: now };

        for (width, height) in [(80u16, 24u16), (100, 32), (120, 40), (180, 55), (220, 60)] {
            for page in [Page::Overview, Page::Leader, Page::Detail, Page::Log] {
                app.page = page;
                let backend = TestBackend::new(width, height);
                let mut terminal = Terminal::new(backend).unwrap();
                terminal.draw(|f| draw(f, &app, now)).unwrap();
                if (width, height) == (180, 55) || ((width, height) == (100, 32) && page == Page::Overview)
                    || ((width, height) == (220, 60) && page == Page::Overview) {
                    println!("---- {}x{} {} ----", width, height, page.label());
                    println!("{}", buffer_text(terminal.backend().buffer()));
                }
            }
        }

        // Live mode without data shows the waiting screen; with a lost link it keeps the data.
        let mut live = App::new(Mode::Live(LiveSource::start("127.0.0.1:1".to_string(), None)));
        live.link = LinkState::Connecting { attempt: 3, last_error: Some("Connection refused (os error 111)".to_string()) };
        let backend = TestBackend::new(120, 30);
        let mut terminal = Terminal::new(backend).unwrap();
        terminal.draw(|f| draw(f, &live, now)).unwrap();
        println!("---- 120x30 waiting ----");
        println!("{}", buffer_text(terminal.backend().buffer()));
    }

    fn buffer_text(buffer: &ratatui::buffer::Buffer) -> String {
        let mut out = String::new();
        for y in 0..buffer.area.height {
            for x in 0..buffer.area.width {
                out.push_str(buffer[(x, y)].symbol());
            }
            out.push('\n');
        }
        out
    }

    fn synthetic_feed() -> Vec<String> {
        let t0: i64 = 1_700_000_000_000;
        let mut lines = vec![format!(concat!(
            r#"{{"f":"hello","t":{t},"protocol":1,"version":"2.0.0-test","started_at":{s},"hostname":"tap-lab","pid":1,"tick_ms":100,"cores":4,"rpi":null,"#,
            r#""channels":[{{"name":"EthernetBroker","bus":"ethernet","capacity":65535}},{{"name":"TcpPipeline","bus":"ethernet","capacity":65535}},"#,
            r#"{{"name":"DnsPipeline","bus":"ethernet","capacity":4096}},{{"name":"Dot11Broker","bus":"dot11","capacity":65535}},"#,
            r#"{{"name":"BluetoothDevicesPipeline","bus":"bluetooth","capacity":1024}}],"#,
            r#""wifi":[{{"name":"wlx0","hopper":true,"channels_2g":[1,6,11],"channels_5g":[36,40,44,48],"channels_6g":[]}}],"#,
            r#""interfaces":[{{"name":"eth0","type":"Ethernet"}},{{"name":"wlx0","type":"WiFi"}}],"#,
            r#""backlog":[{{"t":{b},"level":"INFO","msg":"Bootstrap complete."}}]}}"#),
            t = t0, s = t0 - 3_600_000, b = t0 - 1000)];

        for i in 1..=40i64 {
            let t = t0 + i * 100;
            let m = i * 120;
            lines.push(format!(concat!(
                r#"{{"f":"tick","t":{t},"lag_ms":1,"lock_us":{lock},"bytes":{bytes},"#,
                r#""ch":[{{"n":"EthernetBroker","w":{w},"c":65535,"m":{m},"b":{b},"e":0}},{{"n":"TcpPipeline","w":{w2},"c":65535,"m":{m2},"b":{b2},"e":0}},"#,
                r#"{{"n":"DnsPipeline","w":{w3},"c":4096,"m":{m3},"b":{b3},"e":{e3}}},{{"n":"Dot11Broker","w":0,"c":65535,"m":{m4},"b":{b4},"e":0}},"#,
                r#"{{"n":"BluetoothDevicesPipeline","w":0,"c":1024,"m":0,"b":0,"e":0}}],"#,
                r#""cap":[{{"n":"eth0","type":"Ethernet","run":true,"rx":{rx},"db":0,"di":{di}}},{{"n":"wlx0","type":"WiFi","run":true,"rx":{rx2},"db":0,"di":0}}]}}"#),
                t = t, lock = 20 + (i % 7) * 15, bytes = m * 900,
                w = (i * 37) % 3000, m = m, b = m * 800,
                w2 = if i > 30 { 60000 } else { (i * 11) % 500 }, m2 = m / 2, b2 = m * 300,
                w3 = (i % 5) * 10, m3 = i * 3, b3 = i * 200, e3 = if i > 35 { i - 35 } else { 0 },
                m4 = i * 400, b4 = i * 400 * 120,
                rx = i * 500, di = if i > 38 { 7 } else { 0 }, rx2 = i * 410));
            if i % 10 == 0 {
                lines.push(format!(concat!(
                    r#"{{"f":"sys","t":{t},"cpu":42.5,"cores":[10.0,80.0,35.0,60.0],"mem_total":8589934592,"mem_free":2147483648,"mem_available":5368709120,"#,
                    r#""load":[1.2,0.9,0.5],"temp":51.5,"proc_cpu":37.0,"proc_rss":327155712,"proc_threads":42,"proc_fds":118}}"#), t = t));
                lines.push(format!(concat!(
                    r#"{{"f":"stats","t":{t},"gauges":{{"tables.tcp.sessions.size":{g},"tables.tcp.sessions.bytes":41943040,"tables.dns.ips.size":231,"tables.udp.conversations.size":812,"context.macs.size":87,"state.arp.macs.size":140}},"gauges_f":{{"sona.rssi.avg":-61.3}},"#,
                    r#""timers":{{"tables.tcp.sessions.write":{{"mean":123.4,"p99":1530.0}},"l7.dns.tagger":{{"mean":8.1,"p99":45.0}}}},"engagement":["engaged UAV"]}}"#),
                    t = t, g = 1000 + i * 10));
                lines.push(format!(r#"{{"f":"hop","t":{t},"dev":"wlx0","freq":{f},"width":"20"}}"#, t = t, f = if i % 20 == 0 { 5180 } else { 2437 }));
                lines.push(format!(r#"{{"f":"report","t":{t},"path":"tcp/sessions","ok":true,"status":200,"rtt_ms":{r},"bytes":48213}}"#, t = t, r = 20 + i));
                lines.push(format!(r#"{{"f":"report","t":{t},"path":"status","ok":{ok},"status":{st},"rtt_ms":{r},"bytes":9000{err}}}"#,
                    t = t, ok = i != 40, st = if i == 40 { "null" } else { "200" }, r = if i == 40 { 20000 } else { 35 },
                    err = if i == 40 { r#","error":"connection failed: error sending request (caused by: Connection refused)""# } else { "" }));
                lines.push(format!(r#"{{"f":"report","t":{t},"path":"dot11/summary","ok":true,"status":403,"rtt_ms":12,"bytes":120}}"#, t = t));
                lines.push(format!(r#"{{"f":"cycle","t":{t},"total_ms":{tot},"tables":[{{"name":"tcp","ms":{tcp}}},{{"name":"dns","ms":9}},{{"name":"arp","ms":2}},{{"name":"udp","ms":31}}]}}"#,
                    t = t, tot = 60 + i * 2, tcp = 30 + i));
                lines.push(format!(r#"{{"f":"log","t":{t},"level":"{l}","msg":"Channel [DnsPipeline] had submit errors. You are losing packets/frames."}}"#,
                    t = t, l = if i == 40 { "ERROR" } else { "WARN" }));
            }
        }
        lines
    }
}
