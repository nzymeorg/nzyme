import ApiRoutes from "../../util/ApiRoutes";
import {Presets} from "../shared/timerange/TimeRange";
import {serializeTimeRange} from "../shared/timerange/TimeRangeUrl";
import {resolveBriefingLink} from "../briefing/briefingLinks";
import {filteredLink} from "../ethernet/assets/details/AssetProfileHelpers";

const NUMERIC_FIELDS = ["destination_port", "source_port", "server_port"];

// List pages that take the match as filters. Every parameter of the link is a filter field of the page.
const FILTERED_LISTS = {
  L4_SESSIONS: ApiRoutes.ETHERNET.L4.SESSIONS,
  DNS_LOG: ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS,
  TLS_SESSIONS: ApiRoutes.ETHERNET.TLS.SESSIONS.INDEX,
  DHCP: ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX,
  ARP_PACKETS: ApiRoutes.ETHERNET.ASSETS.ARP.INDEX,
  SSH_SESSIONS: ApiRoutes.ETHERNET.REMOTE.SSH.INDEX,
  VPN_TUNNELS: ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX,
  SOCKS_TUNNELS: ApiRoutes.ETHERNET.TUNNELS.SOCKS.INDEX,
  NTP_TRANSACTIONS: ApiRoutes.ETHERNET.TIME.NTP.INDEX
}

const filteredPath = (path, fields, timeRange, operator = null) => {
  const filters = {};
  Object.keys(fields).forEach((field) => {
    if (fields[field] === undefined || fields[field] === null) {
      return;
    }

    filters[field] = [{
      field: field,
      operator: operator ? operator : (NUMERIC_FIELDS.includes(field) ? "equals_numeric" : "equals"),
      value: fields[field]
    }];
  });

  return filteredLink(path, filters) + "&tr_timerange=" + encodeURIComponent(JSON.stringify(serializeTimeRange(timeRange ? timeRange : Presets.ALL_TIME)));
}

/*
 * The range covers what the search looked at: the range picked on the results page, or else all time for groups
 * that searched all of their data and the last day for the others.
 */
export function rangeOfGroup(group, timeRange) {
  if (timeRange) {
    return timeRange;
  }

  return group && group.searched_from ? Presets.RELATIVE_HOURS_24 : Presets.ALL_TIME;
}

