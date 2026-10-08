mod exit_codes;
mod firmware;
mod usb;
mod arguments;
mod apps;
mod tools;
mod peripherals;
mod api;
mod profiles;

use clap::Parser;
use crate::apps::firmware::{flash_firmware_app, verify_firmware_app};
use crate::apps::devices::list_devices_app;
use crate::apps::release::verify_release_app;
use crate::apps::sona::{generate_sona_configuration_app, sona_test_app};
use crate::apps::api::{gui, list_nodes_app, list_taps_app, tap_metrics_app};
use crate::apps::api::profiles::{add_profile_app, list_profiles_app, remove_profile_app};

use crate::arguments::{ApiSubcommand, CliArguments, Command, DevicesSubcommand, FirmwareSubcommand, InfraSubcommand, NodesSubcommand, ProfilesSubcommand, ReleaseSubcommand, SonaSubcommand, TapsSubcommand};
use crate::exit_codes::EX_OK;

fn main() {
    let arguments = CliArguments::parse();

    match arguments.command {
        Some(Command::Firmware(fw)) => match fw.command {
            FirmwareSubcommand::Flash { firmware_file, serial } => {
                // $ nzyme-util firmware flash
                flash_firmware_app::run(firmware_file, serial);
            }

            FirmwareSubcommand::Verify { firmware_file, public_key_file } => {
                // $ nzyme-util firmware flash
                verify_firmware_app::run(firmware_file, public_key_file);
            }
        },

        Some(Command::Devices(dev_cmd)) => match dev_cmd.command {
            DevicesSubcommand::List => {
                // $ nzyme-util devices list
                list_devices_app::run();
            }
        },

        Some(Command::Release(fw)) => match fw.command {
            ReleaseSubcommand::Verify { release_file, signature_file, public_key_file } => {
                // $ nzyme-util release verify
                verify_release_app::run(release_file, signature_file, public_key_file);
            }
        },

        Some(Command::Sona(fw)) => match fw.command {
            SonaSubcommand::GenerateConfig => {
                // $ nzyme-util sona generate-config
                generate_sona_configuration_app::run();
            }
            SonaSubcommand::Test => {
                // $ nzyme-util sona test
                sona_test_app::run();
            }
        },

        Some(Command::Api(api)) => {
            let connection = &api.connection;

            match api.command {
                ApiSubcommand::Profiles(profiles) => match profiles.command {
                    ProfilesSubcommand::Add { name, api_key_stdin } => {
                        // $ nzyme-util api profiles add
                        add_profile_app::run(connection, name, api_key_stdin);
                    }
                    ProfilesSubcommand::List => {
                        // $ nzyme-util api profiles list
                        list_profiles_app::run();
                    }
                    ProfilesSubcommand::Remove { name } => {
                        // $ nzyme-util api profiles remove
                        remove_profile_app::run(name);
                    }
                },

                ApiSubcommand::Gui => {
                    // $ nzyme-util api gui
                    gui::run(connection);
                }

                ApiSubcommand::Infra(infra) => match infra.command {
                    InfraSubcommand::Nodes(nodes) => match nodes.command {
                        NodesSubcommand::List => {
                            // $ nzyme-util api infra nodes list
                            list_nodes_app::run(connection);
                        }
                    },

                    InfraSubcommand::Taps(taps) => match taps.command {
                        TapsSubcommand::List { organization_id, tenant_id } => {
                            // $ nzyme-util api infra taps list
                            list_taps_app::run(connection, organization_id, tenant_id);
                        }
                        TapsSubcommand::Metrics { uuid } => {
                            // $ nzyme-util api infra taps metrics
                            tap_metrics_app::run(connection, uuid);
                        }
                    },
                },
            }
        },

        None => {
            // Because arg_required_else_help = true, we normally don’t get here
        }
    }

    std::process::exit(EX_OK);
}
