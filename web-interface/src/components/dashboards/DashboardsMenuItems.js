import ApiRoutes from "../../util/ApiRoutes";

export const DASHBOARDS_MENU_ITEMS = [
  {name: "Dashboards", href: ApiRoutes.DASHBOARDS.INDEX},
  {name: "Displays", href: ApiRoutes.DASHBOARDS.DISPLAYS.INDEX, with_permission: "dashboards_manage"}
]
