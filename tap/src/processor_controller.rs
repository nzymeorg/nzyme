use std::sync::{Arc, Mutex};
use std::thread;
use crossbeam_channel::select;
use log::{error, info};
use crate::configuration::Configuration;
use crate::context::context_engine::ContextEngine;
use crate::messagebus::bus::Bus;
use crate::metrics::Metrics;
use crate::protocols::processors::arp_processor::ARPProcessor;
use crate::protocols::processors::dhcpv4_processor::Dhcpv4Processor;
use crate::protocols::processors::dns_processor::DnsProcessor;
use crate::protocols::processors::dot11::dot11_frame_processor::Dot11FrameProcessor;
use crate::protocols::processors::socks_processor::SocksProcessor;
use crate::protocols::processors::ssh_processor::SshProcessor;
use crate::protocols::processors::tcp_processor::TcpProcessor;
use crate::protocols::processors::uav_remote_id_processor::UavRemoteIdProcessor;
use crate::protocols::processors::udp_processor::UDPProcessor;
use crate::state::state::State;
use crate::state::tables::tables::Tables;
use crate::system_state::SystemState;
use crate::protocols::processors::bluetooth_device_processor::BluetoothDeviceProcessor;
use crate::protocols::processors::ntp_processor::NtpProcessor;
use crate::protocols::processors::rtsp_processor::RtspProcessor;
use crate::protocols::processors::stun_processor::StunProcessor;

const DEFAULT_WIFI_PROCESSORS: i32 = 1;
const DEFAULT_TCP_PROCESSORS: i32 = 2;
const DEFAULT_UDP_PROCESSORS: i32 = 2;
const DEFAULT_SHARED_PROCESSORS: i32 = 1;

pub struct ProcessorController {
    ethernet_bus: Arc<Bus>,
    dot11_bus: Arc<Bus>,
    bluetooth_bus: Arc<Bus>,
    generic_bus: Arc<Bus>,
    tables: Arc<Tables>,
    state: Arc<State>,
    context: Arc<ContextEngine>,
    system_state: Arc<SystemState>,
    metrics: Arc<Mutex<Metrics>>,
    configuration: Configuration
}

impl ProcessorController {

    pub fn new(ethernet_bus: Arc<Bus>,
               dot11_bus: Arc<Bus>,
               bluetooth_bus: Arc<Bus>,
               generic_bus: Arc<Bus>,
               tables: Arc<Tables>,
               state: Arc<State>,
               context: Arc<ContextEngine>,
               system_state: Arc<SystemState>,
               metrics: Arc<Mutex<Metrics>>,
               configuration: Configuration) -> ProcessorController {
        ProcessorController {
            ethernet_bus,
            dot11_bus,
            bluetooth_bus,
            generic_bus,
            tables,
            state,
            context,
            system_state,
            metrics,
            configuration
        }
    }

    pub fn initialize(&self) {
        self.initialize_dot11();
        self.initialize_tcp();
        self.initialize_udp();

        self.initialize_shared_pool_processors();
    }

    pub fn initialize_dot11(&self) {
        let num_threads = self.configuration.protocols.wifi.processors
            .unwrap_or(DEFAULT_WIFI_PROCESSORS);

        info!("Initializing WiFi/802.11 processor with <{}> threads.", num_threads);

        for _ in 0..num_threads {
            let dot11_bus = self.dot11_bus.clone();

            let processor = Dot11FrameProcessor::new(
                self.tables.dot11.clone(), self.metrics.clone(), self.generic_bus.clone()
            );

            thread::spawn(move || {
                for frame in dot11_bus.dot11_frames_pipeline.receiver.iter() {
                    processor.process(frame);
                }

                error!("WiFi/802.11 receiver disconnected.");
            });
        }
    }

    pub fn initialize_tcp(&self) {
        let num_threads = self.configuration.protocols.tcp.processors
            .unwrap_or(DEFAULT_TCP_PROCESSORS);

        info!("Initializing TCP processor with <{}> threads.", num_threads);

        for _ in 0..num_threads {
            let ethernet_bus = self.ethernet_bus.clone();

            let mut processor = TcpProcessor::new(
                self.tables.tcp.clone(), self.context.clone()
            );

            thread::spawn(move || {
                for segment in ethernet_bus.tcp_pipeline.receiver.iter() {
                    processor.process(segment);
                }

                error!("TCP receiver disconnected.");
            });
        }
    }

    pub fn initialize_udp(&self) {
        let num_threads = self.configuration.protocols.tcp.processors
            .unwrap_or(DEFAULT_UDP_PROCESSORS);

        info!("Initializing UDP processor with <{}> threads.", num_threads);

        for _ in 0..num_threads {
            let ethernet_bus = self.ethernet_bus.clone();

            let mut processor = UDPProcessor::new(
                self.ethernet_bus.clone(),
                self.metrics.clone(),
                self.tables.udp.clone(),
                self.context.clone()
            );

            thread::spawn(move || {
                for datagram in ethernet_bus.udp_pipeline.receiver.iter() {
                    processor.process(datagram);
                }

                error!("UDP receiver disconnected.");
            });
        }
    }

