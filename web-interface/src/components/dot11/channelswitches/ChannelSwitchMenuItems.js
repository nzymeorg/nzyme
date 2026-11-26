import ApiRoutes from "../../../util/ApiRoutes";

export const CHANNEL_SWITCH_MENU_ITEMS = [
  {name: "Announcements", href: ApiRoutes.DOT11.CHANNEL_SWITCHES.INDEX},
  {name: "Signal", href: ApiRoutes.DOT11.CHANNEL_SWITCHES.SIGNAL},
  {name: "Settings", href: ApiRoutes.DOT11.CHANNEL_SWITCHES.SETTINGS, with_permission: "dot11_monitoring_manage"},
]
