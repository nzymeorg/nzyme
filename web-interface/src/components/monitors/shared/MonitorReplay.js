import ApiRoutes from "../../../util/ApiRoutes";

export default function monitorTypeToSearchLink(monitor) {
  return monitorTypeToSearchPage(monitor.type) + "?monitor=" + monitor.uuid;
}

export function monitorTypeToSearchPage(type) {
  switch (type) {
    case "DOT11_BSSID":
      return ApiRoutes.DOT11.NETWORKS.BSSIDS.INDEX;
    case "DOT11_CLIENT_CONNECTED":
      return ApiRoutes.DOT11.CLIENTS.CONNECTED;
    case "DOT11_CLIENT_DISCONNECTED":
      return ApiRoutes.DOT11.CLIENTS.DISCONNECTED;
    case "BLUETOOTH_DEVICE":
      return ApiRoutes.BLUETOOTH.DEVICES.INDEX;
    case "ETHERNET_ASSET":
      return ApiRoutes.ETHERNET.ASSETS.INDEX;
    case "ETHERNET_L4_SESSION":
      return ApiRoutes.ETHERNET.L4.SESSIONS;
    case "ETHERNET_DNS_TRANSACTION":
      return ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS;
    case "ETHERNET_DHCP_TRANSACTION":
      return ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX;
    case "ETHERNET_ARP_PACKET":
      return ApiRoutes.ETHERNET.ASSETS.ARP.INDEX;
    case "ETHERNET_SSH_SESSION":
      return ApiRoutes.ETHERNET.REMOTE.SSH.INDEX;
    case "ETHERNET_SOCKS_TUNNEL":
      return ApiRoutes.ETHERNET.TUNNELS.SOCKS.INDEX;
    case "ETHERNET_VPN_TUNNEL":
      return ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX;
    case "ETHERNET_TLS_SESSION":
      return ApiRoutes.ETHERNET.TLS.SESSIONS.INDEX;
    case "ETHERNET_TLS_CERTIFICATE":
      return ApiRoutes.ETHERNET.TLS.CERTIFICATES.INDEX;
    case "ETHERNET_RTSP_STREAM":
      return ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX;
    case "ETHERNET_WEBRTC_SESSION":
      return ApiRoutes.ETHERNET.STREAMS.WEBRTC.INDEX;
    case "ETHERNET_NTP_TRANSACTION":
      return ApiRoutes.ETHERNET.TIME.NTP.INDEX;
    case "ETHERNET_STUN_DISCOVERY":
      return ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_DISCOVERY.INDEX;
    case "ETHERNET_STUN_CONNECTION":
      return ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.INDEX;
    case "ETHERNET_PORTAL_INTEGRITY_REPORT":
      return ApiRoutes.ETHERNET.PORTAL_INTEGRITY.INDEX;
  }
}
