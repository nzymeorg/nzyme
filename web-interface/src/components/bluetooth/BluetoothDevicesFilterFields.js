import {FILTER_TYPE} from "../shared/filtering/Filters";

export const BLUETOOTH_DEVICES_FILTER_FIELDS = {
  mac: { title: "MAC Address", type: FILTER_TYPE.STRING },
  ouis: { title: "OUI", type: FILTER_TYPE.STRING },
  manufacturer_names: { title: "Company", type: FILTER_TYPE.STRING },
  tags: { title: "Type", type: FILTER_TYPE.STRING_ARRAY },
  transports: { title: "Transport", type: FILTER_TYPE.STRING },
  names: { title: "Name", type: FILTER_TYPE.STRING },
}