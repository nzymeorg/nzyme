import ApiRoutes from "../../util/ApiRoutes";
import {serializeTimeRange} from "../shared/timerange/TimeRangeUrl";
import {Absolute, Presets} from "../shared/timerange/TimeRange";

const encodeRange = (window) => {
  if (!window) {
    return null;
  }

  return encodeURIComponent(JSON.stringify(serializeTimeRange(Absolute(new Date(window.from), new Date(window.to)))));
}

const withRange = (path, window, keys = ["timerange"]) => {
  const range = encodeRange(window);
  if (!range) {
    return path;
  }

  return path + "?" + keys.map((k) => "tr_" + k + "=" + range).join("&");
}

const alertsPath = (params, window) => {
  const filters = {};
  const add = (field, value) => {
    if (value) {
      filters[field] = [{field: field, operator: "equals", value: value}];
    }
  }
  add("subsystem", params.subsystem);
  add("type", params.type);
  add("status", params.status);

  let path = ApiRoutes.ALERTS.INDEX + "?filters=" + encodeURIComponent(JSON.stringify(filters));
  const range = encodeRange(window);
  if (range) {
    path += "&tr_timerange=" + range;
  }

  return path;
}

// Open links are a review queue, not events of the window, so the page opens with the status filter and all time.
const openAssetLinksPath = () => {
  const filters = {status: [{field: "status", operator: "equals", value: "OPEN"}]};
  return ApiRoutes.ETHERNET.ASSETS.IDENTITY.LINKS + "?filters=" + encodeURIComponent(JSON.stringify(filters))
    + "&tr_identity=" + encodeURIComponent(JSON.stringify(serializeTimeRange(Presets.ALL_TIME)));
}