    pub fn initialize_shared_pool_processors(&self) {
        let num_threads = self.configuration.performance.shared_protocol_processors
            .unwrap_or(DEFAULT_SHARED_PROCESSORS);

        info!("Initializing shared processor pool with <{}> threads.", num_threads);

        for _ in 0..num_threads {
            let ethernet_bus = self.ethernet_bus.clone();
            let bluetooth_bus = self.bluetooth_bus.clone();
            let generic_bus = self.generic_bus.clone();

            let bluetooth_processor = BluetoothDeviceProcessor::new(self.tables.bluetooth.clone());

            let mut uav_processor = UavRemoteIdProcessor::new(self.tables.uav.clone());

            let mut arp_processor = ARPProcessor::new(self.tables.clone(), self.state.clone(), self.context.clone());
            let mut dhcp4_processor = Dhcpv4Processor::new(self.state.clone(), self.tables.dhcp.clone(), self.context.clone());
            let mut dns_processor = DnsProcessor::new(
                self.system_state.clone(),
                self.state.clone(), 
                self.context.clone(), 
                self.tables.dns.clone(), 
                self.metrics.clone(),
                &self.configuration
            );
            let mut ssh_processor = SshProcessor::new(self.tables.ssh.clone());
            let mut socks_processor = SocksProcessor::new(self.tables.socks.clone());
            let mut ntp_processor = NtpProcessor::new(self.tables.ntp.clone());
            let mut rtsp_processor = RtspProcessor::new(self.tables.rtsp.clone());
            let mut stun_processor = StunProcessor::new(self.tables.stun.clone());

            thread::spawn(move || {
                loop {
                    select! {
                        recv(bluetooth_bus.bluetooth_device_pipeline.receiver) -> msg => {
                            match msg {
                                Ok(segment) => bluetooth_processor.process(segment),
                                Err(e) => {
                                    error!("Bluetooth devices receiver disconnected: {}", e);
                                    break;
                                }
                            }
                        }

                        recv(generic_bus.uav_remote_id_pipeline.receiver) -> msg => {
                            match msg {
                                Ok(segment) => uav_processor.process(segment),
                                Err(e) => {
                                    error!("UAV remote ID receiver disconnected: {}", e);
                                    break;
                                }
                            }
                        }

                        recv(ethernet_bus.arp_pipeline.receiver) -> msg => {
                            match msg {
                                Ok(packet) => arp_processor.process(packet),
                                Err(e) => {
                                    error!("ARP receiver disconnected: {}", e);
                                    break;
                                }
                            }
                        }
                        
                        recv(ethernet_bus.dhcpv4_pipeline.receiver) -> msg => {
                            match msg {
                                Ok(packet) => dhcp4_processor.process(packet),
                                Err(e) => {
                                    error!("DHCP receiver disconnected: {}", e);
                                    break;
                                }
                            }
                        }
                        
                        recv(ethernet_bus.dns_pipeline.receiver) -> msg => {
                            match msg {
                                Ok(packet) => dns_processor.process(packet),
                                Err(e) => {
                                    error!("DNS receiver disconnected: {}", e);
                                    break;
                                }
                            }
                        }
                        
                        recv(ethernet_bus.ssh_pipeline.receiver) -> msg => {
                            match msg {
                                Ok(session) => ssh_processor.process(session),
                                Err(e) => {
                                    error!("SSH receiver disconnected: {}", e);
                                    break;
                                }
                            }
                        }
                        
                        recv(ethernet_bus.socks_pipeline.receiver) -> msg => {
                            match msg {
                                Ok(session) => socks_processor.process(session),
                                Err(e) => {
                                    error!("SOCKS receiver disconnected: {}", e);
                                    break;
                                }
                            }
                        }

                        recv(ethernet_bus.ntp_pipeline.receiver) -> msg => {
                            match msg {
                                Ok(packet) => ntp_processor.process(packet),
                                Err(e) => {
                                    error!("NTP receiver disconnected: {}", e);
                                    break;
                                }
                            }
                        }

                        recv(ethernet_bus.rtsp_pipeline.receiver) -> msg => {
                            match msg {
                                Ok(session) => rtsp_processor.process(session),
                                Err(e) => {
                                    error!("RTSP receiver disconnected: {}", e);
                                    break;
                                }
                            }
                        }

                        recv(ethernet_bus.stun_pipeline.receiver) -> msg => {
                            match msg {
                                Ok(flow) => stun_processor.process(flow),
                                Err(e) => {
                                    error!("STUN receiver disconnected: {}", e);
                                    break;
                                }
                            }
                        }
                    }
                }
            });
        }
    }

}