use std::path::PathBuf;

pub fn as_path_buf(s: impl Into<String>) -> PathBuf {
    PathBuf::from(s.into())
}

// Formats a byte count with a binary unit, for example `1.5 GiB`.
pub fn format_bytes(bytes: i64) -> String {
    const UNITS: &[&str] = &["B", "KiB", "MiB", "GiB", "TiB", "PiB"];

    if bytes < 0 {
        return format!("-{}", format_bytes(-bytes));
    }

    let mut value = bytes as f64;
    let mut unit = 0;
    while value >= 1024.0 && unit < UNITS.len() - 1 {
        value /= 1024.0;
        unit += 1;
    }

    if unit == 0 {
        format!("{} {}", bytes, UNITS[unit])
    } else {
        format!("{:.1} {}", value, UNITS[unit])
    }
}

// Formats an integer with thousands separators.
pub fn format_count(n: i64) -> String {
    let s = n.abs().to_string();
    let mut out = String::with_capacity(s.len() + s.len() / 3);
    for (i, c) in s.chars().enumerate() {
        if i > 0 && (s.len() - i) % 3 == 0 {
            out.push(',');
        }
        out.push(c);
    }

    if n < 0 { format!("-{}", out) } else { out }
}

// Formats a used/total pair as `used / total (pct %)`.
pub fn format_usage(used: Option<i64>, total: Option<i64>) -> String {
    match (used, total) {
        (Some(u), Some(t)) if t > 0 => {
            format!("{} / {} ({:.0} %)", format_bytes(u), format_bytes(t), (u as f64 / t as f64) * 100.0)
        }
        (Some(u), Some(t)) => format!("{} / {}", format_bytes(u), format_bytes(t)),
        (Some(u), None) => format_bytes(u),
        _ => "<unknown>".to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn formats_bytes() {
        assert_eq!(format_bytes(0), "0 B");
        assert_eq!(format_bytes(1023), "1023 B");
        assert_eq!(format_bytes(1024), "1.0 KiB");
        assert_eq!(format_bytes(1536), "1.5 KiB");
        assert_eq!(format_bytes(1024 * 1024 * 1024), "1.0 GiB");
    }

    #[test]
    fn formats_counts() {
        assert_eq!(format_count(0), "0");
        assert_eq!(format_count(999), "999");
        assert_eq!(format_count(1000), "1,000");
        assert_eq!(format_count(1234567), "1,234,567");
        assert_eq!(format_count(-1234), "-1,234");
    }

    #[test]
    fn formats_usage() {
        assert_eq!(format_usage(Some(512), Some(1024)), "512 B / 1.0 KiB (50 %)");
        assert_eq!(format_usage(None, None), "<unknown>");
    }
}

/*
 * Parses an ISO 8601 timestamp like `2026-10-07T17:00:00.000Z` or
 * `2026-10-07T17:00:00+02:00` into Unix seconds. Returns `None` for anything it does not
 * understand. Good enough for the timestamps the Nzyme API emits.
 */
pub fn parse_iso8601_to_unix(ts: &str) -> Option<i64> {
    let b = ts.as_bytes();
    if b.len() < 19 || b[4] != b'-' || b[7] != b'-' || (b[10] != b'T' && b[10] != b' ') || b[13] != b':' || b[16] != b':' {
        return None;
    }

    let num = |s: &str| s.parse::<i64>().ok();
    let year = num(&ts[0..4])?;
    let month = num(&ts[5..7])?;
    let day = num(&ts[8..10])?;
    let hour = num(&ts[11..13])?;
    let minute = num(&ts[14..16])?;
    let second = num(&ts[17..19])?;

    // Skip fractional seconds.
    let mut rest = &ts[19..];
    if rest.starts_with('.') {
        let end = rest[1..].find(|c: char| !c.is_ascii_digit()).map(|i| i + 1).unwrap_or(rest.len());
        rest = &rest[end..];
    }

    let offset_seconds = match rest {
        "" | "Z" | "z" => 0,
        _ if rest.len() == 6 && (rest.starts_with('+') || rest.starts_with('-')) && &rest[3..4] == ":" => {
            let sign = if rest.starts_with('-') { -1 } else { 1 };
            sign * (num(&rest[1..3])? * 3600 + num(&rest[4..6])? * 60)
        }
        _ if rest.len() == 5 && (rest.starts_with('+') || rest.starts_with('-')) => {
            let sign = if rest.starts_with('-') { -1 } else { 1 };
            sign * (num(&rest[1..3])? * 3600 + num(&rest[3..5])? * 60)
        }
        _ => return None,
    };

    // Days from civil date (Howard Hinnant's algorithm).
    let y = if month <= 2 { year - 1 } else { year };
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = y - era * 400;
    let mp = (month + 9) % 12;
    let doy = (153 * mp + 2) / 5 + day - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    let days = era * 146097 + doe - 719468;

    Some(days * 86400 + hour * 3600 + minute * 60 + second - offset_seconds)
}

// Formats a duration in seconds as a compact human-readable age, for example `12s`, `3m`, `2h` or `5d`.
pub fn format_age_seconds(seconds: i64) -> String {
    let s = seconds.max(0);
    if s < 60 {
        format!("{}s", s)
    } else if s < 3600 {
        format!("{}m", s / 60)
    } else if s < 86400 {
        format!("{}h {}m", s / 3600, (s % 3600) / 60)
    } else {
        format!("{}d {}h", s / 86400, (s % 86400) / 3600)
    }
}

#[cfg(test)]
mod time_tests {
    use super::*;

    #[test]
    fn parses_iso8601() {
        assert_eq!(parse_iso8601_to_unix("1970-01-01T00:00:00Z"), Some(0));
        assert_eq!(parse_iso8601_to_unix("1970-01-01T00:00:00.000Z"), Some(0));
        assert_eq!(parse_iso8601_to_unix("2000-01-01T00:00:00Z"), Some(946684800));
        assert_eq!(parse_iso8601_to_unix("2026-10-07T17:00:00.123Z"), Some(1791392400));
        assert_eq!(parse_iso8601_to_unix("2026-10-07T19:00:00+02:00"), Some(1791392400));
        assert_eq!(parse_iso8601_to_unix("2026-10-07T15:00:00-0200"), Some(1791392400));
        assert_eq!(parse_iso8601_to_unix("garbage"), None);
    }

    #[test]
    fn formats_age() {
        assert_eq!(format_age_seconds(5), "5s");
        assert_eq!(format_age_seconds(125), "2m");
        assert_eq!(format_age_seconds(3700), "1h 1m");
        assert_eq!(format_age_seconds(90000), "1d 1h");
    }
}

// Current Unix time in seconds.
pub fn now_unix() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

// `HH:MM:SS` in UTC of a Unix timestamp.
pub fn format_clock_utc(unix: i64) -> String {
    let s = unix.rem_euclid(86400);
    format!("{:02}:{:02}:{:02}", s / 3600, (s % 3600) / 60, s % 60)
}

pub fn parse_duration_minutes(input: &str) -> Result<u32, String> {
    let trimmed = input.trim();
    if trimmed.is_empty() {
        return Err("Duration is empty.".to_string());
    }

    let (digits, factor) = match trimmed.chars().last() {
        Some('m') => (&trimmed[..trimmed.len() - 1], 1u32),
        Some('h') => (&trimmed[..trimmed.len() - 1], 60),
        Some('d') => (&trimmed[..trimmed.len() - 1], 1440),
        Some(c) if c.is_ascii_digit() => (trimmed, 1),
        _ => return Err(format!("[{}] is not a duration. Use a number of minutes or a suffix: 30m, 6h, 2d.", trimmed)),
    };

    let amount: u32 = digits.trim().parse()
        .map_err(|_| format!("[{}] is not a duration. Use a number of minutes or a suffix: 30m, 6h, 2d.", trimmed))?;

    if amount == 0 {
        return Err("Duration must be longer than zero.".to_string());
    }

    amount.checked_mul(factor).ok_or_else(|| format!("[{}] is too long.", trimmed))
}

pub fn format_duration_ms(ms: i64) -> String {
    let total_minutes = (ms as f64 / 60000.0).round() as i64;
    if total_minutes < 60 {
        return format!("{}m", total_minutes);
    }

    let total_hours = total_minutes / 60;
    let minutes = total_minutes % 60;
    if total_hours < 48 {
        return if minutes == 0 { format!("{}h", total_hours) } else { format!("{}h {}m", total_hours, minutes) };
    }

    let days = total_hours / 24;
    let hours = total_hours % 24;
    if hours == 0 { format!("{}d", days) } else { format!("{}d {}h", days, hours) }
}

pub fn short_time(timestamp: &str) -> String {
    match parse_iso8601_to_unix(timestamp) {
        Some(unix) => {
            let days = unix.div_euclid(86400);
            let (y, m, d) = civil_from_days(days);
            let secs = unix.rem_euclid(86400);
            format!("{:04}-{:02}-{:02} {:02}:{:02} UTC", y, m, d, secs / 3600, (secs % 3600) / 60)
        }
        None => timestamp.to_string(),
    }
}

pub fn format_iso8601_utc(unix: i64) -> String {
    let (y, m, d) = civil_from_days(unix.div_euclid(86400));
    let secs = unix.rem_euclid(86400);
    format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", y, m, d, secs / 3600, (secs % 3600) / 60, secs % 60)
}

/// Howard Hinnant's days-to-civil algorithm.
fn civil_from_days(days: i64) -> (i64, i64, i64) {
    let z = days + 719468;
    let era = if z >= 0 { z } else { z - 146096 } / 146097;
    let doe = z - era * 146097;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    (if m <= 2 { y + 1 } else { y }, m, d)
}

#[cfg(test)]
mod briefing_time_tests {
    use super::*;

    #[test]
    fn parses_durations() {
        assert_eq!(parse_duration_minutes("30m"), Ok(30));
        assert_eq!(parse_duration_minutes("6h"), Ok(360));
        assert_eq!(parse_duration_minutes("2d"), Ok(2880));
        assert_eq!(parse_duration_minutes("90"), Ok(90));
        assert!(parse_duration_minutes("0").is_err());
        assert!(parse_duration_minutes("1w").is_err());
        assert!(parse_duration_minutes("").is_err());
    }

    #[test]
    fn formats_durations() {
        assert_eq!(format_duration_ms(45 * 60000), "45m");
        assert_eq!(format_duration_ms(3 * 3600000 + 10 * 60000), "3h 10m");
        assert_eq!(format_duration_ms(24 * 3600000), "24h");
        assert_eq!(format_duration_ms(52 * 3600000), "2d 4h");
        assert_eq!(format_duration_ms(7 * 86400000), "7d");
    }

    #[test]
    fn formats_iso8601() {
        assert_eq!(format_iso8601_utc(0), "1970-01-01T00:00:00Z");
        assert_eq!(format_iso8601_utc(1791392400), "2026-10-07T17:00:00Z");
        assert_eq!(parse_iso8601_to_unix(&format_iso8601_utc(1762203257)), Some(1762203257));
    }

    #[test]
    fn shortens_timestamps() {
        assert_eq!(short_time("2026-11-03T20:54:17.862Z"), "2026-11-03 20:54 UTC");
        assert_eq!(short_time("2026-11-03T22:54:17+02:00"), "2026-11-03 20:54 UTC");
        assert_eq!(short_time("2000-02-29T00:00:00Z"), "2000-02-29 00:00 UTC");
        assert_eq!(short_time("garbage"), "garbage");
    }
}
