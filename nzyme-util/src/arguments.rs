use clap::{Parser, Subcommand, Args, ValueEnum};
use crate::tools::parse_duration_minutes;

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
    /// What needs attention, what is watching, and what changed since you last looked
    Briefing(BriefingCommand),
    /// Ethernet assets and protocols
    Ethernet(EthernetCommand),
    /// Search everything you can access: assets, access points, clients, devices, alerts, findings,
    /// configuration and recorded network data
    Search(SearchArgs),
    /// Interactive terminal interface for Nzyme
    Gui,
}

#[derive(Args, Debug)]
pub struct SearchArgs {
    /// What to search for: a MAC address or the start of one, an IP address, a network in CIDR notation, a UUID,
    /// a fingerprint or any text. Several words are searched as one phrase
    #[arg(value_name = "QUERY", required = true, num_args = 1..)]
    pub query: Vec<String>,

    /// Only this organization. Without it, all organizations are searched, which needs super administrator
    /// permissions
    #[arg(long, value_name = "UUID")]
    pub organization_id: Option<String>,

    /// Only this tenant. Without it, all tenants of the organization are searched, which needs organization
    /// administrator permissions
    #[arg(long, value_name = "UUID", requires = "organization_id")]
    pub tenant_id: Option<String>,

    /// Results per group, at most 50
    #[arg(long, value_name = "COUNT", default_value_t = 10, value_parser = clap::value_parser!(u32).range(1..=50))]
    pub limit: u32,

    /// Only these sources, by ID. A group that has more results names its ID
    #[arg(long, value_name = "ID,ID", value_delimiter = ',')]
    pub source: Vec<String>,

    /// Skip this many results of the one source passed with --source, at most 1000
    #[arg(long, value_name = "COUNT", default_value_t = 0, requires = "source",
          value_parser = clap::value_parser!(u32).range(0..=1000))]
    pub offset: u32,

    /// Only configuration and findings. Recorded network data, which takes longer, is not searched
    #[arg(long, conflicts_with = "source")]
    pub quick: bool,

    /// Search recorded network data of the last DURATION, like 30m, 6h or 7d. Without a time range, the last 24
    /// hours are searched, a full MAC address is looked up in all recorded WiFi and Bluetooth data, and a session
    /// key or certificate fingerprint in all recorded data. Configuration and findings are always searched in full
    #[arg(long, value_name = "DURATION", value_parser = parse_duration_minutes, group = "window")]
    pub last: Option<u32>,

    /// Search recorded network data of a named range
    #[arg(long, value_enum, value_name = "NAME", group = "window")]
    pub range: Option<NamedTimeRange>,

    /// Search recorded network data from this ISO 8601 timestamp, together with --to
    #[arg(long, value_name = "TIMESTAMP", group = "window", requires = "to")]
    pub from: Option<String>,

    /// End of the time range, together with --from
    #[arg(long, value_name = "TIMESTAMP", requires = "from")]
    pub to: Option<String>,
}

#[derive(Args, Debug)]
pub struct EthernetCommand {
    #[command(subcommand)]
    pub command: EthernetSubcommand,
}

#[derive(Subcommand, Debug)]
pub enum EthernetSubcommand {
    /// Ethernet assets: every device seen on the wired network
    Assets(AssetsCommand),
}

#[derive(Args, Debug)]
pub struct AssetsCommand {
    #[command(subcommand)]
    pub command: AssetsSubcommand,
}

#[derive(ValueEnum, Clone, Copy, Debug, PartialEq, Eq)]
pub enum AssetSection {
    All,
    /// Summary, roles, protocols, inbound ports, infrastructure and external networks
    Overview,
    /// Hour-of-day heatmap and daily calendar
    Activity,
    /// Peers, constellation and top peer tables
    Communication,
    /// World map of the external networks
    Map,
    /// Change timeline
    Changes,
    /// Alerts, findings and exclusions
    Detections,
    /// Risk signals
    Risks,
    /// Hostnames, IP addresses, context and fingerprints
    Identity,
}

#[derive(Subcommand, Debug)]
pub enum AssetsSubcommand {
    /// The asset details page of the web interface for one MAC address
    Show {
        /// MAC address of the asset. Colons, dashes, dots and bare hex are accepted
        #[arg(long, value_name = "MAC")]
        mac: String,

        /// Only this organization. Without it, all organizations are searched, which needs super administrator
        /// permissions
        #[arg(long, value_name = "UUID")]
        organization_id: Option<String>,

        /// Only this tenant. Without it, all tenants of the organization are searched, which needs organization
        /// administrator permissions
        #[arg(long, value_name = "UUID", requires = "organization_id")]
        tenant_id: Option<String>,

        /// Time window of the communication, changes and risk sections, like 24h, 7d or 30d
        #[arg(long, value_name = "DURATION", value_parser = parse_duration_minutes, default_value = "7d")]
        last: u32,

        /// Days of activity history for the heatmap and calendar, at most 730
        #[arg(long, value_name = "DAYS", default_value_t = 90, value_parser = clap::value_parser!(u32).range(1..=730))]
        activity_days: u32,

        /// Only these sections, in page order. Default: all
        #[arg(long, value_enum, value_name = "SECTION", value_delimiter = ',')]
        section: Vec<AssetSection>,
    },
}

