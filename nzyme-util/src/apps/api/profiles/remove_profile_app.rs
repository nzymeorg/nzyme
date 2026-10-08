use crate::apps::api::{FG_GREEN, FG_RED, RESET};
use crate::exit_codes::{EX_CONFIG, EX_DATAERR, EX_IOERR};
use crate::profiles::file;

pub fn run(name: String) {
    let mut profiles = match file::load() {
        Ok(Some(profiles)) => profiles,
        Ok(None) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} There is no profiles file.");
            std::process::exit(EX_DATAERR);
        }
        Err(e) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} {}", e);
            std::process::exit(EX_CONFIG);
        }
    };

    if profiles.profiles.remove(&name).is_none() {
        eprintln!("{FG_RED}[x] ERROR:{RESET} Profile [{}] does not exist.", name);
        std::process::exit(EX_DATAERR);
    }

    match file::save(&profiles) {
        Ok(path) => eprintln!("{FG_GREEN}[*] Profile [{}] removed from [{}].{RESET}", name, path.display()),
        Err(e) => {
            eprintln!("{FG_RED}[x] ERROR:{RESET} {}", e);
            std::process::exit(EX_IOERR);
        }
    }

    eprintln!("    The API key stored in this profile still works. Delete it on your Nzyme user profile page if \
        you no longer need it.");
}
