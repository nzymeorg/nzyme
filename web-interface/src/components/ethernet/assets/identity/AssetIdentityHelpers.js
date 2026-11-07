import React from "react";

export const CHANGE_SIGNALS = {
  TCP_STACK_REPLACED: { title: "TCP stack replaced", description: "The device's own TCP stack behind the address changed and the old one has not been seen since." },
  TCP_SERVER_FINGERPRINT_REPLACED: { title: "TCP server stack replaced", description: "The stack that answers connections changed and the old one has not been seen since." },
  SOURCE_PORT_RANGE_REPLACED: { title: "Source port range replaced", description: "Client ports come from a different range, which a stack normally never changes." },
  IP_ID_BEHAVIOR_REPLACED: { title: "IP ID behavior replaced", description: "IP identification values are picked in a different way." },
  TCP_TIMESTAMP_RATE_REPLACED: { title: "TCP timestamp rate replaced", description: "The TCP timestamp clock ticks at a different rate." },
  DHCP_FINGERPRINT_REPLACED: { title: "DHCP fingerprint replaced", description: "A different DHCP client is in use and the old one has not been seen since." },
  DHCP_FINGERPRINT_ADDED: { title: "DHCP fingerprint added", description: "A new DHCP fingerprint appeared next to the known ones." },
  DHCP_VENDOR_CLASS_REPLACED: { title: "DHCP vendor class replaced", description: "The vendor class the device sends to DHCP servers changed." },
  DHCP_VENDOR_CLASS_ADDED: { title: "DHCP vendor class added", description: "A new vendor class appeared next to the known ones." },
  DHCP_CLIENT_IDENTIFIER_REPLACED: { title: "DHCP client identifier replaced", description: "The device identifies itself to DHCP servers with a different client identifier." },
  DHCP_PARAMETER_REQUEST_LIST_REPLACED: { title: "DHCP parameter request list replaced", description: "The device requests a different set of DHCP options, which usually means a different DHCP client." },
  JA4_REPLACED: { title: "TLS clients replaced", description: "New TLS client fingerprints appeared and none of the old ones has been seen since." },
  JA4_ADDED: { title: "TLS clients added", description: "Several new TLS client fingerprints appeared next to the known ones." },
  NTP_CLIENT_VERSION_REPLACED: { title: "NTP client replaced", description: "The device synchronizes its clock with a different NTP client." },
  PROTOCOL_SERVER_ADDED: { title: "New server protocols", description: "The device started serving protocols it did not serve before." },
  INBOUND_PORT_ADDED: { title: "New inbound ports", description: "The device started accepting connections on ports it did not listen on before." },
  HOSTNAME_REPLACED: { title: "Hostname replaced", description: "The device uses a new hostname and the old ones have not been seen since." },
  HOSTNAME_ADDED: { title: "Hostname added", description: "A new hostname appeared next to the known ones." }
};

export function changeSignalTitle(signal) {
  return CHANGE_SIGNALS[signal] ? CHANGE_SIGNALS[signal].title : signal.replace(/_/g, " ").toLowerCase();
}

export function changeSignalDescription(signal) {
  return CHANGE_SIGNALS[signal] ? CHANGE_SIGNALS[signal].description : null;
}

export const LINK_EVIDENCE = {
  HOSTNAME: { title: "Shared hostname", description: "Both assets used the same hostname. Rare hostnames count more than ones many assets share." },
  IP_ADDRESS: { title: "Shared IP address", description: "Both assets used the same IP address." },
  DHCP_CLIENT_IDENTIFIER: { title: "Shared DHCP client identifier", description: "Both assets sent the same DHCP client identifier, and it is not derived from either MAC address." },
  DHCP_FINGERPRINT: { title: "Shared DHCP fingerprint", description: "Both assets use the same DHCP client." },
  DHCP_PARAMETER_REQUEST_LIST: { title: "Shared DHCP parameter request list", description: "Both assets request the same DHCP options." },
  DHCP_VENDOR_CLASS: { title: "Shared DHCP vendor class", description: "Both assets send the same DHCP vendor class." },
  TCP_STACK: { title: "Shared TCP stack", description: "Both assets have the same TCP stack, sending directly rather than through a router or NAT box." },
  TCP_STACK_MISMATCH: { title: "Different TCP stacks", description: "The assets have different TCP stacks, which counts against them being one device." },
  TCP_SERVER_FINGERPRINT: { title: "Shared TCP server fingerprint", description: "Both assets answer connections with the same stack." },
  JA4: { title: "Shared TLS clients", description: "The TLS client fingerprints of the assets overlap." },
  INBOUND_PORTS: { title: "Same inbound ports", description: "Both assets accept connections on exactly the same ports." },
  INBOUND_PORTS_MISMATCH: { title: "Different inbound ports", description: "The assets listen on entirely different ports, which counts against them being one device." },
  NTP_CLIENT_VERSION: { title: "Shared NTP client", description: "Both assets use the same NTP client." },
  SOURCE_PORT_RANGE: { title: "Same source port range", description: "Both assets pick client ports from the same range." },
  SOURCE_PORT_RANGE_MISMATCH: { title: "Different source port ranges", description: "The assets pick client ports from different ranges, which counts against them being one device." },
  TCP_TIMESTAMP_RATE: { title: "Same TCP timestamp rate", description: "The TCP timestamp clocks of both assets tick at the same rate." },
  VENDOR_PREFIX: { title: "Same vendor prefix", description: "Both MAC addresses have the same vendor prefix." },
  RANDOMIZED_MAC: { title: "Randomized MAC address", description: "The new address is locally administered, which usually means the device randomizes its address." }
};

export function linkEvidenceTitle(signal) {
  return LINK_EVIDENCE[signal] ? LINK_EVIDENCE[signal].title : signal.replace(/_/g, " ").toLowerCase();
}

export function linkEvidenceDescription(signal) {
  return LINK_EVIDENCE[signal] ? LINK_EVIDENCE[signal].description : null;
}

export function linkKindTitle(kind) {
  switch (kind) {
    case "SUCCESSOR": return "Moved to new address";
    case "ALTERNATE": return "Alternates between addresses";
    default: return kind;
  }
}

export function linkKindDescription(kind) {
  switch (kind) {
    case "SUCCESSOR": return "The old address went silent when the new one appeared.";
    case "ALTERNATE": return "The old address came back later, so the device uses both. A laptop on a dock and on Wi-Fi looks like this.";
    default: return null;
  }
}

export function linkStatus(status) {
  switch (status) {
    case "CONFIRMED": return <span className="text-success">Confirmed</span>;
    case "DISMISSED": return <span className="text-muted">Dismissed</span>;
    default: return <span>Open</span>;
  }
}

export function confidenceClass(confidence) {
  if (confidence >= 80) {
    return "text-success";
  }
  if (confidence >= 60) {
    return "";
  }
  return "text-muted";
}
