use std::fs;
use std::os::unix::fs::PermissionsExt;

pub const GAUGE_NAME: &str = "files.permission_issues";

const FORBIDDEN_BITS: u32 = 0o027;

pub struct PermissionIssue {
    pub path: String,
    pub mode: u32,
}

impl PermissionIssue {
    pub fn describe(&self) -> String {
        format!("[{}] is accessible by other users (mode {:04o})", self.path, self.mode & 0o7777)
    }

    pub fn fix_note(&self) -> String {
        format!("Fix by running as root: chmod 640 {} && chown root:root {}", self.path, self.path)
    }
}

pub fn check(paths: &[&str]) -> Vec<PermissionIssue> {
    let mut issues = Vec::new();

    for path in paths {
        let mode = match fs::metadata(path) {
            Ok(metadata) => metadata.permissions().mode(),
            Err(_) => continue,
        };

        if mode & FORBIDDEN_BITS != 0 {
            issues.push(PermissionIssue { path: path.to_string(), mode });
        }
    }

    issues
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::File;

    fn temp_file(name: &str, mode: u32) -> String {
        let path = std::env::temp_dir().join(format!("nzyme-tap-perm-test-{}-{}", std::process::id(), name));
        File::create(&path).unwrap();
        fs::set_permissions(&path, fs::Permissions::from_mode(mode)).unwrap();
        path.to_string_lossy().to_string()
    }

    #[test]
    fn accepts_owner_and_group_read() {
        for mode in [0o600, 0o640, 0o400] {
            let path = temp_file(&format!("ok{:o}", mode), mode);
            assert!(check(&[&path]).is_empty(), "mode {:o} should be accepted", mode);
            fs::remove_file(path).unwrap();
        }
    }

    #[test]
    fn rejects_world_or_group_write_access() {
        for mode in [0o644, 0o660, 0o604, 0o601, 0o777] {
            let path = temp_file(&format!("bad{:o}", mode), mode);
            let issues = check(&[&path]);
            assert_eq!(issues.len(), 1, "mode {:o} should be rejected", mode);
            assert!(issues[0].describe().contains(&format!("{:04o}", mode)));
            fs::remove_file(path).unwrap();
        }
    }

    #[test]
    fn skips_missing_files() {
        assert!(check(&["/nonexistent/nzyme-tap.conf"]).is_empty());
    }
}
