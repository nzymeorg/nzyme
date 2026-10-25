import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const ICMP_REDIRECTS_FILTER_FIELDS = {
  uuid: { title: "ID", type: FILTER_TYPE.UUID },
  sender_address: { title: "Sender Address", type: FILTER_TYPE.IP_ADDRESS },
  sender_mac: { title: "Sender MAC", type: FILTER_TYPE.MAC_ADDRESS },
  gateway_address: { title: "Advertised Gateway", type: FILTER_TYPE.IP_ADDRESS },
  target_address: { title: "Told Host", type: FILTER_TYPE.IP_ADDRESS },
  target_mac: { title: "Told Host MAC", type: FILTER_TYPE.MAC_ADDRESS },
  packet_count: { title: "Packet Count", type: FILTER_TYPE.NUMERIC }
}
