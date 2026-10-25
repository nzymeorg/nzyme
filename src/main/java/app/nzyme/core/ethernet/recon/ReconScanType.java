package app.nzyme.core.ethernet.recon;

public enum ReconScanType {

    TCP_PORT_SCAN("TCP Port Scan"),
    TCP_HOST_SWEEP("TCP Host Sweep"),
    UDP_PORT_SCAN("UDP Port Scan"),
    UDP_HOST_SWEEP("UDP Host Sweep"),
    ARP_SWEEP("ARP Sweep"),
    ICMP_SWEEP("Ping Sweep"),
    OS_FINGERPRINTING("OS Fingerprinting");

    private final String humanReadable;

    ReconScanType(String humanReadable) {
        this.humanReadable = humanReadable;
    }

    public String getHumanReadable() {
        return humanReadable;
    }

    public boolean isPortScan() {
        return this == TCP_PORT_SCAN || this == UDP_PORT_SCAN;
    }

    public boolean isFingerprinting() {
        return this == OS_FINGERPRINTING;
    }

}
