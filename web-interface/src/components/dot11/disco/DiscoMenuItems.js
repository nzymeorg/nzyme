import ApiRoutes from "../../../util/ApiRoutes";

export const DISCO_MENU_ITEMS = [
  {name: "Frames", href: ApiRoutes.DOT11.DISCO.INDEX},
  {name: "Anomalies", href: ApiRoutes.DOT11.DISCO.ANOMALIES.INDEX},
  {name: "Signal", href: ApiRoutes.DOT11.DISCO.SIGNAL},
  {name: "Settings", href: ApiRoutes.DOT11.DISCO.SETTINGS, with_permission: "dot11_monitoring_manage"},
]
