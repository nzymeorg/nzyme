import ApiRoutes from "../../util/ApiRoutes";
import {userHasPermission, userHasSubsystem} from "../../util/Tools";
import {ASSETS_MENU_ITEMS} from "../ethernet/assets/AssetsMenuItems";
import {BEACONS_MENU_ITEMS} from "../ethernet/beacons/BeaconsMenuItems";
import {DNS_MENU_ITEMS} from "../ethernet/dns/DNSMenuItems";
import {ICMP_MENU_ITEMS} from "../ethernet/icmp/IcmpMenuItems";
import {L4_MENU_ITEMS} from "../ethernet/l4/L4MenuItems";
import {ETHERNET_MONITORING_MENU_ITEMS} from "../ethernet/monitoring/EthernetMonitoringMenuItems";
import {NAT_MENU_ITEMS} from "../ethernet/nat/NATMenuItems";
import {RECON_MENU_ITEMS} from "../ethernet/recon/ReconMenuItems";
import {REMOTE_ACCESS_MENU_ITEMS} from "../ethernet/remote/RemoteAccessMenuItems";
import {SEGMENTATION_MENU_ITEMS} from "../ethernet/segmentation/SegmentationMenuItems";
import {SPOOFING_MENU_ITEMS} from "../ethernet/spoofing/SpoofingMenuItems";
import {STREAMS_MENU_ITEMS} from "../ethernet/streams/StreamsMenuItems";
import {TIME_MENU_ITEMS} from "../ethernet/time/TimeMenuItems";
import {TLS_MENU_ITEMS} from "../ethernet/tls/TLSMenuItems";
import {TUNNELS_MENU_ITEMS} from "../ethernet/tunnels/TunnelsMenuItems";
import {CLIENTS_MENU_ITEMS} from "../dot11/clients/ClientsMenuItems";
import {MONITORING_MENU_ITEMS} from "../dot11/monitoring/Dot11MonitoringMenuItems";
import {BLUETOOTH_MONITORING_MENU_ITEMS} from "../bluetooth/monitoring/BluetoothMonitoringMenuItems";
import {DASHBOARDS_MENU_ITEMS} from "../dashboards/DashboardsMenuItems";
import {DATABASE_MENU_ITEMS} from "../system/database/DatabaseMenuItems";
import {MINIMUM_QUERY_LENGTH} from "./searchQuery";

const anyone = () => true;
const administrator = (user) => user.is_orgadmin || user.is_superadmin;
const superAdministrator = (user) => user.is_superadmin;
const subsystem = (name, permission = null) => (user) =>
  userHasSubsystem(user, name) && (!permission || userHasPermission(user, permission));
const dashboards = (user) => user.dashboards_enabled
  && (userHasPermission(user, "dashboards_view") || userHasPermission(user, "dashboards_manage"));

/*
 * The pages of the web interface, with who can open them. Sections that have a menu bar take their pages from
 * its definition, so a page added to a menu bar can be found without being listed here.
 */
