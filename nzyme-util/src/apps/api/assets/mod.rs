pub mod ansi;
pub mod doc;
pub mod labels;
pub mod profile;
pub mod worldmap;
mod worldmap_data;

use std::io::IsTerminal;

use serde_json::{json, Value};

use crate::api::client::ApiClient;
use crate::apps::api::assets::doc::Section;
use crate::apps::api::assets::profile::{AssetProfile, LocatedAsset, LookupScope, ProfileOptions};
use crate::apps::api::scopes::discover_scopes;
use crate::apps::api::{connect, exit_with_api_error, print_json, BOLD, FG_RED, FG_YELLOW, RESET};
use crate::arguments::{AssetSection, ConnectionArgs};
use crate::exit_codes::{EX_DATAERR, EX_USAGE};
use crate::tools::now_unix;

const DEFAULT_WIDTH: usize = 120;
const MAX_WIDTH: usize = 200;

pub struct ShowOptions {
    pub mac: String,
    pub organization_id: Option<String>,
    pub tenant_id: Option<String>,
    pub last_minutes: u32,
    pub activity_days: u32,
    pub sections: Vec<AssetSection>,
}

impl AssetSection {
    fn sections(selected: &[AssetSection]) -> Vec<Section> {
        if selected.is_empty() || selected.contains(&AssetSection::All) {
            return Section::ALL.to_vec();
        }
        Section::ALL.iter().copied().filter(|s| selected.iter().any(|a| a.matches(*s))).collect()
    }

    fn matches(&self, section: Section) -> bool {
        matches!((self, section),
            (AssetSection::Overview, Section::Overview) | (AssetSection::Activity, Section::Activity)
            | (AssetSection::Communication, Section::Communication) | (AssetSection::Map, Section::Map)
            | (AssetSection::Changes, Section::Changes) | (AssetSection::Detections, Section::Detections)
            | (AssetSection::Risks, Section::Risks) | (AssetSection::Identity, Section::Identity))
    }
}

pub fn show(connection: &ConnectionArgs, options: ShowOptions) {
    let Some(mac) = labels::normalize_mac(&options.mac) else {
        eprintln!("{FG_RED}[x] ERROR:{RESET} [{}] is not a MAC address. Use six hex pairs like 94:83:C4:CE:D3:B0.", options.mac);
        std::process::exit(EX_USAGE);
    };

    let client = connect(connection);
    let scopes: Vec<LookupScope> = discover_scopes(&client, options.organization_id.clone(), options.tenant_id.clone())
        .into_iter()
        .map(|s| LookupScope {
            organization_id: s.organization_id, organization_name: s.organization_name,
            tenant_id: s.tenant_id, tenant_name: s.tenant_name,
        })
        .collect();

    eprintln!("{FG_YELLOW}[>] Looking up {} in {} {}...{RESET}", mac, scopes.len(), if scopes.len() == 1 { "tenant" } else { "tenants" });
    let located = match profile::lookup(&client, &scopes, &mac) {
        Ok(located) => located,
        Err(e) => exit_with_api_error(&e),
    };

    if located.is_empty() {
        eprintln!("{FG_RED}[x] ERROR:{RESET} No asset with MAC address {} in the selected scope.", mac);
        std::process::exit(EX_DATAERR);
    }

    let profile_options = ProfileOptions {
        activity_days: options.activity_days,
        ..ProfileOptions::last_minutes(options.last_minutes)
    };
    let profiles: Vec<AssetProfile> = located.into_iter()
        .map(|asset| load_with_progress(&client, asset, &profile_options))
        .collect();

    if connection.json {
        let out: Vec<Value> = profiles.iter().map(|p| json!({
            "organization_id": p.located.scope.organization_id,
            "organization_name": p.located.scope.organization_name,
            "tenant_id": p.located.scope.tenant_id,
            "tenant_name": p.located.scope.tenant_name,
            "profile": p.raw,
        })).collect();
        print_json(&Value::Array(out));
        return;
    }

    let color = std::io::stdout().is_terminal() && std::env::var_os("NO_COLOR").is_none();
    let width = terminal_width();
    let sections = AssetSection::sections(&options.sections);
    let now = now_unix();

    eprintln!("{BOLD}==> Nzyme Ethernet Asset{RESET}");
    eprintln!("    Server: {}\n", client.base_url());

    for (i, profile) in profiles.iter().enumerate() {
        if profiles.len() > 1 {
            println!("{BOLD}Match {} of {}: organization {} / tenant {}{RESET}", i + 1, profiles.len(),
                     profile.located.scope.organization_name.as_deref().unwrap_or(&profile.located.scope.organization_id),
                     profile.located.scope.tenant_label());
        }

        for line in doc::header(profile, width, now) {
            println!("{}", ansi::line_to_ansi(&line, color));
        }
        for section in &sections {
            for line in doc::build(profile, *section, width, now) {
                println!("{}", ansi::line_to_ansi(&line, color));
            }
        }

        for (name, error) in profile.section_errors() {
            eprintln!("{FG_YELLOW}[!] WARNING:{RESET} The {} section could not be loaded: {}", name, error);
        }
    }
}

fn load_with_progress(client: &ApiClient, asset: LocatedAsset, options: &ProfileOptions) -> AssetProfile {
    eprintln!("{FG_YELLOW}[>] Loading the profile of asset {} in tenant {}...{RESET}", asset.uuid(), asset.scope.tenant_label());
    profile::load(client, asset, options)
}

fn terminal_width() -> usize {
    if let Ok(columns) = std::env::var("COLUMNS") {
        if let Ok(n) = columns.trim().parse::<usize>() {
            return n.clamp(60, MAX_WIDTH);
        }
    }
    match ratatui::crossterm::terminal::size() {
        Ok((columns, _)) if columns > 0 => (columns as usize).clamp(60, MAX_WIDTH),
        _ => DEFAULT_WIDTH,
    }
}
