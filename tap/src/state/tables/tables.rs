use std::{sync::{Arc, Mutex}, thread};
use log::error;
use std::time::Duration;
use crate::wireless::bluetooth::tables::bluetooth_table::BluetoothTable;
use crate::configuration::Configuration;
use crate::state::tables::dot11_table::Dot11Table;
use crate::state::tables::dns_table::DnsTable;
use crate::state::tables::socks_table::SocksTable;
use crate::state::tables::ssh_table::SshTable;
use crate::state::tables::tcp_table::TcpTable;
use crate::state::tables::udp_table::UdpTable;
use crate::link::leaderlink::Leaderlink;
use crate::messagebus::bus::Bus;

use crate::metrics::Metrics;
use crate::state::tables::arp_table::ArpTable;
use crate::state::tables::dhcp_table::DhcpTable;
use crate::state::tables::ntp_table::NtpTable;
use crate::state::tables::rtsp_table::RtspTable;
use crate::state::tables::stun_table::StunTable;
use crate::state::tables::uav_table::UavTable;
use crate::wireless::dot11::engagement::engagement_control::EngagementControl;

pub struct Tables {
    pub dot11: Arc<Mutex<Dot11Table>>,
    pub bluetooth: Arc<Mutex<BluetoothTable>>,
    pub arp: Arc<Mutex<ArpTable>>,
    pub dhcp: Arc<Mutex<DhcpTable>>,
    pub tcp: Arc<Mutex<TcpTable>>,
    pub udp: Arc<Mutex<UdpTable>>,
    pub dns: Arc<Mutex<DnsTable>>,
    pub ssh: Arc<Mutex<SshTable>>,
    pub socks: Arc<Mutex<SocksTable>>,
    pub ntp: Arc<Mutex<NtpTable>>,
    pub rtsp: Arc<Mutex<RtspTable>>,
    pub stun: Arc<Mutex<StunTable>>,
    pub uav: Arc<Mutex<UavTable>>,
    has_ethernet: bool,
    has_dot11: bool,
    has_bluetooth: bool
}

impl Tables {

    pub fn new(metrics: Arc<Mutex<Metrics>>,
               leaderlink: Arc<Mutex<Leaderlink>>,
               ethernet_bus: Arc<Bus>,
               engagement_control: Arc<EngagementControl>,
               configuration: &Configuration) -> Self {
        let has_ethernet_default = configuration.ethernet_interfaces.as_ref()
            .is_some_and(|map| map.values().any(|i| i.active));
        let has_ethernet_raw = configuration.rawip_interfaces.as_ref()
            .is_some_and(|map| map.values().any(|i| i.active));
        let has_ethernet = has_ethernet_default || has_ethernet_raw;
        let has_bluetooth = configuration.bluetooth_interfaces.as_ref()
            .is_some_and(|map| map.values().any(|i| i.active));
        let has_dot11 = configuration.wifi_interfaces.as_ref()
            .is_some_and(|map| map.values().any(|i| i.active));

        Tables {
            dot11: Arc::new(Mutex::new(Dot11Table::new(leaderlink.clone()))),
            bluetooth: Arc::new(Mutex::new(BluetoothTable::new(leaderlink.clone(), metrics.clone()))),
            arp: Arc::new(Mutex::new(ArpTable::new(
                leaderlink.clone(),
                metrics.clone(),
                configuration.protocols.arp.poisoning_monitor,
                configuration.protocols.arp.poisoning_window_seconds))
            ),
            dhcp: Arc::new(Mutex::new(DhcpTable::new(leaderlink.clone(), metrics.clone()))),
            dns: Arc::new(Mutex::new(DnsTable::new(leaderlink.clone(), metrics.clone()))),
            tcp: Arc::new(Mutex::new(TcpTable::new(
                leaderlink.clone(),
                ethernet_bus.clone(),
                metrics.clone(),
                configuration.protocols.tcp.reassembly_buffer_size,
                configuration.protocols.tcp.session_timeout_seconds
            ))),
            udp: Arc::new(Mutex::new(UdpTable::new(leaderlink.clone(), ethernet_bus.clone(), metrics.clone()))),
            ssh: Arc::new(Mutex::new(SshTable::new(leaderlink.clone(), metrics.clone()))),
            socks: Arc::new(Mutex::new(SocksTable::new(leaderlink.clone(), metrics.clone()))),
            ntp: Arc::new(Mutex::new(NtpTable::new(leaderlink.clone(), metrics.clone()))),
            rtsp: Arc::new(Mutex::new(RtspTable::new(leaderlink.clone(), metrics.clone()))),
            stun: Arc::new(Mutex::new(StunTable::new(leaderlink.clone(), metrics.clone()))),
            uav: Arc::new(Mutex::new(UavTable::new(leaderlink.clone(), metrics.clone(), engagement_control))),
            has_ethernet,
            has_dot11,
            has_bluetooth
        }
    }

