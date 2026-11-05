import numeral from "numeral";

const countries = require("i18n-iso-countries");
countries.registerLocale(require("i18n-iso-countries/langs/en.json"));

export function countryName(code) {
  if (!code) {
    return null;
  }
  const name = countries.getName(code, "en", {select: "official"});
  return name ? name : code;
}

export function formatBytes(bytes) {
  if (bytes === null || bytes === undefined) {
    return "n/a";
  }

  return numeral(bytes).format("0.0b");
}

export function formatCount(count) {
  return numeral(count).format("0,0");
}

export const DIMENSION_TITLES = {
  TCP_FINGERPRINT: "TCP Fingerprint",
  JA4: "JA4 (TLS Client)",
  DHCP_FINGERPRINT: "DHCP Fingerprint",
  DHCP_VENDOR_CLASS: "DHCP Vendor Class",
  NTP_CLIENT_VERSION: "NTP Client Version",
  TLS_VERSION: "TLS Version",
  PROTOCOL_CLIENT: "Protocol (as Client)",
  PROTOCOL_SERVER: "Protocol (as Server)",
  INBOUND_PORT: "Inbound Port",
  INBOUND_PORT_PROTOCOL: "Protocol on Inbound Port",
  EXTERNAL_ASN: "External Network",
  EXTERNAL_COUNTRY: "External Country",
  GATEWAY_MAC: "Gateway",
  DHCP_SERVER: "DHCP Server",
  DNS_SERVER: "DNS Server",
  NTP_SERVER: "NTP Server",
  ROLE: "Role",
  NAME_RESOLUTION_PROTOCOL: "Name Resolution Protocol",
  TAP: "Tap"
}

export function dimensionTitle(dimension) {
  return DIMENSION_TITLES[dimension] ? DIMENSION_TITLES[dimension] : dimension;
}

export const ROLE_TITLES = {
  GATEWAY: "Gateway",
  DHCP_SERVER: "DHCP Server",
  DNS_SERVER: "DNS Server",
  NTP_SERVER: "NTP Server",
  NAME_RESOLUTION_RESPONDER: "Name Resolution Responder",
  SSH_SERVER: "SSH Server",
  TLS_SERVER: "TLS Server",
  HTTP_SERVER: "HTTP Server",
  SOCKS_SERVER: "SOCKS Server",
  RTSP_SERVER: "RTSP Server",
  STUN_SERVER: "STUN Server",
  TURN_SERVER: "TURN Server",
  VPN_SERVER: "VPN Server"
}

export function roleTitle(role) {
  return ROLE_TITLES[role] ? ROLE_TITLES[role] : role;
}

const ATTRIBUTE_CHANGES = {
  TCP_FINGERPRINT: { action: "New TCP fingerprint", explanation: () => "The asset's TCP stack presented a fingerprint not seen from it before, which can mean a different operating system or network stack." },
  JA4: { action: "New TLS client fingerprint", explanation: () => "The asset's TLS client presented a JA4 fingerprint not seen from it before, which usually means different client software." },
  DHCP_FINGERPRINT: { action: "New DHCP fingerprint", explanation: () => "The asset sent DHCP requests with an option fingerprint not seen from it before." },
  DHCP_VENDOR_CLASS: { action: "New DHCP vendor class", explanation: () => "The asset identified itself to DHCP servers with a vendor class not seen from it before." },
  NTP_CLIENT_VERSION: { action: "New NTP client version", explanation: () => "The asset sent NTP requests with a client version not seen from it before." },
  TLS_VERSION: { action: "New TLS version", explanation: () => "The asset negotiated a TLS version it had not used before." },
  PROTOCOL_CLIENT: { action: "New client protocol", explanation: (v) => "The asset initiated " + v + " sessions for the first time." },
  PROTOCOL_SERVER: { action: "New server protocol", explanation: (v) => "The asset served " + v + " sessions for the first time." },
  INBOUND_PORT: { action: "New inbound port", explanation: () => "The asset accepted sessions on this port for the first time. This is a port it listens on, not a port it connected to." },
  INBOUND_PORT_PROTOCOL: { action: "New protocol on inbound port", explanation: () => "The asset served this protocol on this port for the first time." },
  EXTERNAL_ASN: { action: "New external network", explanation: () => "The asset exchanged traffic with this autonomous system for the first time." },
  EXTERNAL_COUNTRY: { action: "New external country", explanation: () => "The asset exchanged traffic with addresses in this country for the first time." },
  GATEWAY_MAC: { action: "New gateway", explanation: () => "The asset started sending traffic through this gateway." },
  DHCP_SERVER: { action: "New DHCP server", explanation: () => "The asset obtained a lease from this DHCP server for the first time." },
  DNS_SERVER: { action: "New DNS server", explanation: () => "The asset started sending DNS queries to this server." },
  NTP_SERVER: { action: "New NTP server", explanation: () => "The asset started synchronizing its clock with this server." },
  ROLE: { action: "New role", explanation: () => "The asset was observed on the server side of the protocol behind this role for the first time." },
  NAME_RESOLUTION_PROTOCOL: { action: "New name resolution protocol", explanation: (v) => "The asset answered " + v + " queries for the first time." },
  TAP: { action: "New tap", explanation: () => "This tap observed the asset for the first time." }
}

