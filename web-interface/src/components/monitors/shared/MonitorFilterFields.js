import {BSSID_FILTER_FIELDS} from "../../dot11/bssids/BssidFilterFields";
import {CONNECTED_CLIENT_FILTER_FIELDS} from "../../dot11/clients/ConnectedClientFilterFields";
import {DISCONNECTED_CLIENT_FILTER_FIELDS} from "../../dot11/clients/DisconnectedClientFilterFields";
import {BLUETOOTH_DEVICES_FILTER_FIELDS} from "../../bluetooth/BluetoothDevicesFilterFields";

export default function monitorTypeToFilterFields(type) {
  switch (type) {
    case "DOT11_BSSID":
      return BSSID_FILTER_FIELDS
    case "DOT11_CLIENT_CONNECTED":
      return CONNECTED_CLIENT_FILTER_FIELDS
    case "DOT11_CLIENT_DISCONNECTED":
      return DISCONNECTED_CLIENT_FILTER_FIELDS
    case "BLUETOOTH_DEVICE":
      return BLUETOOTH_DEVICES_FILTER_FIELDS
  }
}