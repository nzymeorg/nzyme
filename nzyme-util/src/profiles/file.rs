//! The profiles file stores named connections to Nzyme installations, including API keys. It lives at
//! `$XDG_CONFIG_HOME/nzyme/util.toml` (usually `~/.config/nzyme/util.toml`) and is always written with
//! permissions that only allow access by the owner.

use std::collections::BTreeMap;
use std::fs;
use std::io;
use std::path::PathBuf;
use serde::{Deserialize, Serialize};

pub const PROFILES_DIR_NAME: &str = "nzyme";
pub const PROFILES_FILE_NAME: &str = "util.toml";
pub const DEFAULT_PROFILE_NAME: &str = "default";

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct ProfilesFile {
    #[serde(default)]
    pub profiles: BTreeMap<String, Profile>,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Profile {
    pub server: String,
    pub api_key: String,
    #[serde(default)]
    pub insecure: bool,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ca_file: Option<String>,
    /// Preferences of the terminal dashboard, written by the dashboard itself.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub gui: Option<GuiSettings>,
}

/// Dashboard preferences stored with a profile. All fields are optional so older files keep loading.
#[derive(Serialize, Deserialize, Debug, Clone, Default, PartialEq)]
pub struct GuiSettings {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub refresh_seconds: Option<u64>,
    /// Selected organization. Absent means all organizations.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub organization_id: Option<String>,
    /// Selected tenant. Absent means all tenants of the selected organization.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub tenant_id: Option<String>,
    /// Selected tap UUIDs. Empty means all taps in scope.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub taps: Vec<String>,
}

/// Replaces the dashboard preferences of one profile and writes the file. Returns `Ok(false)` if the profile does
/// not exist in the file.
pub fn save_gui_settings(profile_name: &str, settings: GuiSettings) -> Result<bool, ProfilesError> {
    let Some(mut file) = load()? else { return Ok(false); };
    let Some(profile) = file.profiles.get_mut(profile_name) else { return Ok(false); };

    profile.gui = if settings == GuiSettings::default() { None } else { Some(settings) };
    save(&file)?;
    Ok(true)
}

#[derive(Debug)]
pub enum ProfilesError {
    NoHomeDirectory,
    Io(PathBuf, io::Error),
    Parse(PathBuf, String),
    Serialize(String),
}

impl std::fmt::Display for ProfilesError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ProfilesError::NoHomeDirectory =>
                write!(f, "Could not determine the configuration directory. Set HOME or XDG_CONFIG_HOME."),
            ProfilesError::Io(path, e) => write!(f, "Could not access [{}]: {}", path.display(), e),
            ProfilesError::Parse(path, e) => write!(f, "Could not parse profiles file [{}]: {}", path.display(), e),
            ProfilesError::Serialize(e) => write!(f, "Could not serialize profiles: {}", e),
        }
    }
}

pub fn profiles_file_path() -> Result<PathBuf, ProfilesError> {
    if let Some(explicit) = std::env::var_os("NZYME_UTIL_PROFILES_FILE") {
        if !explicit.is_empty() {
            return Ok(PathBuf::from(explicit));
        }
    }

    let config_home = match std::env::var_os("XDG_CONFIG_HOME") {
        Some(dir) if !dir.is_empty() => PathBuf::from(dir),
        _ => match std::env::var_os("HOME") {
            Some(home) if !home.is_empty() => PathBuf::from(home).join(".config"),
            _ => return Err(ProfilesError::NoHomeDirectory),
        },
    };

    Ok(config_home.join(PROFILES_DIR_NAME).join(PROFILES_FILE_NAME))
}

/// Loads the profiles file. Returns `None` if it does not exist. Does not check permissions; callers that load
/// credentials from the file must run the permission check themselves and warn the user.
pub fn load() -> Result<Option<ProfilesFile>, ProfilesError> {
    let path = profiles_file_path()?;

    let content = match fs::read_to_string(&path) {
        Ok(content) => content,
        Err(e) if e.kind() == io::ErrorKind::NotFound => return Ok(None),
        Err(e) => return Err(ProfilesError::Io(path, e)),
    };

    toml::from_str(&content)
        .map(Some)
        .map_err(|e| ProfilesError::Parse(path, e.to_string()))
}

