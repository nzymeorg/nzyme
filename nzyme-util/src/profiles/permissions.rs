//! Permission checks for the profiles file. The file contains API keys, so anything beyond owner-only access is
//! reported loudly. The check never fails a command: The user can still run it, but they cannot miss the warning.

use std::path::Path;

#[derive(Debug, PartialEq)]
pub enum PermissionProblem {
    /// The file can be read by the group or by everyone. Exposes the API keys.
    FileReadableByOthers { mode: u32 },
    /// The file can be written by the group or by everyone. Allows injection of a different server URL.
    FileWritableByOthers { mode: u32 },
    /// The directory can be written by the group or by everyone. Allows replacing the file.
    DirectoryWritableByOthers { mode: u32 },
}

#[cfg(unix)]
pub fn check(path: &Path) -> Vec<PermissionProblem> {
    use std::os::unix::fs::MetadataExt;

    let mut problems = Vec::new();

    if let Ok(meta) = std::fs::metadata(path) {
        let mode = meta.mode() & 0o777;

        if mode & 0o044 != 0 {
            problems.push(PermissionProblem::FileReadableByOthers { mode });
        }

        if mode & 0o022 != 0 {
            problems.push(PermissionProblem::FileWritableByOthers { mode });
        }
    }

    if let Some(dir) = path.parent() {
        if let Ok(meta) = std::fs::metadata(dir) {
            let mode = meta.mode() & 0o777;

            // A sticky, world-writable directory (like /tmp) is still a problem for a credentials file.
            if mode & 0o022 != 0 {
                problems.push(PermissionProblem::DirectoryWritableByOthers { mode });
            }
        }
    }

    problems
}

#[cfg(not(unix))]
pub fn check(_path: &Path) -> Vec<PermissionProblem> {
    Vec::new()
}

/// Prints a warning banner to stderr that is hard to overlook.
pub fn print_warning(path: &Path, problems: &[PermissionProblem]) {
    const RESET: &str = "\x1b[0m";
    const BOLD: &str = "\x1b[1m";
    const FG_RED: &str = "\x1b[31m";
    const BG_RED: &str = "\x1b[41m";
    const FG_WHITE: &str = "\x1b[97m";

    if problems.is_empty() {
        return;
    }

    let width = 78;
    let border = "!".repeat(width);
    let line = |text: &str| {
        let padding = width.saturating_sub(text.chars().count() + 4);
        format!("!! {}{} !!", text, " ".repeat(padding))
    };

    eprintln!();
    eprintln!("{BOLD}{FG_RED}{border}{RESET}");
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line(""));
    eprintln!("{BOLD}{BG_RED}{FG_WHITE}{}{RESET}", line("  WARNING: YOUR NZYME API PROFILES FILE IS NOT PROTECTED  "));
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line(""));
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line("This file contains API keys. Anyone who can read it can act as you"));
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line("in Nzyme, with all of your permissions."));
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line(""));

    let path_line = format!("File: {}", path.display());
    for chunk in wrap(&path_line, width - 4) {
        eprintln!("{BOLD}{FG_RED}{}{RESET}", line(&chunk));
    }
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line(""));

    for problem in problems {
        let text = match problem {
            PermissionProblem::FileReadableByOthers { mode } =>
                format!("* The file is readable by other users (mode {:03o}).", mode),
            PermissionProblem::FileWritableByOthers { mode } =>
                format!("* The file is writable by other users (mode {:03o}).", mode),
            PermissionProblem::DirectoryWritableByOthers { mode } =>
                format!("* The directory is writable by other users (mode {:03o}).", mode),
        };
        eprintln!("{BOLD}{FG_RED}{}{RESET}", line(&text));
    }

    eprintln!("{BOLD}{FG_RED}{}{RESET}", line(""));
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line("Fix it now:"));
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line(""));

    let has_dir_problem = problems.iter()
        .any(|p| matches!(p, PermissionProblem::DirectoryWritableByOthers { .. }));
    let has_file_problem = problems.iter()
        .any(|p| !matches!(p, PermissionProblem::DirectoryWritableByOthers { .. }));

    if has_file_problem {
        for chunk in wrap(&format!("    chmod 600 {}", path.display()), width - 4) {
            eprintln!("{BOLD}{FG_RED}{}{RESET}", line(&chunk));
        }
    }

    if has_dir_problem {
        if let Some(dir) = path.parent() {
            for chunk in wrap(&format!("    chmod 700 {}", dir.display()), width - 4) {
                eprintln!("{BOLD}{FG_RED}{}{RESET}", line(&chunk));
            }
        }
    }

    eprintln!("{BOLD}{FG_RED}{}{RESET}", line(""));
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line("If anyone else had access to this machine, consider the stored API"));
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line("keys compromised. Delete them on your Nzyme user profile page and"));
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line("create new ones."));
    eprintln!("{BOLD}{FG_RED}{}{RESET}", line(""));
    eprintln!("{BOLD}{FG_RED}{border}{RESET}");
    eprintln!();
}

fn wrap(text: &str, max: usize) -> Vec<String> {
    let chars: Vec<char> = text.chars().collect();
    if chars.len() <= max {
        return vec![text.to_string()];
    }

    chars.chunks(max).map(|c| c.iter().collect()).collect()
}

#[cfg(all(test, unix))]
mod tests {
    use super::*;
    use std::fs;
    use std::os::unix::fs::PermissionsExt;

    fn temp_file(test: &str, mode: u32) -> std::path::PathBuf {
        let dir = std::env::temp_dir().join(format!("nzyme-util-test-{}-{}", std::process::id(), test));
        fs::create_dir_all(&dir).unwrap();
        fs::set_permissions(&dir, fs::Permissions::from_mode(0o700)).unwrap();

        let path = dir.join("util.toml");
        fs::write(&path, "").unwrap();
        fs::set_permissions(&path, fs::Permissions::from_mode(mode)).unwrap();

        path
    }

    #[test]
    fn accepts_owner_only_file() {
        let path = temp_file("owner_only", 0o600);
        assert!(check(&path).is_empty());
        fs::remove_dir_all(path.parent().unwrap()).unwrap();
    }

    #[test]
    fn detects_group_and_world_readable_file() {
        let path = temp_file("group_world_readable", 0o644);
        assert_eq!(check(&path), vec![PermissionProblem::FileReadableByOthers { mode: 0o644 }]);
        fs::remove_dir_all(path.parent().unwrap()).unwrap();
    }

    #[test]
    fn detects_world_writable_file() {
        let path = temp_file("world_writable", 0o666);
        let problems = check(&path);
        assert!(problems.contains(&PermissionProblem::FileReadableByOthers { mode: 0o666 }));
        assert!(problems.contains(&PermissionProblem::FileWritableByOthers { mode: 0o666 }));
        fs::remove_dir_all(path.parent().unwrap()).unwrap();
    }

    #[test]
    fn detects_writable_directory() {
        let path = temp_file("writable_dir", 0o600);
        fs::set_permissions(path.parent().unwrap(), fs::Permissions::from_mode(0o777)).unwrap();
        assert_eq!(check(&path), vec![PermissionProblem::DirectoryWritableByOthers { mode: 0o777 }]);
        fs::remove_dir_all(path.parent().unwrap()).unwrap();
    }

    #[test]
    fn missing_file_has_no_problems() {
        assert!(check(Path::new("/nonexistent/nzyme-util/util.toml")).is_empty());
    }
}