#[derive(Args, Debug)]
pub struct BriefingCommand {
    #[command(subcommand)]
    pub command: BriefingSubcommand,
}

#[derive(ValueEnum, Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum BriefingType {
    /// Across all subsystems
    Main,
    /// WiFi
    Dot11,
    Ethernet,
    Bluetooth,
}

impl BriefingType {
    pub const ALL: [BriefingType; 4] = [BriefingType::Main, BriefingType::Dot11, BriefingType::Ethernet, BriefingType::Bluetooth];

    pub fn api_name(&self) -> &'static str {
        match self {
            BriefingType::Main => "MAIN",
            BriefingType::Dot11 => "DOT11",
            BriefingType::Ethernet => "ETHERNET",
            BriefingType::Bluetooth => "BLUETOOTH",
        }
    }

    pub fn display_name(&self) -> &'static str {
        match self {
            BriefingType::Main => "Main",
            BriefingType::Dot11 => "WiFi",
            BriefingType::Ethernet => "Ethernet",
            BriefingType::Bluetooth => "Bluetooth",
        }
    }
}

#[derive(ValueEnum, Clone, Copy, Debug, PartialEq)]
pub enum BriefingSince {
    /// Since you last opened this briefing, in the web interface or here. The last 24 hours on a first visit,
    /// capped at 7 days
    LastVisit,
    /// Since your previous session ended
    LastSession,
}

impl BriefingSince {
    pub fn api_name(&self) -> &'static str {
        match self {
            BriefingSince::LastVisit => "LAST_VISIT",
            BriefingSince::LastSession => "LAST_SESSION",
        }
    }
}

#[derive(ValueEnum, Clone, Copy, Debug, PartialEq)]
pub enum NamedTimeRange {
    Today,
    Yesterday,
    WeekToDate,
    MonthToDate,
}

impl NamedTimeRange {
    pub fn api_name(&self) -> &'static str {
        match self {
            NamedTimeRange::Today => "today",
            NamedTimeRange::Yesterday => "yesterday",
            NamedTimeRange::WeekToDate => "week_to_date",
            NamedTimeRange::MonthToDate => "month_to_date",
        }
    }
}

#[derive(Subcommand, Debug)]
pub enum BriefingSubcommand {
    /// Show a briefing. Like opening it in the web interface, this records your visit: the next briefing in
    /// last-visit mode starts where this one ended, unless it runs within ten minutes
    Show {
        /// Which briefing
        #[arg(long, value_enum, default_value_t = BriefingType::Main, value_name = "TYPE")]
        r#type: BriefingType,

        /// Only this organization. Without it, all organizations are briefed, which needs super administrator
        /// permissions
        #[arg(long, value_name = "UUID")]
        organization_id: Option<String>,

        /// Only this tenant. Without it, all tenants of the organization are briefed, which needs organization
        /// administrator permissions
        #[arg(long, value_name = "UUID", requires = "organization_id")]
        tenant_id: Option<String>,

        /// Only these taps. Default: all taps of the tenant you can access, like the web interface
        #[arg(long, value_name = "UUID,UUID", value_delimiter = ',')]
        taps: Vec<String>,

        /// Where the window starts
        #[arg(long, value_enum, default_value_t = BriefingSince::LastVisit, value_name = "MODE", group = "window")]
        since: BriefingSince,

        /// Fixed window instead: everything in the last DURATION, like 30m, 6h or 2d
        #[arg(long, value_name = "DURATION", value_parser = parse_duration_minutes, group = "window")]
        last: Option<u32>,

        /// Fixed window instead: a named range
        #[arg(long, value_enum, value_name = "NAME", group = "window")]
        range: Option<NamedTimeRange>,

        /// Fixed window instead: from this ISO 8601 timestamp, together with --to
        #[arg(long, value_name = "TIMESTAMP", group = "window", requires = "to")]
        from: Option<String>,

        /// End of the fixed window, together with --from
        #[arg(long, value_name = "TIMESTAMP", requires = "from")]
        to: Option<String>,
    },

    /// Mark a briefing as seen now, so the next one in last-visit mode starts a fresh window
    Seen {
        /// Which briefing
        #[arg(long, value_enum, default_value_t = BriefingType::Main, value_name = "TYPE")]
        r#type: BriefingType,
    },
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
