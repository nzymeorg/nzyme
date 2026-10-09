use std::{collections::HashMap, sync::Arc, thread::{sleep, self}};

use anyhow::{Error, bail};
use crossbeam_channel::Sender;
use log::{error, info, debug, trace};
use systemstat::Duration;
use crate::configuration::ChannelWidthHoppingMode::limited;
use crate::configuration::WifiInterface;
use crate::wireless::dot11::supported_frequency::{SupportedChannelWidth, SupportedFrequency};
use crate::wireless::dot11::supported_frequency::SupportedChannelWidth::Mhz20;
use crate::helpers::network::{dot11_channel_to_frequency, Nl80211Band};
use crate::wireless::dot11::sona::commands::{AddressedSonaCommand, SonaCommand};
use crate::wireless::dot11::sona::sona::SonaVersion;
use crate::wireless::dot11::sona::sona_tools::extract_serial_from_interface_name;
use crate::wireless::dot11::sona::supported_frequencies::getSonaSupportedFrequencies;
use crate::telemetry::{EventLog, TelemetryEvent};
use super::nl::Nl;

const HOP_DWELL_MS: u64 = 1000;

pub struct ChannelHopper {
    pub device_assignments: HashMap<String, Vec<SupportedFrequency>>,
    pub command_sender: Sender<AddressedSonaCommand>,
    events: Arc<EventLog>
}

impl ChannelHopper {

