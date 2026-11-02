import ApiRoutes from "../../../util/ApiRoutes";

export const ASSETS_MENU_ITEMS = [
  {name: "Asset List", href: ApiRoutes.ETHERNET.ASSETS.INDEX},
  {name: "DHCP", href: ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX},
  {name: "DHCP Exclusions", href: ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.INDEX},
  {name: "ARP", href: ApiRoutes.ETHERNET.ASSETS.ARP.INDEX},
  {name: "Settings", href: ApiRoutes.ETHERNET.ASSETS.SETTINGS, with_permission: "ethernet_assets_manage"},
]