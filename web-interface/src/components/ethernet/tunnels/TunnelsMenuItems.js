import ApiRoutes from "../../../util/ApiRoutes";

export const TUNNELS_MENU_ITEMS = [
  {name: "SOCKS", href: ApiRoutes.ETHERNET.TUNNELS.SOCKS.INDEX},
  {name: "VPN", href: ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX},
  {name: "Settings", href: ApiRoutes.ETHERNET.TUNNELS.VPN.SETTINGS, with_permission: "ethernet_monitoring_manage"}
]
