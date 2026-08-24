mod helpers;
mod messagebus;
mod link;
mod configuration;
mod exit_code;
mod metrics;
mod system_state;
mod logging;
mod alerting;
mod processor_controller;
mod log_monitor;
mod log_buffer;
mod state;
mod context;
mod protocols;
mod wired;
mod wireless;
mod rpi;
mod peripherals;
mod usb;
mod android;

use std::{process::exit, sync::{Arc, Mutex}, thread::{self, sleep}, time, time::Duration};
use std::collections::HashMap;
use anyhow::Error;
use chrono::Utc;
use clap::Parser;
use crossbeam_channel::{Receiver};
use configuration::Configuration;
use state::tables::tables::Tables;
use link::leaderlink::Leaderlink;
use log::{error, info, warn};
use toml::map::Map;
use messagebus::bus::Bus;
use system_state::SystemState;
use wired::ethernet;
use crate::context::context_engine::ContextEngine;
use wireless::dot11::channel_hopper::ChannelHopper;
use wireless::dot11::dot11_broker::Dot11Broker;
use wired::ethernet::ethernet_broker::EthernetBroker;
use wireless::{bluetooth, dot11};
use crate::android::debug::android_debug::AndroidDebugListener;
use crate::helpers::network::Channel;
use crate::link::payloads::ConfigurationReport;
use crate::log_buffer::LogBuffer;
use crate::log_monitor::LogMonitor;
use crate::peripherals::limina::limina::Limina;
use crate::processor_controller::ProcessorController;
use crate::state::state::State;
use crate::wired::portalintegrity;
use crate::wireless::dot11::engagement::engagement_control::EngagementControl;
use crate::wireless::dot11::sona;
use crate::wireless::dot11::sona::command_router::SonaCommandRouter;
use crate::wireless::dot11::sona::commands::{SonaCommand};
use crate::wireless::dot11::sona::sona_tools::extract_serial_from_interface_name;

#[derive(Parser,Debug)]
struct Arguments {
    #[clap(short, long, required_unless_present("generate_channels"))]
    configuration_file: Option<String>,

    #[clap(short, long)]
    log_level: Option<String>,

    #[clap(short, long, required_unless_present("configuration_file"))]
    generate_channels: bool,

    #[clap(short, long)]
    print_configuration: bool,
}

