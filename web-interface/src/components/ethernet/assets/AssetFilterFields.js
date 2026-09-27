import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const ASSET_FILTER_FIELDS = {
  mac: { title: "MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  hostname: { title: "Hostname", type: FILTER_TYPE.STRING },
  ip_address: { title: "IP Address", type: FILTER_TYPE.IP_ADDRESS },
  is_active: { title: "Is Active", type: FILTER_TYPE.BOOLEAN }
}