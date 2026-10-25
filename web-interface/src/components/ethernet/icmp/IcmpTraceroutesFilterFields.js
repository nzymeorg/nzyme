import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const ICMP_TRACEROUTES_FILTER_FIELDS = {
  uuid: { title: "ID", type: FILTER_TYPE.UUID },
  source_address: { title: "Tracing Host Address", type: FILTER_TYPE.IP_ADDRESS },
  source_mac: { title: "Tracing Host MAC", type: FILTER_TYPE.MAC_ADDRESS },
  destination_address: { title: "Destination Address", type: FILTER_TYPE.IP_ADDRESS },
  protocol: { title: "Probe Protocol", type: FILTER_TYPE.TRACEROUTE_PROTOCOL },
  destination_reached: { title: "Destination Reached", type: FILTER_TYPE.BOOLEAN },
  router_count: { title: "Routers Answering", type: FILTER_TYPE.NUMERIC },
  packet_count: { title: "Packet Count", type: FILTER_TYPE.NUMERIC }
}
