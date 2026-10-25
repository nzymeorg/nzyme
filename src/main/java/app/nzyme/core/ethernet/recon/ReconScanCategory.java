package app.nzyme.core.ethernet.recon;

import java.util.List;

public enum ReconScanCategory {

    SCANS(List.of(
            ReconScanType.TCP_PORT_SCAN.name(),
            ReconScanType.TCP_HOST_SWEEP.name(),
            ReconScanType.UDP_PORT_SCAN.name(),
            ReconScanType.UDP_HOST_SWEEP.name(),
            ReconScanType.ARP_SWEEP.name(),
            ReconScanType.ICMP_SWEEP.name(),
            ReconScanType.SLOW_SCAN.name()
    )),
    FINGERPRINTING(List.of(ReconScanType.OS_FINGERPRINTING.name()));

    private final List<String> types;

    ReconScanCategory(List<String> types) {
        this.types = types;
    }

    public List<String> types() {
        return types;
    }

}
