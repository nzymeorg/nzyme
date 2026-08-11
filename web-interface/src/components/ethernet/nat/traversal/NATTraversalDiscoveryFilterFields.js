import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const NAT_TRAVERSAL_DISCOVERY_FILTER_FIELDS = {
  session_key: { title: "Session Key", type: FILTER_TYPE.STRING },
  source_mac: { title: "Source MAC", type: FILTER_TYPE.MAC_ADDRESS },
  source_address: { title: "Source Address", type: FILTER_TYPE.IP_ADDRESS },
  destination_address: { title: "Destination Address", type: FILTER_TYPE.IP_ADDRESS },
  mapped_address: { title: "Mapped Address", type: FILTER_TYPE.STRING_ARRAY }
}