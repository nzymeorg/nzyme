use std::io::{self, Read, Write};
use std::time::Duration as StdDuration;

use anyhow::{anyhow, bail, Context, Result};
use log::{debug, warn};

use pcap::{Active, Capture};

use smoltcp::iface::{Config, Interface, SocketHandle, SocketSet};
use smoltcp::phy::{self, Device, DeviceCapabilities, Medium};
use smoltcp::phy::ChecksumCapabilities;
use smoltcp::socket::{dhcpv4, dns, raw, tcp};
use smoltcp::time::Instant as SmolInstant;
use smoltcp::wire::{
    DnsQueryType, EthernetAddress, HardwareAddress, Icmpv6Packet, Icmpv6Repr, IpAddress, IpCidr, IpProtocol,
    IpVersion, Ipv4Address, Ipv4Cidr, Ipv6Address, Ipv6Cidr, Ipv6Packet, Ipv6Repr, NdiscRepr,
};

use crate::wired::portalintegrity::slaac;

const TCP_RX_BUF: usize = 32 * 1024;
const TCP_TX_BUF: usize = 8 * 1024;
const POLL_SLEEP: StdDuration = StdDuration::from_millis(1);
const PCAP_MTU: usize = 1514;

const ROUTER_SOLICITATIONS: u32 = 3;
const ROUTER_SOLICITATION_INTERVAL: StdDuration = StdDuration::from_secs(4);

const ICMPV6_ROUTER_ADVERTISEMENT: u8 = 134;
const IPV6_HEADER_LENGTH: usize = 40;

pub struct LeaseInfo {
    pub address: Ipv4Cidr,
    pub router: Option<Ipv4Address>,
    pub dhcp_server: Option<Ipv4Address>,
    pub dns_servers: Vec<Ipv4Address>,
}

pub struct Ipv6Configuration {
    pub address: Ipv6Cidr,
    pub router: Ipv6Address,
    pub dns_servers: Vec<Ipv6Address>,
}

struct PcapDevice {
    cap: Capture<Active>,
    /*
     * IPv6 frames only reach the stack once it has an IPv6 address. Without one, smoltcp panics when it looks
     * for a source address to answer a packet to all nodes with, like an echo request to ff02::1.
     */
    ipv6: bool,
}

fn is_ipv6_frame(frame: &[u8]) -> bool {
    frame.len() >= 14 && frame[12] == 0x86 && frame[13] == 0xdd
}

impl PcapDevice {
    fn new(interface: &str) -> Result<Self> {
        let cap = Capture::from_device(interface)
            .with_context(|| format!("pcap: selecting device '{}'", interface))?
            .promisc(true)
            .immediate_mode(true)
            .snaplen(65_535)
            .timeout(1)
            .open()
            .with_context(|| format!("pcap: opening '{}'", interface))?
            .setnonblock()
            .context("pcap: setting non-blocking mode")?;
        Ok(Self { cap, ipv6: false })
    }
}

impl Device for PcapDevice {
    type RxToken<'a> = PcapRxToken;
    type TxToken<'a> = PcapTxToken<'a>;

    fn receive(&mut self, _t: SmolInstant) -> Option<(Self::RxToken<'_>, Self::TxToken<'_>)> {
        let buf = loop {
            match self.cap.next_packet() {
                Ok(packet) if !self.ipv6 && is_ipv6_frame(packet.data) => continue,
                Ok(packet) => break packet.data.to_vec(),
                Err(_) => return None,
            }
        };
        Some((PcapRxToken(buf), PcapTxToken(&mut self.cap)))
    }

    fn transmit(&mut self, _t: SmolInstant) -> Option<Self::TxToken<'_>> {
        Some(PcapTxToken(&mut self.cap))
    }

    fn capabilities(&self) -> DeviceCapabilities {
        let mut caps = DeviceCapabilities::default();
        caps.medium = Medium::Ethernet;
        caps.max_transmission_unit = PCAP_MTU;
        caps
    }
}

struct PcapRxToken(Vec<u8>);

impl phy::RxToken for PcapRxToken {
    fn consume<R, F>(mut self, f: F) -> R
    where
        F: FnOnce(&mut [u8]) -> R,
    {
        f(&mut self.0)
    }
}