export function timelineKindTitle(kind) {
  if (kind.startsWith("ATTRIBUTE:")) {
    const dimension = kind.substring("ATTRIBUTE:".length);
    return DIMENSION_TITLES[dimension] ? DIMENSION_TITLES[dimension] : dimension;
  }

  switch (kind) {
    case "ASSET_FIRST_SEEN": return "First Seen";
    case "HOSTNAME": return "Hostname";
    case "IP_ADDRESS": return "IP Address";
    case "ALERT": return "Alert";
    default: return kind;
  }
}

export function timelineKindOf(event) {
  return event.type === "ATTRIBUTE" ? "ATTRIBUTE:" + event.detail : event.type;
}

export function describeChange(type, value, detail) {
  switch (type) {
    case "ASSET_FIRST_SEEN":
      return { action: "First seen", value: null, machineData: false, explanation: "The asset appeared on the network for the first time." };
    case "HOSTNAME":
      return { action: "New hostname", value: value, machineData: true, explanation: "The asset was seen under a hostname it had not used before, learned via " + detail + "." };
    case "IP_ADDRESS":
      return { action: "New IP address", value: value, machineData: true, explanation: "The asset used an IP address it had not used before, learned via " + detail + "." };
    case "ALERT":
      return { action: "Alert", value: value ? value.replace(/_/g, " ") : null, machineData: false, explanation: "A detection alert involved this asset." };
    case "ATTRIBUTE": {
      const change = ATTRIBUTE_CHANGES[detail];
      if (!change) {
        return { action: detail ? "New " + dimensionTitle(detail).toLowerCase() : "New attribute", value: value, machineData: true, explanation: null };
      }
      const shown = detail === "ROLE" ? roleTitle(value) : value;
      return { action: change.action, value: shown, machineData: detail !== "ROLE", explanation: change.explanation(shown) };
    }
    default:
      return { action: type, value: value, machineData: false, explanation: null };
  }
}

export const RISK_SIGNALS = {
  PLAINTEXT_HTTP: { title: "Plaintext HTTP", description: "Initiated unencrypted HTTP sessions." },
  PLAINTEXT_RTSP: { title: "Plaintext RTSP", description: "Initiated unencrypted RTSP streaming sessions." },
  LEGACY_TLS: { title: "Legacy TLS", description: "Negotiated TLS versions that are no longer considered secure." },
  SELF_SIGNED_TLS_SERVERS: { title: "Self-Signed TLS Servers", description: "Connected to TLS servers presenting self-signed certificates. Only visible for TLS 1.2 and older, because TLS 1.3 encrypts certificates." },
  UNTRUSTED_TLS_CHAINS: { title: "Untrusted TLS Chains", description: "Connected to TLS servers with certificate chains that failed validation. Only visible for TLS 1.2 and older, because TLS 1.3 encrypts certificates." },
  INBOUND_EXTERNAL_SESSIONS: { title: "Inbound from the Internet", description: "Accepted sessions that were started by addresses outside the local network." },
  DNS_TUNNEL: { title: "DNS Tunnel", description: "DNS tunneling detected from this asset." },
  ICMP_TUNNEL: { title: "ICMP Tunnel", description: "ICMP tunneling detected from this asset." },
  BEACON_MALICIOUS: { title: "Malicious Beacon", description: "Source of a beacon classified as malicious." },
  BEACON_SUSPICIOUS: { title: "Suspicious Beacon", description: "Source of a beacon classified as suspicious." },
  BEACON_UNCLASSIFIED: { title: "Unclassified Beacon", description: "Source of a beacon that has not been classified yet." },
  RECON_SCANNER: { title: "Network Scanner", description: "Performed network scans." },
  ICMP_REDIRECT_SENDER: { title: "ICMP Redirect Sender", description: "Sent ICMP redirects to other hosts." },
  TRACEROUTE_SOURCE: { title: "Traceroute Source", description: "Performed traceroutes." },
  SPOOFING_ARP_ACTOR: { title: "ARP Spoofer", description: "Detected as the source of ARP poisoning." },
  SPOOFING_DHCP_ACTOR: { title: "Rogue DHCP Server", description: "Detected as a rogue or misbehaving DHCP server." },
  SPOOFING_DNS_ACTOR: { title: "DNS Spoofer", description: "Detected as the source of forged DNS responses." },
  SPOOFING_NAME_RESOLUTION_ACTOR: { title: "Name Resolution Poisoner", description: "Detected as the source of name resolution poisoning." },
  ACTIVE_ALERTS: { title: "Active Alerts", description: "Involved in alerts that are currently active." },
  RANDOMIZED_MAC: { title: "Randomized MAC Address", description: "The MAC address is locally administered, which usually means randomization. Identity may change." },
  NO_CONTEXT: { title: "No Context", description: "No MAC address context exists. This asset is not described or approved by an analyst." }
}

