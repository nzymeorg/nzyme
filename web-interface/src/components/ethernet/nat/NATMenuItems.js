import ApiRoutes from "../../../util/ApiRoutes";

export const NAT_MENU_ITEMS = [
  {name: "STUN Connections", href: ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.INDEX},
  {name: "TURN Connections", href: ApiRoutes.ETHERNET.NAT.TRAVERSAL.TURN_CONNECTIONS.INDEX},
  {name: "STUN Discoveries", href: ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_DISCOVERY.INDEX}
]