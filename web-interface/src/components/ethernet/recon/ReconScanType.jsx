import React from "react";

export const reconScanTypeToName = (type) => {
  switch (type) {
    case "TCP_PORT_SCAN": return "TCP Port Scan";
    case "TCP_HOST_SWEEP": return "TCP Host Sweep";
    case "UDP_PORT_SCAN": return "UDP Port Scan";
    case "UDP_HOST_SWEEP": return "UDP Host Sweep";
    case "ARP_SWEEP": return "ARP Sweep";
    case "ICMP_SWEEP": return "Ping Sweep";
    case "DNS_REVERSE_SWEEP": return "Reverse DNS Sweep";
    case "SLOW_SCAN": return "Slow Scan";
    case "OS_FINGERPRINTING": return "OS Fingerprinting";
    default: return type;
  }
}

export default function ReconScanType({type}) {
  return <span title={type}>{reconScanTypeToName(type)}</span>
}