const SECTIONS = [
  {path: [], show: anyone, items: [
    {name: "Briefing", href: ApiRoutes.DASHBOARD, keywords: "overview home start"},
    {name: "Locations", href: ApiRoutes.LOCATIONS.INDEX, keywords: "floors map"},
    {name: "User Profile", href: ApiRoutes.USERPROFILE.PROFILE, keywords: "account mfa api keys"},
    {name: "Change Password", href: ApiRoutes.USERPROFILE.PASSWORD, keywords: "account"}
  ]},
  {path: ["Alerts"], show: (user) => userHasPermission(user, "alerts_view") || userHasPermission(user, "alerts_manage"), items: [
    {name: "Alerts", href: ApiRoutes.ALERTS.INDEX, keywords: "detections"}
  ]},
  {path: ["Alerts"], show: administrator, items: [
    {name: "Subscriptions", href: ApiRoutes.ALERTS.SUBSCRIPTIONS.INDEX, keywords: "notifications actions"}
  ]},
  {path: ["Context"], show: anyone, items: [
    {name: "MAC Addresses", href: ApiRoutes.CONTEXT.MAC_ADDRESSES.INDEX, keywords: "names"},
    {name: "Networks", href: ApiRoutes.CONTEXT.NETWORKS.INDEX, keywords: "cidr internal"}
  ]},
  {path: [], show: dashboards, items: DASHBOARDS_MENU_ITEMS},

  {path: ["Ethernet"], show: subsystem("ethernet"), items: [
    {name: "Briefing", href: ApiRoutes.ETHERNET.OVERVIEW, keywords: "overview"},
    {name: "Topology", href: ApiRoutes.ETHERNET.TOPOLOGY.INDEX, keywords: "graph map connections"},
    {name: "Portal Integrity", href: ApiRoutes.ETHERNET.PORTAL_INTEGRITY.INDEX, keywords: "captive portal"}
  ]},
  {path: ["Ethernet", "Segmentation"], show: subsystem("ethernet"), items: SEGMENTATION_MENU_ITEMS},
  {path: ["Ethernet", "Assets"], show: subsystem("ethernet"), items: ASSETS_MENU_ITEMS, keywords: "devices hosts"},
  {path: ["Ethernet", "Beacons"], show: subsystem("ethernet"), items: BEACONS_MENU_ITEMS, keywords: "c2 command control"},
  {path: ["Ethernet", "Spoofing"], show: subsystem("ethernet"), items: SPOOFING_MENU_ITEMS, keywords: "poisoning rogue"},
  {path: ["Ethernet", "Recon"], show: subsystem("ethernet"), items: RECON_MENU_ITEMS, keywords: "reconnaissance port scan sweep"},
  {path: ["Ethernet", "TCP/UDP"], show: subsystem("ethernet"), items: L4_MENU_ITEMS, keywords: "connections l4"},
  {path: ["Ethernet", "ICMP"], show: subsystem("ethernet"), items: ICMP_MENU_ITEMS, keywords: "ping"},
  {path: ["Ethernet", "DNS"], show: subsystem("ethernet"), items: DNS_MENU_ITEMS, keywords: "names resolution"},
  {path: ["Ethernet", "Remote Access"], show: subsystem("ethernet"), items: REMOTE_ACCESS_MENU_ITEMS},
  {path: ["Ethernet", "TLS"], show: subsystem("ethernet"), items: TLS_MENU_ITEMS, keywords: "ssl https"},
  {path: ["Ethernet", "Tunnels"], show: subsystem("ethernet"), items: TUNNELS_MENU_ITEMS, keywords: "wireguard openvpn tailscale proxy"},
  {path: ["Ethernet", "NAT"], show: subsystem("ethernet"), items: NAT_MENU_ITEMS},
  {path: ["Ethernet", "Streams"], show: subsystem("ethernet"), items: STREAMS_MENU_ITEMS, keywords: "video camera"},
  {path: ["Ethernet", "Time"], show: subsystem("ethernet"), items: TIME_MENU_ITEMS, keywords: "clock"},
  {path: ["Ethernet", "Monitoring"], show: subsystem("ethernet", "ethernet_monitoring_manage"), items: ETHERNET_MONITORING_MENU_ITEMS},

  {path: ["WiFi"], show: subsystem("dot11"), items: [
    {name: "Briefing", href: ApiRoutes.DOT11.OVERVIEW, keywords: "overview 802.11"},
    {name: "Access Points", href: ApiRoutes.DOT11.NETWORKS.BSSIDS.INDEX, keywords: "bssid ssid networks 802.11"},
    {name: "Disconnections", href: ApiRoutes.DOT11.DISCO.INDEX, keywords: "deauthentication disassociation deauth 802.11"}
  ]},
  {path: ["WiFi", "Clients"], show: subsystem("dot11"), items: CLIENTS_MENU_ITEMS, keywords: "stations 802.11"},
  {path: ["WiFi", "Monitoring"], show: subsystem("dot11", "dot11_monitoring_manage"), items: MONITORING_MENU_ITEMS, keywords: "802.11"},

  {path: ["Bluetooth"], show: subsystem("bluetooth"), items: [
    {name: "Briefing", href: ApiRoutes.BLUETOOTH.OVERVIEW, keywords: "overview"},
    {name: "Devices", href: ApiRoutes.BLUETOOTH.DEVICES.INDEX}
  ]},
  {path: ["Bluetooth", "Monitoring"], show: subsystem("bluetooth", "bluetooth_monitoring_manage"), items: BLUETOOTH_MONITORING_MENU_ITEMS},

  {path: ["UAV"], show: subsystem("uav"), items: [
    {name: "UAVs", href: ApiRoutes.UAV.INDEX, keywords: "drones"},
    {name: "Types", href: ApiRoutes.UAV.TYPES.INDEX, keywords: "drones models"}
  ]},
  {path: ["UAV"], show: subsystem("uav", "uav_monitoring_manage"), items: [
    {name: "Monitoring", href: ApiRoutes.UAV.MONITORING.INDEX, keywords: "drones"}
  ]},

  {path: ["System"], show: administrator, items: [
    {name: "Taps", href: ApiRoutes.SYSTEM.TAPS.INDEX, keywords: "sensors"}
  ]},
  {path: ["System"], show: (user) => user.is_orgadmin && !user.is_superadmin, items: (user) => [
    {name: "Organization", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX, keywords: "tenants users administrators"},
    {name: "Events & Actions", keywords: "notifications email webhook",
      href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS_PAGE(user.organization_id)}
  ]},
  {path: ["System"], show: superAdministrator, items: [
    {name: "Authentication", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX, keywords: "organizations tenants users administrators sessions"},
    {name: "Authentication Settings", href: ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.SETTINGS, keywords: "sessions timeout mfa"},
    {name: "Cluster & Nodes", href: ApiRoutes.SYSTEM.CLUSTER.INDEX},
    {name: "Cluster Messaging", href: ApiRoutes.SYSTEM.CLUSTER.MESSAGING.INDEX, keywords: "message bus tasks"},
    {name: "Nzyme Connect", href: ApiRoutes.SYSTEM.CONNECT},
    {name: "Events & Actions", href: ApiRoutes.SYSTEM.EVENTS.INDEX, keywords: "notifications email webhook"},
    {name: "Integrations", href: ApiRoutes.SYSTEM.INTEGRATIONS.INDEX, keywords: "geoip smtp"},
    {name: "Health Console", href: ApiRoutes.SYSTEM.HEALTH.INDEX, keywords: "indicators"},
    {name: "Monitoring & Metrics", href: ApiRoutes.SYSTEM.MONITORING.INDEX},
    {name: "Prometheus", href: ApiRoutes.SYSTEM.MONITORING.PROMETHEUS.INDEX, keywords: "metrics exporter"},
    {name: "Keys & Certificates", href: ApiRoutes.SYSTEM.CRYPTO.INDEX, keywords: "tls pgp crypto"},
    {name: "Look & Feel", href: ApiRoutes.SYSTEM.LOOKANDFEEL, keywords: "branding logo"},
    {name: "Subsystems", href: ApiRoutes.SYSTEM.SUBSYSTEMS, keywords: "enable disable"},
    {name: "Version", href: ApiRoutes.SYSTEM.VERSION, keywords: "update release"}
  ]},
  {path: ["System", "Database"], show: superAdministrator, items: DATABASE_MENU_ITEMS, keywords: "postgresql retention storage"}
]

