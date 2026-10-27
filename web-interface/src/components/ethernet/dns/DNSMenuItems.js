import ApiRoutes from "../../../util/ApiRoutes";

export const DNS_MENU_ITEMS = [
    {name: "DNS Overview", href: ApiRoutes.ETHERNET.DNS.INDEX},
    {name: "Transaction Logs", href: ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS},
    {name: "Tunnels", href: ApiRoutes.ETHERNET.DNS.TUNNELS.INDEX},
    {name: "Exclusions", href: ApiRoutes.ETHERNET.DNS.TUNNELS.EXCLUSIONS.INDEX},
    {name: "Settings", href: ApiRoutes.ETHERNET.DNS.TUNNELS.SETTINGS, with_permission: "ethernet_monitoring_manage"}
]
