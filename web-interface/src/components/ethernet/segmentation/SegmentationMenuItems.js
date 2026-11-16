import ApiRoutes from "../../../util/ApiRoutes";

export const SEGMENTATION_MENU_ITEMS = [
  {name: "Overview", href: ApiRoutes.ETHERNET.SEGMENTATION.INDEX},
  {name: "Findings", href: ApiRoutes.ETHERNET.SEGMENTATION.FINDINGS.INDEX},
  {name: "Policies", href: ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.INDEX},
  {name: "Segments", href: ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX},
  {name: "Settings", href: ApiRoutes.ETHERNET.SEGMENTATION.SETTINGS, with_permission: "ethernet_monitoring_manage"}
]
