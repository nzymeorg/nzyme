import ApiRoutes from "../../../util/ApiRoutes";

export const RECON_MENU_ITEMS = [
  {name: "Scans", href: ApiRoutes.ETHERNET.RECON.SCANS.INDEX},
  {name: "Fingerprinting", href: ApiRoutes.ETHERNET.RECON.FINGERPRINTING.INDEX},
  {name: "Probed Hosts", href: ApiRoutes.ETHERNET.RECON.HOSTS.INDEX},
  {name: "Exclusions", href: ApiRoutes.ETHERNET.RECON.SCANS.EXCLUSIONS.INDEX},
  {name: "Settings", href: ApiRoutes.ETHERNET.RECON.SCANS.SETTINGS, with_permission: "ethernet_monitoring_manage"},
]
