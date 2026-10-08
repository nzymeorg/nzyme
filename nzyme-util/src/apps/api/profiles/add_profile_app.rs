use std::io::{BufRead, IsTerminal};
use crate::api::client::{is_api_key, API_KEY_PREFIX};
use crate::apps::api::{BOLD, FG_GREEN, FG_RED, FG_YELLOW, RESET};
use crate::arguments::ConnectionArgs;
use crate::exit_codes::{EX_CONFIG, EX_DATAERR, EX_IOERR, EX_USAGE};
use crate::profiles::file::{self, Profile, ProfilesFile, DEFAULT_PROFILE_NAME};
use crate::profiles::permissions;

pub fn run(connection: &ConnectionArgs, name: Option<String>, api_key_stdin: bool) {
    let name = name.unwrap_or_else(|| DEFAULT_PROFILE_NAME.to_string());

    if name.trim().is_empty() {
        eprintln!("{FG_RED}[x] ERROR:{RESET} Profile name must not be empty.");
        std::process::exit(EX_USAGE);
    }

    let server = match &connection.server {
        Some(server) if !server.trim().is_empty() => server.trim().trim_end_matches('/').to_string(),
        _ => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} Pass the server URL with --server, for example \
                --server https://nzyme.example.org:22900");
            std::process::exit(EX_USAGE);
        }
    };

    if !(server.starts_with("https://") || server.starts_with("http://")) {
        eprintln!("{FG_RED}[x] ERROR:{RESET} The server URL must start with https:// or http://.");
        std::process::exit(EX_USAGE);
    }

    eprintln!("{BOLD}==> Add Nzyme API Profile{RESET}");
    eprintln!("    Profile : {}", name);
    eprintln!("    Server  : {}", server);
    if connection.insecure {
        eprintln!("    TLS     : {FG_YELLOW}certificate verification disabled{RESET}");
    }
    if let Some(ca_file) = &connection.ca_file {
        eprintln!("    CA File : {}", ca_file);
    }
    eprintln!();
    eprintln!("    Create an API key on your Nzyme user profile page and paste it below. The key is never");
    eprintln!("    shown while you type.\n");

    let api_key = read_api_key(api_key_stdin);

    if !is_api_key(&api_key) {
        eprintln!("{FG_RED}[x] ERROR:{RESET} This does not look like a Nzyme API key. Keys start with [{}].",
                  API_KEY_PREFIX);
        std::process::exit(EX_DATAERR);
    }

    let mut profiles = match file::load() {
        Ok(Some(existing)) => existing,
        Ok(None) => ProfilesFile::default(),
        Err(e) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} {}", e);
            std::process::exit(EX_CONFIG);
        }
    };

    let replaced = profiles.profiles.insert(name.clone(), Profile {
        server,
        api_key,
        insecure: connection.insecure,
        ca_file: connection.ca_file.clone(),
        gui: None,
    }).is_some();

    let path = match file::save(&profiles) {
        Ok(path) => path,
        Err(e) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} {}", e);
            std::process::exit(EX_IOERR);
        }
    };

    if replaced {
        eprintln!("{FG_GREEN}[*] Profile [{}] replaced in [{}].{RESET}", name, path.display());
    } else {
        eprintln!("{FG_GREEN}[*] Profile [{}] saved to [{}].{RESET}", name, path.display());
    }

    // The file was just written with mode 0600, but the directory may have been created by something else.
    let problems = permissions::check(&path);
    permissions::print_warning(&path, &problems);

    if name == DEFAULT_PROFILE_NAME {
        eprintln!("\n    Try it: nzyme-util api infra taps list");
    } else {
        eprintln!("\n    Try it: nzyme-util api --profile {} infra taps list", name);
    }
}

fn read_api_key(from_stdin: bool) -> String {
    let stdin = std::io::stdin();

    if from_stdin || !stdin.is_terminal() {
        let mut line = String::new();
        if let Err(e) = stdin.lock().read_line(&mut line) {
            eprintln!("{FG_RED}[x] ERROR:{RESET} Could not read API key from stdin: {}", e);
            std::process::exit(EX_IOERR);
        }
        return line.trim().to_string();
    }

    match rpassword::prompt_password("    API key: ") {
        Ok(key) => key.trim().to_string(),
        Err(e) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} Could not read API key: {}", e);
            std::process::exit(EX_IOERR);
        }
    }
}
