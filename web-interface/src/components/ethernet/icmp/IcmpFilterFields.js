import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const ICMP_FILTER_FIELDS = {
  source_address: { title: "Source Address", type: FILTER_TYPE.IP_ADDRESS },
  destination_address: { title: "Destination Address", type: FILTER_TYPE.IP_ADDRESS },
  source_mac: { title: "Source MAC", type: FILTER_TYPE.MAC_ADDRESS },
  destination_mac: { title: "Destination MAC", type: FILTER_TYPE.MAC_ADDRESS },
  icmp_type: { title: "ICMP Type", type: FILTER_TYPE.ICMP_TYPE },
  icmp_code: { title: "ICMP Code", type: FILTER_TYPE.NUMERIC },
  packet_count: { title: "Packet Count", type: FILTER_TYPE.NUMERIC },
  redirect_gateway: { title: "Redirect Gateway", type: FILTER_TYPE.IP_ADDRESS },
  quoted_destination_address: { title: "Quoted Destination Address", type: FILTER_TYPE.IP_ADDRESS }
}
