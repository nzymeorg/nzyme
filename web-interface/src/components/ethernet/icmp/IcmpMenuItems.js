import ApiRoutes from "../../../util/ApiRoutes";

export const ICMP_MENU_ITEMS = [
  {name: "Traffic", href: ApiRoutes.ETHERNET.ICMP.OVERVIEW},
  {name: "Redirects", href: ApiRoutes.ETHERNET.ICMP.REDIRECTS.INDEX},
  {name: "Traceroutes", href: ApiRoutes.ETHERNET.ICMP.TRACEROUTES.INDEX},
  {name: "Unreachable Ports", href: ApiRoutes.ETHERNET.ICMP.UNREACHABLES},
  {name: "Unreachable Hosts", href: ApiRoutes.ETHERNET.ICMP.UNREACHABLE_HOSTS},
  {name: "Tunnels", href: ApiRoutes.ETHERNET.ICMP.TUNNELS.INDEX},
  {name: "Exclusions", href: ApiRoutes.ETHERNET.ICMP.EXCLUSIONS.INDEX},
  {name: "Settings", href: ApiRoutes.ETHERNET.ICMP.SETTINGS, with_permission: "ethernet_monitoring_manage"},
]