export function resolveSearchLink(link, timeRange = null) {
  if (!link || !link.target) {
    return null;
  }

  const p = link.params || {};

  if (FILTERED_LISTS[link.target]) {
    return filteredPath(FILTERED_LISTS[link.target], p, timeRange);
  }

  const management = ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT;

  switch (link.target) {
    case "IP_ADDRESS": return ApiRoutes.ETHERNET.IP.ADDRESS_DETAILS(p.address);
    case "CONTEXT_MAC": return ApiRoutes.CONTEXT.MAC_ADDRESSES.SHOW(p.uuid);
    case "CONTEXT_NETWORK": return ApiRoutes.CONTEXT.NETWORKS.SHOW(p.uuid);
    case "DOT11_MONITORED_PROBE_REQUESTS": return ApiRoutes.DOT11.MONITORING.PROBE_REQUESTS.INDEX;
    case "DOT11_BANDIT": return ApiRoutes.DOT11.MONITORING.BANDITS.CUSTOM_DETAILS(p.uuid);
    case "DOT11_BSSIDS_OF_SSID":
      return filteredPath(ApiRoutes.DOT11.NETWORKS.BSSIDS.INDEX, {advertised_ssid: p.ssid}, timeRange);
    case "DOT11_CLIENTS_PROBING":
      return filteredPath(ApiRoutes.DOT11.CLIENTS.DISCONNECTED, {probe_request: p.ssid}, timeRange);
    case "UAV_TYPES": return ApiRoutes.UAV.TYPES.INDEX;
    case "MONITOR": return ApiRoutes.ALERTS.MONITORS.DETAILS(p.uuid);
    case "DASHBOARD": return ApiRoutes.DASHBOARDS.DETAILS(p.uuid);
    case "DASHBOARD_DISPLAY": return ApiRoutes.DASHBOARDS.DISPLAYS.DETAILS(p.uuid);
    case "TENANT": return management.TENANTS.DETAILS(p.organization, p.tenant);
    case "TENANT_USER": return management.USERS.DETAILS(p.organization, p.tenant, p.uuid);
    case "ORGANIZATION_ADMINISTRATOR": return management.ORGANIZATIONS.ADMINS.DETAILS(p.organization, p.uuid);
    case "SUPER_ADMINISTRATOR": return management.SUPERADMINS.DETAILS(p.uuid);
    case "EVENT_ACTION": return ApiRoutes.SYSTEM.EVENTS.ACTIONS.DETAILS(p.uuid);
    case "ORGANIZATION_EVENT_ACTION": return management.ORGANIZATIONS.EVENTS.ACTIONS.DETAILS(p.organization, p.uuid);
    case "NODE": return ApiRoutes.SYSTEM.CLUSTER.NODES.DETAILS(p.uuid);
    case "ASSET_DUPLICATE_MAC": return ApiRoutes.ETHERNET.ASSETS.IDENTITY.DUPLICATE_DETAILS(p.uuid);
    case "ASSET_IDENTITY_CHANGE": return ApiRoutes.ETHERNET.ASSETS.IDENTITY.CHANGE_DETAILS(p.uuid);
    case "ASSET_LINK": return ApiRoutes.ETHERNET.ASSETS.IDENTITY.LINK_DETAILS(p.uuid);
    case "ETHERNET_ASSETS_OF_TAG":
      return filteredPath(ApiRoutes.ETHERNET.ASSETS.INDEX, {tag: p.tag}, Presets.ALL_TIME, "asset_tag_equals");
    case "SEGMENT": return ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.DETAILS(p.uuid);
    case "SEGMENTATION_POLICY": return ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(p.uuid);
    case "SEGMENTATION_FINDING": return ApiRoutes.ETHERNET.SEGMENTATION.FINDINGS.DETAILS(p.uuid);
    case "BEACON_EXCLUSION": return ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.DETAILS(p.uuid);
    case "RECON_EXCLUSION": return ApiRoutes.ETHERNET.RECON.SCANS.EXCLUSIONS.DETAILS(p.uuid);
    case "SPOOFING_EXCLUSION": return ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.DETAILS(p.uuid);
    case "ICMP_EXCLUSION": return ApiRoutes.ETHERNET.ICMP.EXCLUSIONS.DETAILS(p.uuid);
    case "DNS_TUNNEL_EXCLUSION": return ApiRoutes.ETHERNET.DNS.TUNNELS.EXCLUSIONS.DETAILS(p.uuid);
    case "DHCP_FINGERPRINT_EXCLUSION": return ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.DETAILS(p.uuid);
    case "ASSET_IDENTITY_EXCLUSION": return ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.DETAILS(p.uuid);
    case "L4_SESSION":
      if (!p.key || !p.start) {
        return null;
      }

      return p.type === "UDP"
        ? ApiRoutes.ETHERNET.L4.UDP.SESSION_DETAILS(p.key, p.start)
        : ApiRoutes.ETHERNET.L4.TCP.SESSION_DETAILS(p.key, p.start);
    case "TLS_CERTIFICATE": return ApiRoutes.ETHERNET.TLS.CERTIFICATES.DETAILS(p.sha256);
    case "DHCP_TRANSACTION":
      if (!p.id || !p.time) {
        return null;
      }

      return ApiRoutes.ETHERNET.ASSETS.DHCP.TRANSACTION_DETAILS(p.id)
        + "?transaction_time=" + encodeURIComponent(p.time);
    case "RTSP_STREAM": return ApiRoutes.ETHERNET.STREAMS.RTSP.DETAILS(p.key);
    case "WEBRTC_SESSION": return ApiRoutes.ETHERNET.STREAMS.WEBRTC.DETAILS(p.key);
    case "STUN_CONNECTION": return ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.DETAILS(p.key);
    case "PORTAL_INTEGRITY_REPORT": return ApiRoutes.ETHERNET.PORTAL_INTEGRITY.REPORT_DETAILS(p.uuid);
    default: return resolveBriefingLink(link);
  }
}