    pub fn new(devices: HashMap<String, WifiInterface>,
               command_sender: Sender<AddressedSonaCommand>,
               events: Arc<EventLog>) -> Result<Self, Error> {
        // Define adapters with their channels
        let mut adapters: HashMap<String, Vec<SupportedFrequency>> = HashMap::new();

        let mut nl = match Nl::new() {
            Ok(nl) => nl,
            Err(e) => {
                bail!("Could not establish Netlink connection: {}", e);
            }
        };

        for (device_name, device_configuration) in &devices {
            if !device_configuration.active {
                info!("Skipping disabled WiFi interface [{}].", device_name);
                continue;
            }

            if let Some(true) = device_configuration.disable_hopper {
                info!("Skipping hopper for WiFi interface [{}].", device_name);
                continue;
            }

            if device_name.starts_with("sona-") {
                adapters.insert(device_name.clone(), getSonaSupportedFrequencies(SonaVersion::One));
            } else {
                match nl.fetch_device_info(device_name) {
                        Ok(device) => { adapters.insert(device_name.clone(), device.supported_frequencies); },
                        Err(e) => { error!("Could not fetch information of device [{}]. Not assigning to channels: {}", device_name, e); }
                    }
                }
        }

        info!("Available adapters and channels: {:?}", adapters);

        // Map to config.
        let mut device_assignments: HashMap<String, Vec<SupportedFrequency>> = HashMap::new();
        for (device_name, device_configuration) in devices.clone() {
            if !device_configuration.active {
                debug!("Skipping disabled WiFi interface [{}].", device_name);
                continue;
            }

            if let Some(true) = device_configuration.disable_hopper {
                debug!("Skipping hopper for WiFi interface [{}].", device_name);
                continue;
            }

            if device_name.starts_with("sona-") {

            } else {
                // Check if selected frequencies are supported and bail if not.
                match adapters.get(&device_name) {
                    None => bail!("WiFi adapter [{}] not found.", device_name),
                    Some(adapter) => {
                        if let Some(channels) = device_configuration.channels_2g.as_ref() {
                            for &channel in channels {
                                let frequency: u16 = match dot11_channel_to_frequency(channel, Nl80211Band::Band2GHz) {
                                    Ok(frequency) => frequency,
                                    Err(e) => bail!("Could not get frequency for 2G channel <{}> of device [{}]: {}",
                                        channel, device_name, e)
                                };

                                if !Self::adapter_supports_frequency(adapter, frequency as u32) {
                                    bail!("WiFi adapter [{}] does not support channel <{} / {} MHz>.",
                                        device_name, channel, frequency);
                                }
                            }
                        }

                        if let Some(channels) = device_configuration.channels_5g.as_ref() {
                            for &channel in channels {
                                let frequency: u16 = match dot11_channel_to_frequency(channel, Nl80211Band::Band5GHz) {
                                    Ok(frequency) => frequency,
                                    Err(e) => bail!("Could not get frequency for 5G channel <{}> of device [{}]: {}",
                                        channel, device_name, e)
                                };

                                if !Self::adapter_supports_frequency(adapter, frequency as u32) {
                                    bail!("WiFi adapter [{}] does not support channel <{} / {} MHz>.",
                                        device_name, channel, frequency);
                                }
                            }
                        }

                        if let Some(channels) = device_configuration.channels_6g.as_ref() {
                            for &channel in channels {
                                let frequency: u16 = match dot11_channel_to_frequency(channel, Nl80211Band::Band6GHz) {
                                    Ok(frequency) => frequency,
                                    Err(e) => bail!("Could not get frequency for 6G channel <{}> of device [{}]: {}",
                                        channel, device_name, e)
                                };

                                if !Self::adapter_supports_frequency(adapter, frequency as u32) {
                                    bail!("WiFi adapter [{}] does not support channel <{} / {} MHz>.",
                                        device_name, channel, frequency);
                                }
                            }
                        }
                    }
                }
            }

            // Build final list of device assigments, including frequencies and channel widths.
            let mut frequencies: Vec<SupportedFrequency> = Vec::new();
            if let Some(channels) = device_configuration.channels_2g.as_ref() {
                for &channel in channels {
                    let (frequency, mut channel_widths) = match dot11_channel_to_frequency(channel, Nl80211Band::Band2GHz) {
                        Ok(frequency) => (frequency as u32, Self::get_all_supported_channel_widths(adapters.get(&device_name).unwrap(), frequency as u32)),
                        Err(e) => bail!("Could not get frequency for 2G channel <{}> of device [{}]: {}",
                                    channel, device_name, e)
                    };

                    // Set channel widths to only 20Mhz for limited channel width hopping mode.
                    if let Some(mode) = &device_configuration.channel_width_hopping_mode {
                        if mode.eq(&limited) {
                            channel_widths.clear();
                            channel_widths.push(Mhz20);
                        }
                    }

                    frequencies.push(SupportedFrequency { frequency, channel_widths })
                }
            }
            if let Some(channels) = device_configuration.channels_5g.as_ref() {
                for &channel in channels {
                    let (frequency, mut channel_widths) = match dot11_channel_to_frequency(channel, Nl80211Band::Band5GHz) {
                        Ok(frequency) => (frequency as u32, Self::get_all_supported_channel_widths(adapters.get(&device_name).unwrap(), frequency as u32)),
                        Err(e) => bail!("Could not get frequency for 5G channel <{}> of device [{}]: {}",
                                    channel, device_name, e)
                    };

                    // Set channel widths to only 20Mhz for limited channel width hopping mode.
                    if let Some(mode) = &device_configuration.channel_width_hopping_mode {
                        if mode.eq(&limited) {
                            channel_widths.clear();
                            channel_widths.push(Mhz20);
                        }
                    }

                    frequencies.push(SupportedFrequency { frequency, channel_widths })
                }
            }
            if let Some(channels) = device_configuration.channels_6g.as_ref() {
                for &channel in channels {
                    let (frequency, mut channel_widths) = match dot11_channel_to_frequency(channel, Nl80211Band::Band6GHz) {
                        Ok(frequency) => (frequency as u32, Self::get_all_supported_channel_widths(adapters.get(&device_name).unwrap(), frequency as u32)),
                        Err(e) => bail!("Could not get frequency for 6G channel <{}> of device [{}]: {}",
                                    channel, device_name, e)
                    };

                    // Set channel widths to only 20Mhz for limited channel width hopping mode.
                    if let Some(mode) = &device_configuration.channel_width_hopping_mode {
                        if mode.eq(&limited) {
                            channel_widths.clear();
                            channel_widths.push(Mhz20);
                        }
                    }

                    frequencies.push(SupportedFrequency { frequency, channel_widths })
                }
            }

            device_assignments.insert(device_name, frequencies);
        }

        Ok(ChannelHopper { device_assignments, command_sender, events })
    }