struct PcapTxToken<'a>(&'a mut Capture<Active>);

impl<'a> phy::TxToken for PcapTxToken<'a> {
    fn consume<R, F>(self, len: usize, f: F) -> R
    where
        F: FnOnce(&mut [u8]) -> R,
    {
        let mut buf = vec![0u8; len];
        let result = f(&mut buf);
        if let Err(e) = self.0.sendpacket(&buf[..]) {
            warn!("pcap sendpacket failed ({} bytes): {}", len, e);
        }
        result
    }
}

pub struct Stack<'a> {
    device: PcapDevice,
    iface: Interface,
    sockets: SocketSet<'a>,
    dns_handle: Option<SocketHandle>,
    mac: EthernetAddress,
    ipv6: bool,
}

impl<'a> Stack<'a> {
    pub fn new(interface: &str, mac: EthernetAddress) -> Result<Self> {
        let mut device = PcapDevice::new(interface)
            .with_context(|| format!("opening pcap device on '{}'", interface))?;

        let mut config = Config::new(HardwareAddress::Ethernet(mac));
        config.random_seed = rand::random();

        let iface = Interface::new(config, &mut device, SmolInstant::now());

        Ok(Self {
            device,
            iface,
            sockets: SocketSet::new(vec![]),
            dns_handle: None,
            mac,
            ipv6: false,
        })
    }

    fn poll(&mut self) {
        let now = SmolInstant::now();
        let _ = self.iface.poll(now, &mut self.device, &mut self.sockets);
    }

    pub fn run_dhcp(&mut self, timeout: StdDuration) -> Result<LeaseInfo> {
        let dhcp_handle = self.sockets.add(dhcpv4::Socket::new());

        let deadline = std::time::Instant::now() + timeout;
        let lease = loop {
            self.poll();

            let event = self
                .sockets
                .get_mut::<dhcpv4::Socket>(dhcp_handle)
                .poll();

            match event {
                Some(dhcpv4::Event::Configured(cfg)) => {
                    let dns_servers: Vec<Ipv4Address> = cfg.dns_servers.iter().copied().collect();
                    break LeaseInfo {
                        address: cfg.address,
                        router: cfg.router,
                        dhcp_server: Some(cfg.server.address),
                        dns_servers,
                    };
                }
                Some(dhcpv4::Event::Deconfigured) => {
                    debug!("DHCP deconfigured event");
                }
                None => {}
            }

            if std::time::Instant::now() >= deadline {
                bail!("DHCP timed out after {:?}", timeout);
            }
            std::thread::sleep(POLL_SLEEP);
        };

        debug!("DHCP lease: addr={} router={:?} dns={:?}",
            lease.address, lease.router, lease.dns_servers);

        // Apply the lease to the interface.
        self.iface.update_ip_addrs(|addrs| {
            addrs
                .push(IpCidr::Ipv4(lease.address))
                .expect("ip addr storage full");
        });
        if let Some(router) = lease.router {
            self.iface
                .routes_mut()
                .add_default_ipv4_route(router)
                .map_err(|_| anyhow!("failed to add default route"))?;
        }

        // Stand up the DNS socket using the leased resolvers.
        if lease.dns_servers.is_empty() {
            warn!("DHCP provided no DNS servers. Hostname control URLs will fail to resolve.");
        } else {
            const DNS_SERVER_CAP: usize = 1;
            let servers: Vec<IpAddress> = lease
                .dns_servers
                .iter()
                .take(DNS_SERVER_CAP)
                .map(|s| IpAddress::Ipv4(*s))
                .collect();
            if lease.dns_servers.len() > servers.len() {
                debug!("DHCP offered {} DNS servers; using first {}.",
                    lease.dns_servers.len(),servers.len());
            }
            let dns_sock = dns::Socket::new(&servers, vec![]);
            self.dns_handle = Some(self.sockets.add(dns_sock));
        }

        Ok(lease)
    }

