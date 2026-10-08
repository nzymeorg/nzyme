use clap::{Parser, Subcommand, Args};

#[derive(Parser, Debug)]
#[command(
    name = "nzyme-util",
    version,
    author,
    about = "Nzyme CLI Utility",

    subcommand_required = false,
    arg_required_else_help = true
)]
pub struct CliArguments {
    #[command(subcommand)]
    pub command: Option<Command>,
}

#[derive(Args, Debug, Clone)]
#[command(next_help_heading = "Nzyme REST API connection")]
pub struct ConnectionArgs {
    // Base URL of the Nzyme REST API, for example https://nzyme.example.org:22900. Overrides the profile.
    #[arg(long, global = true, env = "NZYME_SERVER", value_name = "URL")]
    pub server: Option<String>,

    // Name of the profile to use from the profiles file. Defaults to "default".
    #[arg(long, global = true, env = "NZYME_PROFILE", value_name = "NAME")]
    pub profile: Option<String>,

    // Do not verify the TLS certificate of the server.
    #[arg(long, global = true)]
    pub insecure: bool,

    // PEM file with CA certificates to trust instead of the built-in root certificates.
    #[arg(long, global = true, value_name = "FILE")]
    pub ca_file: Option<String>,

    // Print raw JSON API responses to stdout instead of human-readable output.
    #[arg(long, global = true)]
    pub json: bool,
}

#[derive(Subcommand, Debug)]
pub enum Command {
    Firmware(FirmwareCommand),
    Devices(DevicesCommand),
    Release(ReleaseCommand),
    Sona(SonaCommand),
    // Talk to the Nzyme REST API
    Api(ApiCommand),
}

#[derive(Args, Debug)]
pub struct ApiCommand {
    #[command(flatten)]
    pub connection: ConnectionArgs,

    #[command(subcommand)]
    pub command: ApiSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum ApiSubcommand {
    // Manage connection profiles for the Nzyme REST API
    Profiles(ProfilesCommand),
    // Inspect Nzyme infrastructure: cluster nodes and taps
    Infra(InfraCommand),
    // Interactive terminal dashboard with live WiFi data. All options are set from a menu inside the dashboard.
    Gui,
}

#[derive(Args, Debug)]
pub struct InfraCommand {
    #[command(subcommand)]
    pub command: InfraSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum InfraSubcommand {
    // Inspect Nzyme cluster nodes
    Nodes(NodesCommand),
    // Inspect Nzyme taps
    Taps(TapsCommand),
}

#[derive(Args, Debug)]
pub struct FirmwareCommand {
    #[command(subcommand)]
    pub command: FirmwareSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum FirmwareSubcommand {
    Flash {
        #[arg(long)]
        firmware_file: String,

        #[arg(long)]
        serial: String,
    },

    Verify {
        #[arg(long)]
        firmware_file: String,

        #[arg(long)]
        public_key_file: String,
    },
}

#[derive(Args, Debug)]
pub struct DevicesCommand {
    #[command(subcommand)]
    pub command: DevicesSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum DevicesSubcommand {
    List,
}

#[derive(Args, Debug)]
pub struct ReleaseCommand {
    #[command(subcommand)]
    pub command: ReleaseSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum ReleaseSubcommand {
    Verify {
        #[arg(long)]
        release_file: String,

        #[arg(long)]
        signature_file: String,

        #[arg(long)]
        public_key_file: String,
    },
}

#[derive(Args, Debug)]
pub struct SonaCommand {
    #[command(subcommand)]
    pub command: SonaSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum SonaSubcommand {
    GenerateConfig,
    Test
}

#[derive(Args, Debug)]
pub struct ProfilesCommand {
    #[command(subcommand)]
    pub command: ProfilesSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum ProfilesSubcommand {
    /*
     * Store a connection profile. Uses the --server, --insecure and --ca-file options and prompts
     * for the API key. The profiles file is created with permissions that only allow access by
     * your user.
     */
    Add {
        // Name of the profile. Defaults to "default".
        #[arg(long)]
        name: Option<String>,

        // Read the API key from the first line of stdin instead of prompting for it.
        #[arg(long)]
        api_key_stdin: bool,
    },

    // List all stored connection profiles. API keys are masked.
    List,

    // Delete a stored connection profile.
    Remove {
        // Name of the profile.
        #[arg(long)]
        name: String,
    },
}

#[derive(Args, Debug)]
pub struct NodesCommand {
    #[command(subcommand)]
    pub command: NodesSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum NodesSubcommand {
    // List all cluster nodes with their status and metrics. Requires super administrator permissions.
    List,
}

#[derive(Args, Debug)]
pub struct TapsCommand {
    #[command(subcommand)]
    pub command: TapsSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum TapsSubcommand {
    /*
     * List taps with their status and metrics. Without --organization-id, all organizations and
     * tenants are listed, which requires super administrator permissions. Organization
     * administrators pass  --organization-id, other users pass --organization-id and --tenant-id.
     */
    List {
        // Only list taps of this organization.
        #[arg(long, value_name = "UUID")]
        organization_id: Option<String>,

        // Only list taps of this tenant. Requires --organization-id.
        #[arg(long, value_name = "UUID", requires = "organization_id")]
        tenant_id: Option<String>,
    },

    /*
     * Show the current gauge and timer metrics of a single tap. Requires organization
     * administrator permissions.
     */
    Metrics {
        // UUID of the tap.
        #[arg(long, value_name = "UUID")]
        uuid: String,
    },
}