fn main() {
    let process_started_at = Utc::now();

    let args = Arguments::parse();
    let log_level = args.log_level.unwrap_or_else(|| "info".to_string());

    if args.generate_channels {
        /*
         * To avoid log output in the generated TOML, we only initialize logging
         * if it is not set to info
         */
        if log_level != "info" {
            logging::initialize(&log_level, &Arc::new(LogMonitor::default()), &Arc::new(LogBuffer::new()));
        }

        let nl = dot11::nl::Nl::new();
        match nl {
            Ok(mut nl) => {
                let interfaces = nl.fetch_devices();
                match interfaces {
                    Ok(interfaces) => {
                        println!();
                        println!("# IMPORTANT: Remove duplicate channels. Each channel can only be monitored by one adapter.");
                        println!();
                        for (key, _) in &interfaces {
                            let interface_info = nl.fetch_device_info(key);
                            match interface_info {
                                Ok(interface_info) => {
                                    let channels: Vec<Result<Channel, Error>> = interface_info.supported_frequencies
                                        .iter().map(|f| Channel::from_frequency(f.frequency)).collect();

                                    let mut iface = Map::new();
                                    iface.insert("active".to_string(), toml::Value::Boolean(true));
                                    iface.insert("channels_2g".to_string(), toml::Value::Array(channels.iter()
                                        .filter( | f | (**f).is_ok() && (**f).as_ref().unwrap().is_2g())
                                        .map(|f| toml::Value::Integer((*f).as_ref().unwrap().channel as i64)).collect()));
                                    iface.insert("channels_5g".to_string(), toml::Value::Array(channels.iter()
                                        .filter( | f | (**f).is_ok() && (**f).as_ref().unwrap().is_5g())
                                        .map(|f| toml::Value::Integer((*f).as_ref().unwrap().channel as i64)).collect()));
                                    iface.insert("channels_6g".to_string(), toml::Value::Array(channels.iter()
                                        .filter( | f | (**f).is_ok() && (**f).as_ref().unwrap().is_6g())
                                        .map(|f| toml::Value::Integer((*f).as_ref().unwrap().channel as i64)).collect()));
                                    let mut iface_table = Map::new();
                                    iface_table.insert(key.clone(), toml::Value::Table(iface));
                                    let mut wifi_ifaces = Map::new();
                                    wifi_ifaces.insert("wifi_interfaces".to_string(), toml::Value::Table(iface_table));
                                    let toml_string = toml::to_string(&toml::Value::Table(wifi_ifaces)).unwrap();
                                    println!("{}", toml_string);
                                },
                                Err(e) => {
                                    error!("Could not fetch information for device [{}]: {}", key, e);
                                }
                            }
                        }

                    },
                    Err(e) => {
                        error!("Could not fetch interfaces: {}", e);
                        exit(exit_code::EX_OSERR);
                    }
                }
            },
            Err(e) => {
                error!("Could not establish Netlink connection: {}", e);
                exit(exit_code::EX_OSERR);
            }
        }
        exit(exit_code::EX_OK);
    }

    let log_monitor = Arc::new(LogMonitor::default());
    let log_buffer = Arc::new(LogBuffer::new());
    logging::initialize(&log_level, &log_monitor, &log_buffer);

    info!("Starting nzyme tap version [{}].", env!("CARGO_PKG_VERSION"));

    // Load configuration.
    let configuration: Configuration = match configuration::load(args.configuration_file.unwrap()) { // can unwrap here due to clap requirement
        Ok(configuration) => {
            info!("Parsed and loaded configuration.");
            configuration
        },
        Err(e) => {
            error!("Fatal error: Could not load configuration. {}", e);
            exit(exit_code::EX_CONFIG);
        }
    };

    if args.print_configuration {
        info!("Printing configuration.");

        let configuration_report = ConfigurationReport::try_from(configuration).unwrap();
        serde_json::to_writer_pretty(std::io::stdout(), &configuration_report).unwrap();

        exit(exit_code::EX_OK);
    }

    let system_state = Arc::new(
        SystemState::new(configuration.misc.training_period_minutes as usize).initialize()
    );

    wired::interfaces::print_devices();

    // Install panic logger.
    logging::install_panic_logger(configuration.misc.panic_log_file_path.clone()
        .unwrap_or("/var/log/nzyme-tap-panic.log".to_string()));

    let metrics = Arc::new(Mutex::new(metrics::Metrics::new(log_monitor)));

    // TODO: Unify into single Bus struct? We may be over-allocating channels here.
    let ethernet_bus = Arc::new(Bus::new(metrics.clone(), "ethernet_data".to_string(), configuration.clone()));
    let dot11_bus = Arc::new(Bus::new(metrics.clone(), "dot11_frames".to_string(), configuration.clone()));
    let bluetooth_bus = Arc::new(Bus::new(metrics.clone(), "bluetooth_data".to_string(), configuration.clone()));
    let generic_bus = Arc::new(Bus::new(metrics.clone(), "generic_data".to_string(), configuration.clone()));

    let leaderlink = match Leaderlink::new(
        configuration.clone(),
        metrics.clone(),
        ethernet_bus.clone(),
        dot11_bus.clone(),
        bluetooth_bus.clone(),
        generic_bus.clone()
    ) {
        Ok(leaderlink) => Arc::new(Mutex::new(leaderlink)),
        Err(e) => {
            error!("Fatal error: Could not set up conection to nzyme leader. {}", e);
            exit(exit_code::EX_CONFIG);
        }
    };

    // Launch limina if enabled.
    if configuration.general.limina == Some(true) {
        let limina = Limina::new(metrics.clone());
        limina.launch_monitor();
    }

    // Engagement control.
    let engagement_control = Arc::new(EngagementControl::new(
        configuration.wifi_engagement_interfaces.clone().unwrap_or_default(),
        metrics.clone(),
        dot11_bus.clone()
    ));
    engagement_control.initialize();

    let tables = Arc::new(Tables::new(
        metrics.clone(),
        leaderlink.clone(),
        ethernet_bus.clone(),
        engagement_control,
        &configuration
    ));
    let state = Arc::new(State::new(metrics.clone()));
    state.initialize();

    let tables_bg = tables.clone();
    thread::spawn(move || {
        tables_bg.run_jobs();
    });

    // Start context engine.
    let context_engine = Arc::new(ContextEngine::new(
        state.clone(),
        leaderlink.clone(),
        metrics.clone(), 
        configuration.clone())
    );
    context_engine.initialize();

    // Android debug listener.
    let android_metrics = metrics.clone();
    let android_configuration = configuration.clone();
    let android_log_buffer = log_buffer.clone();
    thread::spawn(move || {
        info!("Initializing Android debug subsystem.");
        let listener = AndroidDebugListener::new(
            process_started_at, android_configuration, android_metrics, android_log_buffer
        );

        thread::spawn(move || {
            listener.listen();
        });
    });

    // Ethernet handler.
    let ethernet_handlerbus = ethernet_bus.clone();
    thread::spawn(move || {
        EthernetBroker::new(ethernet_handlerbus, configuration.performance.ethernet_brokers as usize).run();
    });
    
    // WiFi handler.
    let wifi_handlerbus = dot11_bus.clone();
    thread::spawn(move || {
        Dot11Broker::new(wifi_handlerbus, configuration.performance.wifi_brokers as usize).run();
    });

    // Ethernet Capture.
    if let Some(ethernet_interfaces) = &configuration.ethernet_interfaces {
        for (interface_name, interface_config) in ethernet_interfaces {
            if !interface_config.active {
                info!("Skipping disabled Ethernet interface [{}].", interface_name);
                continue;
            }
            let capture_metrics = metrics.clone();
            let capture_bus = ethernet_bus.clone();
            let interface_name = interface_name.clone();
            thread::spawn(move || {
                let mut ethernet_capture = ethernet::capture::Capture {
                    metrics: capture_metrics.clone(),
                    bus: capture_bus
                };

                match capture_metrics.lock() {
                    Ok(mut metrics) => metrics.register_new_capture(&interface_name, metrics::CaptureType::Ethernet),
                    Err(e) => error!("Could not acquire mutex of metrics: {}", e)
                }

                loop {
                    ethernet_capture.run(&interface_name);

                    error!("Ethernet capture [{}] disconnected. Retrying in 5 seconds.", interface_name);
                    match capture_metrics.lock() {
                        Ok(mut metrics) => metrics.mark_capture_as_failed(&interface_name),
                        Err(e) => error!("Could not acquire mutex of metrics: {}", e)
                    }
                    sleep(Duration::from_secs(5));
                }
            });
        }
    }
    
    // Raw IP capture.
    if let Some(rawip_interfaces) = &configuration.rawip_interfaces {
        for (interface_name, interface_config) in rawip_interfaces {
            if !interface_config.active {
                info!("Skipping disabled Raw IP interface [{}].", interface_name);
                continue;
            }
            let capture_metrics = metrics.clone();
            let capture_bus = ethernet_bus.clone();
            let interface_name = interface_name.clone();
            
            thread::spawn(move || {
                let mut rawip_capture = wired::rawip::capture::Capture {
                    metrics: capture_metrics.clone(),
                    bus: capture_bus
                };

                match capture_metrics.lock() {
                    Ok(mut metrics) => metrics.register_new_capture(&interface_name, metrics::CaptureType::RawIp),
                    Err(e) => error!("Could not acquire mutex of metrics: {}", e)
                }

                loop {
                    rawip_capture.run(&interface_name);

                    error!("Raw IP capture [{}] disconnected. Retrying in 5 seconds.", &interface_name);
                    match capture_metrics.lock() {
                        Ok(mut metrics) => metrics.mark_capture_as_failed(&interface_name),
                        Err(e) => error!("Could not acquire mutex of metrics: {}", e)
                    }
                    sleep(Duration::from_secs(5));
                }
            });
        }
    }

    // Set up Sona command router infrastructure.
    let mut sona_command_router = SonaCommandRouter::new();
    let sona_command_router_sender = sona_command_router.sender.clone();
    let mut cmd_receivers: HashMap<String, Receiver<SonaCommand>> = HashMap::new();
    if let Some(wifi_interfaces) = &configuration.wifi_interfaces {
        for (interface_name, interface_config) in wifi_interfaces {
            if !interface_config.active {
                continue;
            }

            if interface_name.starts_with("sona-") {
                if let Ok(serial) = extract_serial_from_interface_name("sona", interface_name) {
                    let rx = sona_command_router.register_capture(&serial);
                    cmd_receivers.insert(serial, rx);
                }
            }
        }
    }

    // Run Sona command router.
    std::thread::spawn(move || {
        sona_command_router.run();
    });

    // WiFi capture.
    if let Some(wifi_interfaces) = &configuration.wifi_interfaces {
        for (interface_name, interface_config) in wifi_interfaces {
            if !interface_config.active {
                info!("Skipping disabled WiFi interface [{}].", interface_name);
                continue;
            }

            let capture_metrics = metrics.clone();
            let capture_bus = dot11_bus.clone();
            let interface_name = interface_name.clone();

            if interface_name.starts_with("sona-") {
                // Sona capture.
                let serial = match extract_serial_from_interface_name("sona", &interface_name) {
                    Ok(serial) => serial,
                    Err(e) => {
                        error!("Could not extract Sona serial from interface name [{}].", interface_name);
                        continue;
                    }
                };

                // Remove so the receiver is moved exactly once into exactly one thread
                let Some(cmd_rx) = cmd_receivers.remove(&serial) else {
                    error!("No command receiver registered for Sona [{}].", serial);
                    continue;
                };

                thread::spawn(move || {
                    if let Ok(sona_serial) = extract_serial_from_interface_name("sona",
                                                                                &interface_name) {
                        let mut sona_capture = sona::capture::Capture {
                            metrics: capture_metrics.clone(),
                            bus: capture_bus.clone(),
                            command_receiver: cmd_rx
                        };

                        match capture_metrics.lock() {
                            Ok(mut metrics) => metrics.register_new_capture(&interface_name,
                                                                            metrics::CaptureType::WiFi),
                            Err(e) => error!("Could not acquire mutex of metrics: {}", e)
                        }

                        loop {
                            sona_capture.run(&interface_name, &sona_serial);

                            error!("Sona capture [{}] disconnected. Retrying in 5 seconds.",
                                &sona_serial);
                            match capture_metrics.lock() {
                                Ok(mut metrics) => metrics.mark_capture_as_failed(&interface_name),
                                Err(e) => error!("Could not acquire mutex of metrics: {}", e)
                            }
                            thread::sleep(time::Duration::from_secs(5));
                        }
                    } else {
                        warn!("Sona with invalid interface name: [{}].", &interface_name);
                    }
                });
            } else {
                // Classic netlink/libpcap capture.
                thread::spawn(move || {
                    let mut dot11_capture = dot11::capture::Capture {
                        metrics: capture_metrics.clone(),
                        bus: capture_bus.clone()
                    };

                    match capture_metrics.lock() {
                        Ok(mut metrics) => metrics.register_new_capture(&interface_name, metrics::CaptureType::WiFi),
                        Err(e) => error!("Could not acquire mutex of metrics: {}", e)
                    }

                    loop {
                        dot11_capture.run(&interface_name);

                        error!("WiFi capture [{}] disconnected. Retrying in 5 seconds.", &interface_name);
                        match capture_metrics.lock() {
                            Ok(mut metrics) => metrics.mark_capture_as_failed(&interface_name),
                            Err(e) => error!("Could not acquire mutex of metrics: {}", e)
                        }
                        thread::sleep(time::Duration::from_secs(5));
                    }
                });
            }
        }
    }

    let covered_wifi_spectrum;
    let wifi_device_cycle_times;
    if let Some(wifi_interfaces) = configuration.clone().wifi_interfaces {
        let hopper = match ChannelHopper::new(wifi_interfaces, sona_command_router_sender) {
            Ok(ch) => ch,
            Err(e) => {
                error!("Could not initialize ChannelHopper: {}", e);
                exit(exit_code::EX_OSERR);
            }
        };
        covered_wifi_spectrum = Some(hopper.get_device_assignments());
        wifi_device_cycle_times = Some(hopper.get_device_cycle_times());
        hopper.spawn_loop();
    } else {
        covered_wifi_spectrum = None;
        wifi_device_cycle_times = None;
    }

    // Bluetooth capture.
    if let Some(bluetooth_interfaces) = &configuration.bluetooth_interfaces {
        for (interface_name, interface_config) in bluetooth_interfaces {
            if !interface_config.active {
                info!("Skipping disabled Bluetooth interface [{}].", interface_name);
                continue;
            }

            let capture_metrics = metrics.clone();
            let capture_bus = bluetooth_bus.clone();
            let interface_config = interface_config.clone();
            let interface_name = interface_name.clone();
            thread::spawn(move || {
                let mut bt_capture = bluetooth::capture::Capture {
                    configuration: interface_config,
                    metrics: capture_metrics.clone(),
                    bus: capture_bus
                };

                match capture_metrics.lock() {
                    Ok(mut metrics) => metrics.register_new_capture(&interface_name, metrics::CaptureType::Bluetooth),
                    Err(e) => error!("Could not acquire mutex of metrics: {}", e)
                }

                loop {
                    bt_capture.run(&interface_name);

                    error!("Bluetooth capture [{}] disconnected. Retrying in 5 seconds.", &interface_name);
                    match capture_metrics.lock() {
                        Ok(mut metrics) => metrics.mark_capture_as_failed(&interface_name),
                        Err(e) => error!("Could not acquire mutex of metrics: {}", e)
                    }
                    sleep(Duration::from_secs(5));
                }
            });
        }
    }

    // Portal integrity checks.
    if let Some(checks) = &configuration.portal_integrity_checks {
        for (name, config) in checks {
            if !config.active {
                continue;
            }

            let name = name.clone();
            let portalmetrics = metrics.clone();
            let portallink = leaderlink.clone();
            let portalconfig = config.clone();
            let portalname = name.clone();
            thread::spawn(move || {
                loop {
                    info!("Initializing portal integrity check [{}]: {:?}", portalname, portalconfig);
                    if let Err(e) = portalintegrity::run(
                        portalconfig.clone(),
                        portalname.clone(),
                        portalmetrics.clone(),
                        portallink.clone()) {
                        error!("Portal integrity check [{}] disconnected. Retrying in 5 seconds. {}",
                                name, e);
                    }

                    sleep(Duration::from_secs(5));
                }
            });
        }
    }

    // Processors.
    let processor_controller = ProcessorController::new(
        ethernet_bus.clone(),
        dot11_bus.clone(),
        bluetooth_bus.clone(),
        generic_bus.clone(),
        tables.clone(),
        state.clone(),
        context_engine,
        system_state,
        metrics.clone(),
        configuration
    );
    processor_controller.initialize();

    // Metrics aggregator.
    let aggregatormetrics = metrics.clone();
    thread::spawn(move || {
        metrics::MetricsAggregator::new(aggregatormetrics).run();
    });
    
    // Metrics monitor.
    let monitormetrics = metrics.clone();
    thread::spawn(move || {
        metrics::MetricsMonitor::new(monitormetrics).run();
    });

    let leaderlink_runner = leaderlink.clone();
    thread::spawn(move || { // TODO capsule into struct
        loop {
            match leaderlink_runner.lock() {
                Ok(mut link) => link.run(),
                Err(e) => error!("Could not acquire Leaderlink mutex to run background jobs: {}", e)
            }

            sleep(Duration::from_secs(10));
        }
    });

    info!("Bootstrap complete.");

    // Send initial status with tap configuration to node. Retry until successful.
    loop {
        sleep(Duration::from_secs(5));
        
        let success = match leaderlink.lock() {
            Ok(ll) => {
                // Send hello.
                match ll.send_node_hello(&covered_wifi_spectrum, &wifi_device_cycle_times) {
                    Ok(_) => {
                        info!("Node hello submitted.");
                        true
                    },
                    Err(e) => {
                        error!("Node hello failed. Retrying. Error was: {}", e);
                        false
                    }
                }
            },
            Err(e) => {
                error!("Could not acquire leaderlink lock to submit node hello: {}", e);
                false
            }
        };

        if success {
            break
        }
    }

    loop {
        sleep(Duration::from_secs(1));
    }

}
