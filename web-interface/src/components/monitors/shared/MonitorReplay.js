import ApiRoutes from "../../../util/ApiRoutes";

export default function monitorTypeToSearchLink(monitor) {
  switch (monitor.type) {
    case "DOT11_BSSID":
      return ApiRoutes.DOT11.NETWORKS.BSSIDS.INDEX + "?monitor=" + monitor.uuid;
    case "DOT11_CLIENT_CONNECTED":
      return ApiRoutes.DOT11.CLIENTS.CONNECTED + "?monitor=" + monitor.uuid;
    case "DOT11_CLIENT_DISCONNECTED":
      return ApiRoutes.DOT11.CLIENTS.DISCONNECTED + "?monitor=" + monitor.uuid;
    case "BLUETOOTH_DEVICE":
      return ApiRoutes.BLUETOOTH.DEVICES.INDEX + "?monitor=" + monitor.uuid;
  }
}