    pub fn run_slaac(&mut self, timeout: StdDuration) -> Result<Ipv6Configuration> {
        let mac = self.mac.0;
        let link_local = Ipv6Address::from(slaac::link_local_address(mac));

        self.iface.update_ip_addrs(|addrs| {
            addrs
                .push(IpCidr::Ipv6(Ipv6Cidr::new(link_local, 64)))
                .expect("ip addr storage full");
        });
        self.device.ipv6 = true;

        let raw_handle = self.sockets.add(raw::Socket::new(
            IpVersion::Ipv6,
            IpProtocol::Icmpv6,
            raw::PacketBuffer::new(vec![raw::PacketMetadata::EMPTY; 32], vec![0u8; 32 * 1024]),
            raw::PacketBuffer::new(vec![raw::PacketMetadata::EMPTY; 4], vec![0u8; 1024]),
        ));

        let solicitation = router_solicitation(link_local, self.mac);

        let started = std::time::Instant::now();
        let deadline = started + timeout;
        let mut solicitations_sent = 0;
        let mut unusable: Option<slaac::Unusable> = None;

        let offer = loop {
            let due = started + ROUTER_SOLICITATION_INTERVAL * solicitations_sent;
            if solicitations_sent < ROUTER_SOLICITATIONS && std::time::Instant::now() >= due {
                let socket = self.sockets.get_mut::<raw::Socket>(raw_handle);
                if let Err(e) = socket.send_slice(&solicitation) {
                    warn!("Could not queue router solicitation: {:?}", e);
                }
                solicitations_sent += 1;
            }

            self.poll();

            let mut offer = None;
            {
                // The socket sees all ICMPv6 of the link, so everything is drained.
                let socket = self.sockets.get_mut::<raw::Socket>(raw_handle);
                while let Ok(packet) = socket.recv() {
                    if packet.get(IPV6_HEADER_LENGTH) != Some(&ICMPV6_ROUTER_ADVERTISEMENT) {
                        continue;
                    }

                    match slaac::read_offer(packet) {
                        Ok(o) => {
                            offer = Some(o);
                            break;
                        }
                        Err(reason) => {
                            if unusable.is_none_or(|u| reason.rank() > u.rank()) {
                                unusable = Some(reason);
                            }
                        }
                    }
                }
            }

            if let Some(offer) = offer {
                break offer;
            }

            if std::time::Instant::now() >= deadline {
                self.sockets.remove(raw_handle);
                match unusable {
                    Some(reason) => bail!("IPv6 autoconfiguration failed: {}", reason.describe()),
                    None => bail!("IPv6 autoconfiguration timed out after {:?}: No router answered the solicitation.",
                        timeout)
                }
            }

            std::thread::sleep(POLL_SLEEP);
        };

        self.sockets.remove(raw_handle);

        let configuration = Ipv6Configuration {
            address: Ipv6Cidr::new(Ipv6Address::from(slaac::address(offer.prefix, mac)), 64),
            router: Ipv6Address::from(offer.router),
            dns_servers: offer.dns_servers.iter().map(|s| Ipv6Address::from(*s)).collect(),
        };

        debug!("IPv6 autoconfiguration: addr={} router={} dns={:?}",
            configuration.address, configuration.router, configuration.dns_servers);

        let mut stored = true;
        self.iface.update_ip_addrs(|addrs| {
            stored = addrs.push(IpCidr::Ipv6(configuration.address)).is_ok();
        });
        if !stored {
            bail!("IP address storage of the interface is full");
        }

        self.iface
            .routes_mut()
            .add_default_ipv6_route(configuration.router)
            .map_err(|_| anyhow!("failed to add default route"))?;

        match configuration.dns_servers.first() {
            Some(server) => {
                let dns_sock = dns::Socket::new(&[IpAddress::Ipv6(*server)], vec![]);
                self.dns_handle = Some(self.sockets.add(dns_sock));
            }
            None => warn!("The router advertisement names no DNS servers. Hostname control URLs will fail to resolve."),
        }

        self.ipv6 = true;

        Ok(configuration)
    }

    pub fn is_ipv6(&self) -> bool {
        self.ipv6
    }

