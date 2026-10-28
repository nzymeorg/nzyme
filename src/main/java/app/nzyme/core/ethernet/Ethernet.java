package app.nzyme.core.ethernet;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.ethernet.arp.ARP;
import app.nzyme.core.ethernet.beacons.Beacons;
import app.nzyme.core.ethernet.dhcp.DHCP;
import app.nzyme.core.ethernet.dns.DNS;
import app.nzyme.core.ethernet.dns.tunnels.DnsTunnels;
import app.nzyme.core.ethernet.l4.L4;
import app.nzyme.core.ethernet.nat.NAT;
import app.nzyme.core.ethernet.portalintegrity.PortalIntegrity;
import app.nzyme.core.ethernet.recon.Recon;
import app.nzyme.core.ethernet.spoofing.Spoofing;
import app.nzyme.core.ethernet.icmp.ICMP;
import app.nzyme.core.ethernet.rtsp.RTSP;
import app.nzyme.core.ethernet.socks.SOCKS;
import app.nzyme.core.ethernet.vpn.VPN;
import app.nzyme.core.ethernet.ssh.SSH;
import app.nzyme.core.ethernet.tls.TLS;
import app.nzyme.core.ethernet.l4.tcp.TCP;
import app.nzyme.core.ethernet.time.ntp.NTP;
import app.nzyme.core.ethernet.webrtc.WebRTC;

public class Ethernet {

    private final NzymeNode nzyme;

    private final ARP arp;
    private final DHCP dhcp;
    private final L4 l4;
    private final TCP tcp;
    private final DNS dns;
    private final DnsTunnels dnsTunnels;
    private final SSH ssh;
    private final TLS tls;
    private final SOCKS socks;
    private final VPN vpn;
    private final NTP ntp;
    private final RTSP rtsp;
    private final NAT nat;
    private final WebRTC webRtc;
    private final PortalIntegrity portalIntegrity;
    private final Beacons beacons;
    private final Recon recon;
    private final Spoofing spoofing;
    private final ICMP icmp;

    public Ethernet(NzymeNode nzyme) {
        this.nzyme = nzyme;

        this.arp = new ARP(this);
        this.dhcp = new DHCP(this);
        this.l4 = new L4(this);
        this.tcp = new TCP(this);
        this.dns = new DNS(this);
        this.dnsTunnels = new DnsTunnels(nzyme);
        this.ssh = new SSH(this);
        this.tls = new TLS(this);
        this.socks = new SOCKS(this);
        this.vpn = new VPN(this);
        this.ntp = new NTP(this);
        this.rtsp = new RTSP(this);
        this.nat = new NAT(this);
        this.webRtc = new WebRTC(this);
        this.portalIntegrity = new PortalIntegrity(this);
        this.beacons = new Beacons(nzyme);
        this.recon = new Recon(nzyme);
        this.spoofing = new Spoofing(nzyme);
        this.icmp = new ICMP(this);
    }

    public NzymeNode getNzyme() {
        return this.nzyme;
    }

    public ARP arp() {
        return arp;
    }

    public DHCP dhcp() {
        return dhcp;
    }

    public L4 l4() {
        return l4;
    }

    public TCP tcp() {
        return tcp;
    }

    public DNS dns() {
        return dns;
    }

    public SSH ssh() {
        return ssh;
    }

    public TLS tls() {
        return tls;
    }

    public SOCKS socks() {
        return socks;
    }

    public VPN vpn() {
        return vpn;
    }

    public NTP ntp() {
        return ntp;
    }

    public RTSP rtsp() {
        return rtsp;
    }

    public NAT nat() {
        return nat;
    }

    public WebRTC webRtc() {
        return webRtc;
    }

    public Beacons beacons() {
        return beacons;
    }

    public Recon recon() {
        return recon;
    }

    public Spoofing spoofing() {
        return spoofing;
    }

    public DnsTunnels dnsTunnels() {
        return dnsTunnels;
    }

    public ICMP icmp() {
        return icmp;
    }

    public PortalIntegrity portalIntegrity() {
        return portalIntegrity;
    }
}