export function riskSignalTitle(signal) {
  return RISK_SIGNALS[signal] ? RISK_SIGNALS[signal].title : signal;
}

export function riskSignalDescription(signal) {
  return RISK_SIGNALS[signal] ? RISK_SIGNALS[signal].description : null;
}

export function severityBadgeClass(severity) {
  switch (severity) {
    case "CRITICAL": return "bg-danger";
    case "WARNING": return "bg-warning text-dark";
    default: return "bg-info text-dark";
  }
}

export const FINDING_TITLES = {
  RECON_SCAN: "Network Scans",
  BEACON_UNKNOWN: "Beacons (Unclassified)",
  BEACON_BENIGN: "Beacons (Benign)",
  BEACON_SUSPICIOUS: "Beacons (Suspicious)",
  BEACON_MALICIOUS: "Beacons (Malicious)",
  DNS_TUNNEL: "DNS Tunnels",
  ICMP_TUNNEL: "ICMP Tunnels",
  ICMP_TRACEROUTE: "Traceroutes",
  ICMP_REDIRECT: "ICMP Redirects",
  SPOOFING_ARP: "ARP Spoofing",
  SPOOFING_DHCP: "DHCP Spoofing",
  SPOOFING_DNS: "DNS Spoofing",
  SPOOFING_NAME_RESOLUTION: "Name Resolution Spoofing"
}

export function findingTitle(type) {
  return FINDING_TITLES[type] ? FINDING_TITLES[type] : type;
}

export const EXCLUSION_TITLES = {
  DHCP_FINGERPRINT: "DHCP Fingerprint",
  SPOOFING: "Spoofing",
  BEACON: "Beacons",
  ICMP: "ICMP",
  RECON: "Recon",
  DNS_TUNNEL: "DNS Tunnels"
}

export function exclusionTitle(type) {
  return EXCLUSION_TITLES[type] ? EXCLUSION_TITLES[type] : type;
}

// Role in an alert or finding, not the network role of roleTitle().
export function alertRoleTitle(role) {
  switch (role) {
    case "ACTOR": return "Actor";
    case "VICTIM": return "Victim";
    case "ACTOR_AND_VICTIM": return "Actor and Victim";
    default: return "Subject";
  }
}

export function sortRows(rows, column, direction, accessors) {
  const accessor = accessors[column];
  if (!accessor) {
    return rows;
  }

  const sign = direction === "ASC" ? 1 : -1;
  return [...rows].sort((a, b) => {
    const va = accessor(a);
    const vb = accessor(b);
    if (va === null || va === undefined) return (vb === null || vb === undefined) ? 0 : 1;
    if (vb === null || vb === undefined) return -1;
    if (typeof va === "number" && typeof vb === "number") return sign * (va - vb);
    return sign * String(va).localeCompare(String(vb), undefined, {numeric: true, sensitivity: "base"});
  });
}

export function macFilter(field, mac) {
  const filters = {};
  filters[field] = [{ field: field, operator: "equals", value: mac }];
  return filters;
}

export function filteredLink(route, filters) {
  return route + "?filters=" + encodeURIComponent(JSON.stringify(filters));
}

export const UNRESOLVED_EXTERNAL_LABEL = "Internet (unresolved)";

export function isUnresolvedExternal(edge) {
  return edge.peer_type === "EXTERNAL" && !edge.peer_asn_number && !edge.peer_country_code;
}

export function externalPeerLabel(edge) {
  if (edge.peer_asn_name) {
    return edge.peer_asn_name;
  }
  if (edge.peer_asn_number) {
    return "AS" + edge.peer_asn_number;
  }
  if (edge.peer_country_code) {
    return edge.peer_country_code;
  }
  return UNRESOLVED_EXTERNAL_LABEL;
}