    pub fn resolve(&mut self, host: &str, timeout: StdDuration) -> Result<IpAddress> {
        let dns_handle = self
            .dns_handle
            .ok_or_else(|| anyhow!("no DNS socket (the network provided no resolvers)"))?;

        let (query_type, record) = if self.ipv6 { (DnsQueryType::Aaaa, "AAAA") } else { (DnsQueryType::A, "A") };

        let query = {
            let sock = self.sockets.get_mut::<dns::Socket>(dns_handle);
            sock.start_query(self.iface.context(), host, query_type)
                .map_err(|e| anyhow!("starting DNS query for '{}': {:?}", host, e))?
        };

        let deadline = std::time::Instant::now() + timeout;
        loop {
            self.poll();

            let result = self
                .sockets
                .get_mut::<dns::Socket>(dns_handle)
                .get_query_result(query);

            match result {
                Ok(addrs) => {
                    let addr = addrs
                        .iter()
                        .copied()
                        .next()
                        .ok_or_else(|| anyhow!("DNS returned no {} records for '{}'", record, host))?;
                    debug!("resolved {} -> {}", host, addr);
                    return Ok(addr);
                }
                Err(dns::GetQueryResultError::Pending) => {
                    if std::time::Instant::now() >= deadline {
                        bail!("DNS query for '{}' timed out", host);
                    }
                    std::thread::sleep(POLL_SLEEP);
                }
                Err(e) => bail!("DNS query for '{}' failed: {:?}", host, e),
            }
        }
    }

    pub fn connect(&mut self, remote: IpAddress, remote_port: u16, connect_timeout: StdDuration)
        -> Result<SocketHandle> {

        let rx = tcp::SocketBuffer::new(vec![0u8; TCP_RX_BUF]);
        let tx = tcp::SocketBuffer::new(vec![0u8; TCP_TX_BUF]);
        let handle = self.sockets.add(tcp::Socket::new(rx, tx));

        let local_port = 49152 + (rand::random::<u16>() % 16000);

        {
            let sock = self.sockets.get_mut::<tcp::Socket>(handle);
            sock.connect(self.iface.context(), (remote, remote_port), local_port)
                .map_err(|e| anyhow!("tcp connect to {}:{} failed: {:?}", remote, remote_port, e))?;
        }

        let deadline = std::time::Instant::now() + connect_timeout;
        loop {
            self.poll();
            let sock = self.sockets.get::<tcp::Socket>(handle);
            if sock.state() == tcp::State::Established {
                return Ok(handle);
            }
            if std::time::Instant::now() >= deadline {
                bail!("tcp connect to {}:{} timed out", remote, remote_port);
            }
            std::thread::sleep(POLL_SLEEP);
        }
    }

    pub fn stream(&mut self, handle: SocketHandle, io_timeout: StdDuration) -> TcpStream<'_, 'a> {
        TcpStream {
            stack: self,
            handle,
            timeout: io_timeout,
        }
    }
}

fn router_solicitation(source: Ipv6Address, mac: EthernetAddress) -> Vec<u8> {
    let destination = Ipv6Address::LINK_LOCAL_ALL_ROUTERS;

    let icmp = Icmpv6Repr::Ndisc(NdiscRepr::RouterSolicit { lladdr: Some(mac.into()) });
    let ip = Ipv6Repr {
        src_addr: source,
        dst_addr: destination,
        next_header: IpProtocol::Icmpv6,
        payload_len: icmp.buffer_len(),
        hop_limit: 255,
    };

    let mut buffer = vec![0u8; IPV6_HEADER_LENGTH + icmp.buffer_len()];
    ip.emit(&mut Ipv6Packet::new_unchecked(&mut buffer[..IPV6_HEADER_LENGTH]));
    icmp.emit(
        &IpAddress::Ipv6(source),
        &IpAddress::Ipv6(destination),
        &mut Icmpv6Packet::new_unchecked(&mut buffer[IPV6_HEADER_LENGTH..]),
        &ChecksumCapabilities::default(),
    );

    buffer
}

pub struct TcpStream<'s, 'a> {
    stack: &'s mut Stack<'a>,
    handle: SocketHandle,
    timeout: StdDuration,
}

