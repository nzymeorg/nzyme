export default function MonitorType({type}) {

  switch (type) {
    case "DOT11_BSSID":
      return "802.11/WiFi BSSID"
    case "DOT11_CLIENT_CONNECTED":
      return "802.11/WiFi Client (Connected)"
    case "DOT11_CLIENT_DISCONNECTED":
      return "802.11/WiFi Client (Disconnected)"
    case "BLUETOOTH_DEVICE":
      return "Bluetooth Device"
    default:
      return "Unknown"
  }

}