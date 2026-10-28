export default function MonitorType({type}) {
  return monitorTypeToName(type);
}

export function monitorTypeToName(type) {
  switch (type) {
    case "DOT11_BSSID":
      return "802.11/WiFi BSSID"
    case "DOT11_CLIENT_CONNECTED":
      return "802.11/WiFi Client (Connected)"
    case "DOT11_CLIENT_DISCONNECTED":
      return "802.11/WiFi Client (Disconnected)"
    case "BLUETOOTH_DEVICE":
      return "Bluetooth Device"
    case "ETHERNET_ASSET":
      return "Ethernet Asset"
    case "ETHERNET_L4_SESSION":
      return "Ethernet TCP/UDP Session"
    case "ETHERNET_DNS_TRANSACTION":
      return "Ethernet DNS Transaction"
    case "ETHERNET_DHCP_TRANSACTION":
      return "Ethernet DHCP Transaction"
    case "ETHERNET_ARP_PACKET":
      return "Ethernet ARP Packet"
    case "ETHERNET_SSH_SESSION":
      return "Ethernet SSH Session"
    case "ETHERNET_SOCKS_TUNNEL":
      return "Ethernet SOCKS Tunnel"
    case "ETHERNET_VPN_TUNNEL":
      return "Ethernet VPN Tunnel"
    case "ETHERNET_TLS_SESSION":
      return "Ethernet TLS Session"
    case "ETHERNET_TLS_CERTIFICATE":
      return "Ethernet TLS Certificate"
    case "ETHERNET_RTSP_STREAM":
      return "Ethernet RTSP Stream"
    case "ETHERNET_WEBRTC_SESSION":
      return "Ethernet WebRTC Session"
    case "ETHERNET_NTP_TRANSACTION":
      return "Ethernet NTP Transaction"
    case "ETHERNET_STUN_DISCOVERY":
      return "Ethernet STUN Discovery"
    case "ETHERNET_STUN_CONNECTION":
      return "Ethernet STUN Connection"
    case "ETHERNET_PORTAL_INTEGRITY_REPORT":
      return "Ethernet Portal Integrity Report"
    default:
      return "Unknown"
  }
}