export function resolveBriefingLink(link, window) {
  if (!link || !link.target) {
    return null;
  }

  const p = link.params || {};

  switch (link.target) {
    case "ALERTS": return alertsPath(p, window);
    case "ALERT": return ApiRoutes.ALERTS.DETAILS(p.uuid);
    case "ALERT_SUBSCRIPTIONS": return ApiRoutes.ALERTS.SUBSCRIPTIONS.INDEX;
    case "TAP": return ApiRoutes.SYSTEM.TAPS.DETAILS(p.uuid);
    case "TAPS": return ApiRoutes.SYSTEM.TAPS.INDEX;
    case "HEALTH": return ApiRoutes.SYSTEM.HEALTH.INDEX;
    case "CLUSTER": return ApiRoutes.SYSTEM.CLUSTER.INDEX;
    case "SYSTEM_EVENTS": return ApiRoutes.SYSTEM.EVENTS.INDEX;
    case "SESSIONS": return ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX;
    case "ORGANIZATION": return ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(p.uuid);
    case "LOCATIONS": return ApiRoutes.LOCATIONS.INDEX;
    case "LOCATION": return ApiRoutes.LOCATIONS.DETAILS(p.uuid);
    case "DOT11_BRIEFING": return ApiRoutes.DOT11.OVERVIEW;
    case "ETHERNET_BRIEFING": return ApiRoutes.ETHERNET.OVERVIEW;
    case "BLUETOOTH_BRIEFING": return ApiRoutes.BLUETOOTH.OVERVIEW;
    case "BLUETOOTH_MONITORS": return ApiRoutes.BLUETOOTH.MONITORING.MONITORS.INDEX;
    case "DOT11_BSSIDS": return withRange(ApiRoutes.DOT11.NETWORKS.BSSIDS.INDEX, window);
    case "DOT11_BSSID": return ApiRoutes.DOT11.NETWORKS.BSSIDS.DETAILS(p.bssid);
    case "DOT11_CLIENTS": return withRange(ApiRoutes.DOT11.CLIENTS.CONNECTED, window);
    case "DOT11_CLIENT": return ApiRoutes.DOT11.CLIENTS.DETAILS(p.mac);
    case "DOT11_DISCO": return withRange(ApiRoutes.DOT11.DISCO.INDEX, window);
    case "DOT11_MONITORING": return ApiRoutes.DOT11.MONITORING.INDEX;
    case "DOT11_MONITORING_CREATE": return ApiRoutes.DOT11.MONITORING.CREATE;
    case "DOT11_MONITORED_NETWORK": return ApiRoutes.DOT11.MONITORING.SSID_DETAILS(p.uuid);
    case "DOT11_MONITORED_CLIENTS": return ApiRoutes.DOT11.MONITORING.CLIENTS_CONFIGURATION(p.uuid);
    case "DOT11_KNOWN_NETWORKS": return ApiRoutes.DOT11.MONITORING.SSIDS.INDEX;
    case "ETHERNET_ASSETS": return withRange(ApiRoutes.ETHERNET.ASSETS.INDEX, window);
    case "ETHERNET_ASSET": return ApiRoutes.ETHERNET.ASSETS.DETAILS(p.uuid);
    case "BEACONS": return withRange(ApiRoutes.ETHERNET.BEACONS.INDEX, window);
    case "BEACON": return ApiRoutes.ETHERNET.BEACONS.DETAILS(p.uuid);
    case "RECON_SCANS": return withRange(ApiRoutes.ETHERNET.RECON.SCANS.INDEX, window);
    case "RECON_SCAN": return ApiRoutes.ETHERNET.RECON.SCANS.DETAILS(p.uuid);
    case "SPOOFING_ARP": return p.uuid ? ApiRoutes.ETHERNET.SPOOFING.ARP.DETAILS(p.uuid) : withRange(ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX, window);
    case "SPOOFING_DHCP": return p.uuid ? ApiRoutes.ETHERNET.SPOOFING.DHCP.DETAILS(p.uuid) : withRange(ApiRoutes.ETHERNET.SPOOFING.DHCP.INDEX, window);
    case "SPOOFING_DNS": return p.uuid ? ApiRoutes.ETHERNET.SPOOFING.DNS.DETAILS(p.uuid) : withRange(ApiRoutes.ETHERNET.SPOOFING.DNS.INDEX, window);
    case "SPOOFING_NAMERES": return p.uuid ? ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.DETAILS(p.uuid) : withRange(ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.INDEX, window);
    case "DNS_TUNNELS": return withRange(ApiRoutes.ETHERNET.DNS.TUNNELS.INDEX, window);
    case "DNS_TUNNEL": return ApiRoutes.ETHERNET.DNS.TUNNELS.DETAILS(p.uuid);
    case "ICMP_TUNNELS": return withRange(ApiRoutes.ETHERNET.ICMP.TUNNELS.INDEX, window);
    case "ICMP_TUNNEL": return ApiRoutes.ETHERNET.ICMP.TUNNELS.DETAILS(p.uuid);
    case "ICMP_REDIRECTS": return withRange(ApiRoutes.ETHERNET.ICMP.REDIRECTS.INDEX, window);
    case "ICMP_REDIRECT": return ApiRoutes.ETHERNET.ICMP.REDIRECTS.DETAILS(p.uuid);
    case "ICMP_TRACEROUTES": return withRange(ApiRoutes.ETHERNET.ICMP.TRACEROUTES.INDEX, window);
    case "ICMP_TRACEROUTE": return ApiRoutes.ETHERNET.ICMP.TRACEROUTES.DETAILS(p.uuid);
    case "PORTAL_INTEGRITY": return withRange(ApiRoutes.ETHERNET.PORTAL_INTEGRITY.INDEX, window);
    case "BEACONS_SETTINGS": return ApiRoutes.ETHERNET.BEACONS.SETTINGS;
    case "RECON_SETTINGS": return ApiRoutes.ETHERNET.RECON.SCANS.SETTINGS;
    case "SPOOFING_SETTINGS": return ApiRoutes.ETHERNET.SPOOFING.SETTINGS;
    case "DNS_TUNNELS_SETTINGS": return ApiRoutes.ETHERNET.DNS.TUNNELS.SETTINGS;
    case "ICMP_SETTINGS": return ApiRoutes.ETHERNET.ICMP.SETTINGS;
    case "ETHERNET_MONITORS": return ApiRoutes.ETHERNET.MONITORING.MONITORS.INDEX;
    case "SSH": return withRange(ApiRoutes.ETHERNET.REMOTE.SSH.INDEX, window);
    case "SOCKS": return withRange(ApiRoutes.ETHERNET.TUNNELS.SOCKS.INDEX, window);
    case "VPN": return withRange(ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX, window);
    case "RTSP": return withRange(ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX, window);
    case "WEBRTC": return withRange(ApiRoutes.ETHERNET.STREAMS.WEBRTC.INDEX, window);
    case "L4": return withRange(ApiRoutes.ETHERNET.L4.OVERVIEW, window);
    case "DNS": return withRange(ApiRoutes.ETHERNET.DNS.INDEX, window);
    case "DHCP": return withRange(ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX, window);
    case "ASSET_IDENTITY_LINKS": return openAssetLinksPath();
    case "CONTEXT_NETWORKS": return ApiRoutes.CONTEXT.NETWORKS.INDEX;
    case "BLUETOOTH_DEVICES": return withRange(ApiRoutes.BLUETOOTH.DEVICES.INDEX, window);
    case "BLUETOOTH_DEVICE": return ApiRoutes.BLUETOOTH.DEVICES.DETAILS(p.mac);
    case "UAVS": return withRange(ApiRoutes.UAV.INDEX, window);
    case "UAV": return ApiRoutes.UAV.DETAILS(p.identifier);
    default: return null;
  }
}