    pub fn spawn_loop(&self) {
        let device_assigments = self.device_assignments.clone();

        info!("Channel map: {:?}", device_assigments);
        
        // Spawn a hopper thread for each device.
        for (device, channels) in device_assigments {
            if channels.is_empty() {
                continue;
            }

            let sona_command_sender = self.command_sender.clone();
            let events = self.events.clone();

            thread::spawn(move || {
                let mut nl = match Nl::new() {
                    Ok(nl) => nl,
                    Err(e) => {
                        error!("Could not establish Netlink connection: {}", e);
                        return
                    }
                };

                loop {
                    for channel in &channels {
                        let frequency = channel.frequency;

                        for width in &channel.channel_widths {
                            if device.starts_with("sona-") {
                                let sona_device_serial = match extract_serial_from_interface_name("sona", &device) {
                                    Ok(serial) => serial,
                                    Err(e) => {
                                        // Should never happen.
                                        error!("Could not extract device serial from \
                                            interface [{}]: {}", device, e);
                                        continue
                                    }
                                };

                                match sona_command_sender.send(AddressedSonaCommand {
                                    sona_device_serial,
                                    cmd: SonaCommand::SetFrequency(frequency as u16),
                                }) {
                                    Ok(()) => {
                                        trace!("Sent command to set frequency of Sona [{}] \
                                            to <{}>", device, frequency);
                                        events.push(TelemetryEvent::Hop {
                                            device: device.clone(), frequency, width: width_label(width)
                                        });
                                    },
                                    Err(e) => {
                                        error!("Could not send command to set frequency of \
                                            Sona [{}] to <{}>: {}", device, frequency, e);
                                    }
                                }
                            } else {
                                match nl.set_device_frequency(&device, frequency, width) {
                                    Ok(()) => {
                                        debug!("Device [{}] now tuned to frequency [{} Mhz / {:?}].", device, frequency, width);
                                        events.push(TelemetryEvent::Hop {
                                            device: device.clone(), frequency, width: width_label(width)
                                        });
                                    },
                                    Err(e) => error!("Could not tune [{}] to frequency [{} Mhz / {:?}]: {}", device, frequency, width, e)
                                }
                            }

                            sleep(Duration::from_millis(HOP_DWELL_MS));
                        }
                    }
                }
            });
        }
    }

    pub fn get_device_assignments(&self) -> HashMap<String, Vec<SupportedFrequency>> {
        self.device_assignments.clone()
    }

    pub fn get_device_cycle_times(&self) -> HashMap<String, u64> {
        let mut cycle_times = HashMap::new();
        
        for (device, freqs) in &self.device_assignments {
            let mut hop_count: u64 = 0;

            for freq in freqs {
                hop_count += freq.channel_widths.len() as u64;
            }

            cycle_times.insert(device.clone(), hop_count* HOP_DWELL_MS);
        }

        cycle_times
    }

    fn adapter_supports_frequency(frequencies: &Vec<SupportedFrequency>, frequency: u32) -> bool {
        for f in frequencies {
            if f.frequency == frequency {
                return true
            }
        }

        false
    }

    fn get_all_supported_channel_widths(frequencies: &Vec<SupportedFrequency>, frequency: u32)
        -> Vec<SupportedChannelWidth> {
        for f in frequencies {
            if f.frequency == frequency {
                return f.clone().channel_widths
            }
        }

        vec![]
    }

}

fn width_label(width: &SupportedChannelWidth) -> &'static str {
    match width {
        SupportedChannelWidth::Mhz20 => "20",
        SupportedChannelWidth::Mhz40Minus => "40-",
        SupportedChannelWidth::Mhz40Plus => "40+",
        SupportedChannelWidth::Mhz80 => "80",
        SupportedChannelWidth::Mhz160 => "160",
        SupportedChannelWidth::Mhz320 => "320",
    }
}
