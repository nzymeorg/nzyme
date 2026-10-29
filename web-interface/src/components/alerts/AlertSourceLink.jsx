import React from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../util/ApiRoutes";

export default function AlertSourceLink({alert}) {

  const a = alert.attributes ? alert.attributes : {};

  const link = (to, title) => <Link to={to} title={"Open " + title.toLowerCase() + " this alert was raised from."}>{title}</Link>

  switch (alert.detection_type) {
    case "MONITOR_TRIGGERED_ETHERNET":
    case "MONITOR_TRIGGERED_DOT11":
    case "MONITOR_TRIGGERED_BLUETOOTH":
      return a.monitor_uuid ? link(ApiRoutes.ALERTS.MONITORS.DETAILS(a.monitor_uuid), "Monitor") : null;
    case "ASSETS_NEW":
    case "ASSETS_DHCP_FINGERPRINT_NEW":
      return a.asset_uuid ? link(ApiRoutes.ETHERNET.ASSETS.DETAILS(a.asset_uuid), "Asset") : null;
    case "BEACON_NEW":
    case "BEACON_NEW_SOURCE":
    case "BEACON_ACTIVITY_SUSPICIOUS":
    case "BEACON_ACTIVITY_MALICIOUS":
      return a.beacon_uuid ? link(ApiRoutes.ETHERNET.BEACONS.DETAILS(a.beacon_uuid), "Beacon") : null;
    case "RECON_SCAN_NEW":
      return a.scan_uuid ? link(ApiRoutes.ETHERNET.RECON.SCANS.DETAILS(a.scan_uuid), "Scan") : null;
    case "RECON_FINGERPRINTING_NEW":
      return a.scan_uuid ? link(ApiRoutes.ETHERNET.RECON.FINGERPRINTING.DETAILS(a.scan_uuid), "Fingerprinting") : null;
    case "SPOOFING_ARP_NEW":
      return a.finding_uuid ? link(ApiRoutes.ETHERNET.SPOOFING.ARP.DETAILS(a.finding_uuid), "Finding") : null;
    case "SPOOFING_DHCP_ROGUE_SERVER":
    case "SPOOFING_DHCP_PARAMETERS_CHANGED":
      return a.finding_uuid ? link(ApiRoutes.ETHERNET.SPOOFING.DHCP.DETAILS(a.finding_uuid), "Finding") : null;
    case "SPOOFING_NAME_RESOLUTION_POISONING":
      return a.finding_uuid ? link(ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.DETAILS(a.finding_uuid), "Finding") : null;
    case "SPOOFING_DNS_NEW":
      return a.finding_uuid ? link(ApiRoutes.ETHERNET.SPOOFING.DNS.DETAILS(a.finding_uuid), "Finding") : null;
    case "DNS_TUNNEL":
      return a.finding_uuid ? link(ApiRoutes.ETHERNET.DNS.TUNNELS.DETAILS(a.finding_uuid), "Finding") : null;
    case "ICMP_TUNNEL":
      return a.tunnel_uuid ? link(ApiRoutes.ETHERNET.ICMP.TUNNELS.DETAILS(a.tunnel_uuid), "Tunnel")
          : link(ApiRoutes.ETHERNET.ICMP.TUNNELS.INDEX, "Tunnels");
    case "ICMP_REDIRECT":
      return a.redirect_uuid ? link(ApiRoutes.ETHERNET.ICMP.REDIRECTS.DETAILS(a.redirect_uuid), "Redirect")
          : link(ApiRoutes.ETHERNET.ICMP.REDIRECTS.INDEX, "Redirects");
    case "ICMP_TRACEROUTE":
      return a.traceroute_uuid ? link(ApiRoutes.ETHERNET.ICMP.TRACEROUTES.DETAILS(a.traceroute_uuid), "Traceroute")
          : link(ApiRoutes.ETHERNET.ICMP.TRACEROUTES.INDEX, "Traceroutes");
    case "UAV_DETECTED_CLASSIFICATION_UNKNOWN":
    case "UAV_DETECTED_CLASSIFICATION_FRIENDLY":
    case "UAV_DETECTED_CLASSIFICATION_NEUTRAL":
    case "UAV_DETECTED_CLASSIFICATION_HOSTILE":
      return a.identifier ? link(ApiRoutes.UAV.DETAILS(a.identifier), "UAV") : null;
    case "ENVIRONMENTAL_SEVERE_ALERT":
      return a.location_uuid ? link(ApiRoutes.LOCATIONS.DETAILS(a.location_uuid), "Location") : null;
    default:
      // WiFi alerts are raised for a monitored network.
      return alert.dot11_monitored_network_id
          ? link(ApiRoutes.DOT11.MONITORING.SSID_DETAILS(alert.dot11_monitored_network_id), "Network") : null;
  }

}