const LINK_LABELS = {
  ALERTS: "Alerts", ALERT: "Alert details", ALERT_SUBSCRIPTIONS: "Alert subscriptions",
  TAP: "Tap details", TAPS: "Taps", HEALTH: "Health", CLUSTER: "Cluster",
  SYSTEM_EVENTS: "System events", SESSIONS: "Sessions", ORGANIZATION: "Organization", LOCATIONS: "Locations", LOCATION: "Location",
  DOT11_BRIEFING: "WiFi briefing", ETHERNET_BRIEFING: "Ethernet briefing", BLUETOOTH_BRIEFING: "Bluetooth briefing",
  BLUETOOTH_MONITORS: "Monitors",
  DOT11_BSSIDS: "Access points", DOT11_BSSID: "Access point", DOT11_CLIENTS: "Clients", DOT11_CLIENT: "Client",
  DOT11_DISCO: "Disconnection activity", DOT11_MONITORING: "Monitored networks",
  DOT11_MONITORING_CREATE: "Add monitored network", DOT11_MONITORED_NETWORK: "Network configuration",
  DOT11_MONITORED_CLIENTS: "Client monitoring", DOT11_KNOWN_NETWORKS: "Known networks",
  ETHERNET_ASSETS: "Assets", ETHERNET_ASSET: "Asset", BEACONS: "Beacons", BEACON: "Beacon",
  RECON_SCANS: "Recon scans", RECON_SCAN: "Scan", SPOOFING_ARP: "ARP spoofing", SPOOFING_DHCP: "DHCP servers",
  SPOOFING_DNS: "DNS spoofing", SPOOFING_NAMERES: "Name resolution spoofing",
  DNS_TUNNELS: "DNS tunnels", DNS_TUNNEL: "DNS tunnel", ICMP_TUNNELS: "ICMP tunnels", ICMP_TUNNEL: "ICMP tunnel",
  ICMP_REDIRECTS: "ICMP redirects", ICMP_REDIRECT: "ICMP redirect", ICMP_TRACEROUTES: "Traceroutes",
  ICMP_TRACEROUTE: "Traceroute", PORTAL_INTEGRITY: "Portal integrity",
  BEACONS_SETTINGS: "Beacon settings", RECON_SETTINGS: "Recon settings", SPOOFING_SETTINGS: "Spoofing settings",
  DNS_TUNNELS_SETTINGS: "DNS tunnel settings", ICMP_SETTINGS: "ICMP settings", ETHERNET_MONITORS: "Monitors",
  SSH: "SSH sessions", SOCKS: "SOCKS tunnels", VPN: "VPN tunnels", RTSP: "RTSP streams", WEBRTC: "WebRTC",
  L4: "Connections", DNS: "DNS", DHCP: "DHCP", CONTEXT_NETWORKS: "Internal networks",
  ASSET_IDENTITY_LINKS: "MAC address changes",
  BLUETOOTH_DEVICES: "Bluetooth devices", BLUETOOTH_DEVICE: "Bluetooth device", UAVS: "UAVs", UAV: "UAV"
}

export function briefingLinkLabel(link) {
  if (!link || !link.target) {
    return "Open";
  }

  return LINK_LABELS[link.target] || "Open";
}
