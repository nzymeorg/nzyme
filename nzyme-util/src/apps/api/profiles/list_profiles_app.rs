use crate::apps::api::{BOLD, FG_RED, RESET};
use crate::exit_codes::EX_CONFIG;
use crate::profiles::file::{self, mask_api_key};
use crate::profiles::permissions;

pub fn run() {
    let path = match file::profiles_file_path() {
        Ok(path) => path,
        Err(e) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} {}", e);
            std::process::exit(EX_CONFIG);
        }
    };

    eprintln!("{BOLD}==> Nzyme API Profiles{RESET}");
    eprintln!("    File: {}\n", path.display());

    let profiles = match file::load() {
        Ok(Some(profiles)) => profiles,
        Ok(None) => {
            println!("No profiles file yet. Create one with `nzyme-util api profiles add`.");
            return;
        }
        Err(e) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} {}", e);
            std::process::exit(EX_CONFIG);
        }
    };

    let problems = permissions::check(&path);
    permissions::print_warning(&path, &problems);

    if profiles.profiles.is_empty() {
        println!("The profiles file contains no profiles.");
        return;
    }

    for (name, profile) in &profiles.profiles {
        println!(
            "Profile:\n\
             ├─ Name:     {BOLD}{}{RESET}\n\
             ├─ Server:   {}\n\
             ├─ API Key:  {}\n\
             ├─ Insecure: {}\n\
             └─ CA File:  {}\n",
            name,
            profile.server,
            mask_api_key(&profile.api_key),
            if profile.insecure { "yes (TLS verification disabled)" } else { "no" },
            profile.ca_file.as_deref().unwrap_or("<none>"),
        );
    }

    eprintln!("[*] {} profiles.", profiles.profiles.len());
}
