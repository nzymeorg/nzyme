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
    /// Base URL of the Nzyme REST API, overrides the profile
    #[arg(long, global = true, env = "NZYME_SERVER", value_name = "URL")]
    pub server: Option<String>,

    /// Name of the connection profile
    #[arg(long, global = true, env = "NZYME_PROFILE", value_name = "NAME")]
    pub profile: Option<String>,

    /// Do not verify the TLS certificate
    #[arg(long, global = true)]
    pub insecure: bool,

    /// PEM file with CA certificates to trust
    #[arg(long, global = true, value_name = "FILE")]
    pub ca_file: Option<String>,

    /// Print raw JSON responses
    #[arg(long, global = true)]
    pub json: bool,
}

#[derive(Subcommand, Debug)]
pub enum Command {
    /// Flash and verify firmware of Nzyme devices
    Firmware(FirmwareCommand),
    /// List connected Nzyme devices
    Devices(DevicesCommand),
    /// Verify the signature of Nzyme software releases
    Release(ReleaseCommand),
    /// Configure and test Sona sensors
    Sona(SonaCommand),
    /// Talk to the Nzyme REST API
    Api(ApiCommand),
    /// Watch Nzyme tap telemetry in real time
    Tap(TapCommand),
}

#[derive(Args, Debug)]
pub struct TapCommand {
    #[command(subcommand)]
    pub command: TapSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum TapSubcommand {
    /// Live dashboard of a running tap
    Top {
        /// Address of the tap telemetry feed
        #[arg(long, default_value = "127.0.0.1:22910", value_name = "HOST:PORT", conflicts_with = "replay")]
        address: String,

        /// Record the session to this file
        #[arg(long, value_name = "FILE", conflicts_with = "replay")]
        record: Option<String>,

        /// Play back a recorded session
        #[arg(long, value_name = "FILE")]
        replay: Option<String>,

        /// Playback speed factor
        #[arg(long, default_value_t = 1.0, value_name = "FACTOR", requires = "replay")]
        speed: f64,
    },
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
    /// Manage connection profiles for the Nzyme REST API
    Profiles(ProfilesCommand),
    /// Inspect Nzyme infrastructure
    Infra(InfraCommand),
    /// Interactive terminal interface for Nzyme
    Gui,
}

#[derive(Args, Debug)]
pub struct InfraCommand {
    #[command(subcommand)]
    pub command: InfraSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum InfraSubcommand {
    /// Inspect Nzyme cluster nodes
    Nodes(NodesCommand),
    /// Inspect Nzyme taps
    Taps(TapsCommand),
}

#[derive(Args, Debug)]
pub struct FirmwareCommand {
    #[command(subcommand)]
    pub command: FirmwareSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum FirmwareSubcommand {
    /// Flash firmware onto a connected device
    Flash {
        /// Firmware file
        #[arg(long)]
        firmware_file: String,

        /// Serial number of the device
        #[arg(long)]
        serial: String,
    },

    /// Verify the signature of a firmware file
    Verify {
        /// Firmware file
        #[arg(long)]
        firmware_file: String,

        /// Nzyme public key file
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
    /// List connected devices
    List,
}

#[derive(Args, Debug)]
pub struct ReleaseCommand {
    #[command(subcommand)]
    pub command: ReleaseSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum ReleaseSubcommand {
    /// Verify the signature of a release file
    Verify {
        /// Release file
        #[arg(long)]
        release_file: String,

        /// Signature file
        #[arg(long)]
        signature_file: String,

        /// Nzyme public key file
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
    /// Generate a tap configuration snippet for connected Sona sensors
    GenerateConfig,
    /// Interactive test of connected Sona sensors
    Test
}

#[derive(Args, Debug)]
pub struct ProfilesCommand {
    #[command(subcommand)]
    pub command: ProfilesSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum ProfilesSubcommand {
    /// Store a connection profile
    Add {
        /// Name of the profile
        #[arg(long)]
        name: Option<String>,

        /// Read the API key from stdin
        #[arg(long)]
        api_key_stdin: bool,
    },

    /// List stored connection profiles
    List,

    /// Delete a connection profile
    Remove {
        /// Name of the profile
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
    /// List cluster nodes
    List,
}

#[derive(Args, Debug)]
pub struct TapsCommand {
    #[command(subcommand)]
    pub command: TapsSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum TapsSubcommand {
    /// List taps
    List {
        /// Only taps of this organization
        #[arg(long, value_name = "UUID")]
        organization_id: Option<String>,

        /// Only taps of this tenant
        #[arg(long, value_name = "UUID", requires = "organization_id")]
        tenant_id: Option<String>,
    },

    /// Show metrics of a tap
    Metrics {
        /// UUID of the tap
        #[arg(long, value_name = "UUID")]
        uuid: String,
    },
}
