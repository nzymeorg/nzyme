import ApiRoutes from "../../../util/ApiRoutes";

export const ASSETS_MENU_ITEMS = [
  {name: "Asset List", href: ApiRoutes.ETHERNET.ASSETS.INDEX},
  {name: "DHCP", children: [
    {name: "Transactions", href: ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX},
    {name: "Exclusions", href: ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.INDEX},
  ]},
  {name: "ARP", href: ApiRoutes.ETHERNET.ASSETS.ARP.INDEX},
  {name: "Identity", children: [
    {name: "Duplicate MAC Addresses", href: ApiRoutes.ETHERNET.ASSETS.IDENTITY.DUPLICATES},
    {name: "Identity Changes", href: ApiRoutes.ETHERNET.ASSETS.IDENTITY.CHANGES},
    {name: "MAC Address Changes", href: ApiRoutes.ETHERNET.ASSETS.IDENTITY.LINKS},
    {name: "Exclusions", href: ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.INDEX},
  ]},
  {name: "Settings", href: ApiRoutes.ETHERNET.ASSETS.SETTINGS, with_permission: "ethernet_assets_manage"},
]