    pub fn run_jobs(&self) {
        loop {
            thread::sleep(Duration::from_secs(10));

            if self.has_dot11 {
                match self.dot11.lock() {
                    Ok(dot11) => dot11.process_report(),
                    Err(e) => error!("Could not acquire 802.11 table lock for report processing: {}", e)
                }
            }

            if self.has_bluetooth {
                match self.bluetooth.lock() {
                    Ok(bluetooth) => {
                        bluetooth.calculate_metrics();
                        bluetooth.process_report();
                    },
                    Err(e) => error!("Could not acquire Bluetooth table lock for report processing: {}", e)
                }
            }

            if self.has_ethernet {
                match self.arp.lock() {
                    Ok(arp) => {
                        arp.calculate_metrics();
                        arp.process_report();
                    },
                    Err(e) => error!("Could not acquire ARP table lock for report processing: {}", e)
                }

                match self.dhcp.lock() {
                    Ok(dhcp) => {
                        dhcp.calculate_metrics();
                        dhcp.process_report();
                    },
                    Err(e) => error!("Could not acquire DHCP table lock for report processing: {}", e)
                }

                match self.tcp.lock() {
                    Ok(tcp) => {
                        tcp.calculate_metrics();
                        tcp.process_report();
                    },
                    Err(e) => error!("Could not acquire TCP table lock for report processing: {}", e)
                }

                match self.udp.lock() {
                    Ok(udp) => {
                        udp.calculate_metrics();
                        udp.process_report();
                    },
                    Err(e) => error!("Could not acquire UDP table lock for report processing: {}", e)
                }

                match self.dns.lock() {
                    Ok(dns) => {
                        dns.calculate_metrics();
                        dns.process_report();
                    },
                    Err(e) => error!("Could not acquire DNS table lock for report processing: {}", e)
                }

                match self.ssh.lock() {
                    Ok(ssh) => {
                        ssh.calculate_metrics();
                        ssh.process_report();
                    },
                    Err(e) => error!("Could not acquire SSH table lock for report processing: {}", e)
                }

                match self.socks.lock() {
                    Ok(socks) => {
                        socks.calculate_metrics();
                        socks.process_report();
                    },
                    Err(e) => error!("Could not acquire SOCKS table lock for report processing: {}", e)
                }

                match self.ntp.lock() {
                    Ok(ntp) => {
                        ntp.calculate_metrics();
                        ntp.process_report();
                    },
                    Err(e) => error!("Could not acquire NTP table lock for report processing: {}", e)
                }

                match self.rtsp.lock() {
                    Ok(rtsp) => {
                        rtsp.calculate_metrics();
                        rtsp.process_report();
                    },
                    Err(e) => error!("Could not acquire RTSP table lock for report processing: {}", e)
                }

                match self.stun.lock() {
                    Ok(stun) => {
                        stun.calculate_metrics();
                        stun.process_report();
                    },
                    Err(e) => error!("Could not acquire STUN table lock for report processing: {}", e)
                }
            }

            match self.uav.lock() {
                Ok(uavs) => {
                    /*
                     * UAVs subsystem doesn't have own captures but uses 802.11. We have to
                     * determine activity in this way instead of checking if any captures exist.
                     */
                    if uavs.is_active() {
                        uavs.calculate_metrics();
                        uavs.process_report();
                    }
                },
                Err(e) => error!("Could not acquire UAV table lock for report processing: {}", e)
            }
        }
    }

}