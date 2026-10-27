import ApiRoutes from "../../../util/ApiRoutes";

export const SPOOFING_MENU_ITEMS = [
  {name: "ARP", href: ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX},
  {name: "DHCP", href: ApiRoutes.ETHERNET.SPOOFING.DHCP.INDEX},
  {name: "Name Resolution", href: ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.INDEX},
  {name: "Exclusions", href: ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.INDEX},
  {name: "Settings", href: ApiRoutes.ETHERNET.SPOOFING.SETTINGS, with_permission: "ethernet_monitoring_manage"},
]
