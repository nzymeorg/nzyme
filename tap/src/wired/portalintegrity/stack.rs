use std::io::{self, Read, Write};
use std::time::Duration as StdDuration;

use anyhow::{anyhow, bail, Context, Result};
use log::{debug, info, warn};

use pcap::{Active, Capture};

use smoltcp::iface::{Config, Interface, SocketHandle, SocketSet};
use smoltcp::phy::{self, Device, DeviceCapabilities, Medium};
use smoltcp::socket::{dhcpv4, dns, tcp};
use smoltcp::time::Instant as SmolInstant;
use smoltcp::wire::{
    DnsQueryType, EthernetAddress, HardwareAddress, IpAddress, IpCidr, Ipv4Address, Ipv4Cidr,
};

const TCP_RX_BUF: usize = 32 * 1024;
const TCP_TX_BUF: usize = 8 * 1024;
const POLL_SLEEP: StdDuration = StdDuration::from_millis(1);
const PCAP_MTU: usize = 1514;

pub struct LeaseInfo {
    pub address: Ipv4Cidr,
    pub router: Option<Ipv4Address>,
    pub dhcp_server: Option<Ipv4Address>,
    pub dns_servers: Vec<Ipv4Address>,
}

struct PcapDevice {
    cap: Capture<Active>,
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
        Ok(Self { cap })
    }
}

impl Device for PcapDevice {
    type RxToken<'a> = PcapRxToken;
    type TxToken<'a> = PcapTxToken<'a>;

    fn receive(&mut self, _t: SmolInstant) -> Option<(Self::RxToken<'_>, Self::TxToken<'_>)> {
        let buf = match self.cap.next_packet() {
            Ok(packet) => packet.data.to_vec(),
            Err(_) => return None,
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

        info!(
            "DHCP lease: addr={} router={:?} dns={:?}",
            lease.address, lease.router, lease.dns_servers
        );

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

    pub fn resolve(&mut self, host: &str, timeout: StdDuration) -> Result<IpAddress> {
        let dns_handle = self
            .dns_handle
            .ok_or_else(|| anyhow!("no DNS socket (DHCP provided no resolvers)"))?;

        let query = {
            let sock = self.sockets.get_mut::<dns::Socket>(dns_handle);
            sock.start_query(self.iface.context(), host, DnsQueryType::A)
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
                        .ok_or_else(|| anyhow!("DNS returned no A records for '{}'", host))?;
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