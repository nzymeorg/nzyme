//! Resolves the connection settings for API commands from flags, environment variables and the profiles file.
//!
//! Precedence, highest first:
//!
//! 1. `--server`, `--insecure`, `--ca-file` flags (and their `NZYME_SERVER` environment variable).
//! 2. The `NZYME_API_KEY` environment variable for the key.
//! 3. The selected profile (`--profile`, `NZYME_PROFILE`, or `default`).

use std::path::PathBuf;
use crate::api::client::{is_api_key, ClientConfig, API_KEY_PREFIX};
use crate::arguments::ConnectionArgs;
use crate::profiles::file::{self, DEFAULT_PROFILE_NAME};
use crate::profiles::permissions;

pub const API_KEY_ENV: &str = "NZYME_API_KEY";

pub struct ResolvedConnection {
    pub config: ClientConfig,
}

pub fn resolve(args: &ConnectionArgs) -> Result<ResolvedConnection, String> {
    let profile_name = args.profile.clone().unwrap_or_else(|| DEFAULT_PROFILE_NAME.to_string());
    let profile_requested_explicitly = args.profile.is_some();

    // Load the profile, if there is a profiles file.
    let profiles_file = file::load().map_err(|e| e.to_string())?;
    let profile = match &profiles_file {
        Some(f) => {
            let path = file::profiles_file_path().map_err(|e| e.to_string())?;
            let problems = permissions::check(&path);
            permissions::print_warning(&path, &problems);

            match f.profiles.get(&profile_name) {
                Some(p) => Some(p.clone()),
                None if profile_requested_explicitly => {
                    return Err(format!("Profile [{}] does not exist in [{}]. Run `nzyme-util api profiles \
                        list` to see all profiles.", profile_name, path.display()));
                }
                None => None,
            }
        }
        None if profile_requested_explicitly => {
            return Err(format!("Profile [{}] was requested but there is no profiles file yet. Create one with \
                `nzyme-util api profiles add`.", profile_name));
        }
        None => None,
    };

    let server = args.server.clone()
        .or_else(|| profile.as_ref().map(|p| p.server.clone()))
        .ok_or_else(|| "No Nzyme server configured. Pass --server, set NZYME_SERVER, or create a profile with \
            `nzyme-util api profiles add`.".to_string())?;

    let env_key = std::env::var(API_KEY_ENV).ok().map(|k| k.trim().to_string()).filter(|k| !k.is_empty());

    let (api_key, key_source) = match (env_key, profile.as_ref()) {
        (Some(k), _) => (k, format!("{} environment variable", API_KEY_ENV)),
        (None, Some(p)) => (p.api_key.clone(), format!("profile [{}]", profile_name)),
        (None, None) => return Err(format!("No API key configured. Set {} or create a profile with \
            `nzyme-util api profiles add`. You can create API keys on your Nzyme user profile page.",
            API_KEY_ENV)),
    };

    if !is_api_key(&api_key) {
        return Err(format!("The API key from the {} does not look like a Nzyme API key. Nzyme API keys start \
            with [{}]. Make sure you are not passing a session token or a Nzyme Connect key.",
            key_source, API_KEY_PREFIX));
    }

    let insecure = args.insecure || profile.as_ref().map(|p| p.insecure).unwrap_or(false);
    let ca_file = args.ca_file.clone()
        .or_else(|| profile.as_ref().and_then(|p| p.ca_file.clone()))
        .map(PathBuf::from);

    Ok(ResolvedConnection {
        config: ClientConfig { server, api_key, insecure, ca_file },
    })
}