impl<'s, 'a> Read for TcpStream<'s, 'a> {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        let deadline = std::time::Instant::now() + self.timeout;
        loop {
            self.stack.poll();
            let sock = self.stack.sockets.get_mut::<tcp::Socket>(self.handle);
            if sock.can_recv() {
                return sock
                    .recv_slice(buf)
                    .map_err(|e| io::Error::new(io::ErrorKind::Other, format!("{:?}", e)));
            }
            // Peer closed / connection finished => clean EOF.
            if !sock.may_recv() {
                return Ok(0);
            }
            if std::time::Instant::now() >= deadline {
                return Err(io::Error::new(io::ErrorKind::TimedOut, "tcp read timeout"));
            }
            std::thread::sleep(POLL_SLEEP);
        }
    }
}

impl<'s, 'a> Write for TcpStream<'s, 'a> {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        self.write_impl(buf)
    }

    fn flush(&mut self) -> io::Result<()> {
        // Push queued bytes out.
        let deadline = std::time::Instant::now() + self.timeout;
        loop {
            self.stack.poll();
            let sock = self.stack.sockets.get::<tcp::Socket>(self.handle);
            if sock.send_queue() == 0 {
                return Ok(());
            }
            if std::time::Instant::now() >= deadline {
                return Err(io::Error::new(io::ErrorKind::TimedOut, "tcp flush timeout"));
            }
            std::thread::sleep(POLL_SLEEP);
        }
    }
}

impl<'s, 'a> TcpStream<'s, 'a> {
    fn write_impl(&mut self, buf: &[u8]) -> io::Result<usize> {
        let deadline = std::time::Instant::now() + self.timeout;
        loop {
            self.stack.poll();
            let sock = self.stack.sockets.get_mut::<tcp::Socket>(self.handle);
            if sock.can_send() {
                return sock
                    .send_slice(buf)
                    .map_err(|e| io::Error::new(io::ErrorKind::Other, format!("{:?}", e)));
            }
            if !sock.may_send() {
                return Err(io::Error::new(io::ErrorKind::BrokenPipe, "tcp closed for send"));
            }
            if std::time::Instant::now() >= deadline {
                return Err(io::Error::new(io::ErrorKind::TimedOut, "tcp write timeout"));
            }
            std::thread::sleep(POLL_SLEEP);
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn router_solicitation_is_a_valid_packet() {
        let mac = EthernetAddress([0xae, 0x3f, 0x99, 0xdf, 0x3d, 0xf5]);
        let source = Ipv6Address::from(slaac::link_local_address(mac.0));
        let packet = router_solicitation(source, mac);

        // IPv6 header, then the solicitation with the source link-layer address option.
        assert_eq!(packet.len(), 40 + 8 + 8);
        assert_eq!(packet[0] >> 4, 6);
        assert_eq!(u16::from_be_bytes([packet[4], packet[5]]), 16);
        assert_eq!(packet[6], 58);
        assert_eq!(packet[7], 255);
        assert_eq!(&packet[8..24], source.as_bytes());
        assert_eq!(&packet[24..40], Ipv6Address::LINK_LOCAL_ALL_ROUTERS.as_bytes());
        assert_eq!(&packet[40..42], &[133, 0]);
        assert_eq!(&packet[48..56], &[1, 1, 0xae, 0x3f, 0x99, 0xdf, 0x3d, 0xf5]);

        let parsed = Ipv6Packet::new_checked(&packet[..]).unwrap();
        let icmp = Icmpv6Packet::new_checked(parsed.payload()).unwrap();
        assert!(icmp.verify_checksum(&IpAddress::Ipv6(source), &IpAddress::Ipv6(Ipv6Address::LINK_LOCAL_ALL_ROUTERS)));
    }

    #[test]
    fn tells_ipv6_frames_apart() {
        let mut frame = vec![0u8; 60];
        frame[12] = 0x86;
        frame[13] = 0xdd;
        assert!(is_ipv6_frame(&frame));

        frame[12] = 0x08;
        frame[13] = 0x00;
        assert!(!is_ipv6_frame(&frame));
        assert!(!is_ipv6_frame(&frame[..10]));
    }
}