const flatten = (section, items, path, pages, user) => {
  items.forEach((item) => {
    if (item.with_permission && !userHasPermission(user, item.with_permission)) {
      return;
    }

    if (item.administrators_only && !administrator(user)) {
      return;
    }

    if (item.children) {
      flatten(section, item.children, path.concat([item.name]), pages, user);
      return;
    }

    pages.push({
      title: item.name,
      path: path,
      href: item.href,
      text: [item.name].concat(path).concat([section.keywords || "", item.keywords || ""]).join(" ").toLowerCase()
    });
  });
}

export function pagesOf(user) {
  const pages = [];

  SECTIONS.forEach((section) => {
    if (section.show(user)) {
      const items = typeof section.items === "function" ? section.items(user) : section.items;
      flatten(section, items, section.path, pages, user);
    }
  });

  return pages;
}

export function findPages(pages, query) {
  const words = query.toLowerCase().split(/\s+/).filter((word) => word.length > 0);
  if (words.length === 0 || query.trim().length < MINIMUM_QUERY_LENGTH) {
    return [];
  }

  const full = words.join(" ");

  return pages
    .filter((page) => words.every((word) => page.text.includes(word)))
    .map((page) => {
      const title = page.title.toLowerCase();
      const named = page.path.concat([page.title]).join(" ").toLowerCase();

      let rank = 3;
      if (title === full || named.endsWith(full)) {
        rank = 0;
      } else if (title.startsWith(full)) {
        rank = 1;
      } else if (words.every((word) => named.includes(word))) {
        rank = 2;
      }

      return {page: page, rank: rank};
    })
    .sort((a, b) => a.rank - b.rank || a.page.path.length - b.page.path.length)
    .map((match) => match.page);
}
