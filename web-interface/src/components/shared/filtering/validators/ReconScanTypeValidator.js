import {reconScanTypeToName} from "../../../ethernet/recon/ReconScanType";

export const ALLOWED_RECON_SCAN_TYPE_VALUES = [
  "TCP_PORT_SCAN", "TCP_HOST_SWEEP", "UDP_PORT_SCAN", "UDP_HOST_SWEEP", "ARP_SWEEP", "ICMP_SWEEP", "DNS_REVERSE_SWEEP", "SLOW_SCAN", "OS_FINGERPRINTING"
];

export const RECON_SCAN_TYPE_SELECT_OPTIONS = ALLOWED_RECON_SCAN_TYPE_VALUES.map((type) => {
  return {name: reconScanTypeToName(type), value: type};
});