/// Writes the profiles file with mode 0600, creating the directory with mode 0700 if required. The content is
/// written to a temporary file in the same directory first and then renamed into place.
pub fn save(file: &ProfilesFile) -> Result<PathBuf, ProfilesError> {
    let path = profiles_file_path()?;
    let dir = path.parent().ok_or(ProfilesError::NoHomeDirectory)?;

    create_private_dir(dir).map_err(|e| ProfilesError::Io(dir.to_path_buf(), e))?;

    let content = toml::to_string_pretty(file).map_err(|e| ProfilesError::Serialize(e.to_string()))?;

    let tmp_path = dir.join(format!(".{}.{}.tmp", PROFILES_FILE_NAME, std::process::id()));
    write_private_file(&tmp_path, &content).map_err(|e| ProfilesError::Io(tmp_path.clone(), e))?;

    fs::rename(&tmp_path, &path).map_err(|e| {
        let _ = fs::remove_file(&tmp_path);
        ProfilesError::Io(path.clone(), e)
    })?;

    Ok(path)
}

#[cfg(unix)]
fn create_private_dir(dir: &std::path::Path) -> io::Result<()> {
    use std::os::unix::fs::DirBuilderExt;

    if dir.exists() {
        return Ok(());
    }

    fs::DirBuilder::new().recursive(true).mode(0o700).create(dir)
}

#[cfg(not(unix))]
fn create_private_dir(dir: &std::path::Path) -> io::Result<()> {
    fs::create_dir_all(dir)
}

#[cfg(unix)]
fn write_private_file(path: &std::path::Path, content: &str) -> io::Result<()> {
    use std::io::Write;
    use std::os::unix::fs::OpenOptionsExt;

    let mut f = fs::OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o600)
        .open(path)?;

    f.write_all(content.as_bytes())?;
    f.sync_all()
}

#[cfg(not(unix))]
fn write_private_file(path: &std::path::Path, content: &str) -> io::Result<()> {
    fs::write(path, content)
}

/// Masks an API key for display, keeping the prefix and the last four characters.
pub fn mask_api_key(key: &str) -> String {
    const PREFIX_LEN: usize = 4;
    const SUFFIX_LEN: usize = 4;

    if key.len() <= PREFIX_LEN + SUFFIX_LEN {
        return "*".repeat(key.len());
    }

    format!("{}{}{}", &key[..PREFIX_LEN], "*".repeat(key.len() - PREFIX_LEN - SUFFIX_LEN), &key[key.len() - SUFFIX_LEN..])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn masks_api_keys() {
        assert_eq!(mask_api_key("nzk_abcdefghij1234"), "nzk_**********1234");
        assert_eq!(mask_api_key("short"), "*****");
    }

    #[test]
    fn roundtrips_toml() {
        let mut file = ProfilesFile::default();
        file.profiles.insert("prod".to_string(), Profile {
            server: "https://nzyme.example.org:22900".to_string(),
            api_key: "nzk_test".to_string(),
            insecure: true,
            ca_file: None,
            gui: Some(GuiSettings { refresh_seconds: Some(5), organization_id: None, tenant_id: Some("t".into()), taps: vec!["a".into()] }),
        });

        let s = toml::to_string_pretty(&file).unwrap();
        let parsed: ProfilesFile = toml::from_str(&s).unwrap();
        let p = parsed.profiles.get("prod").unwrap();

        assert_eq!(p.server, "https://nzyme.example.org:22900");
        assert_eq!(p.api_key, "nzk_test");
        assert!(p.insecure);
        assert!(p.ca_file.is_none());
        assert_eq!(p.gui.as_ref().unwrap().refresh_seconds, Some(5));
        assert_eq!(p.gui.as_ref().unwrap().taps, vec!["a".to_string()]);
    }

    #[test]
    fn loads_profiles_without_gui_settings() {
        let parsed: ProfilesFile = toml::from_str("[profiles.x]\nserver = \"https://x\"\napi_key = \"nzk_x\"\n").unwrap();
        assert!(parsed.profiles["x"].gui.is_none());
    }
}
