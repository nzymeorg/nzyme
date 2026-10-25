import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const ICMP_TUNNELS_FILTER_FIELDS = {
  uuid: { title: "ID", type: FILTER_TYPE.UUID },
  source_address: { title: "Source", type: FILTER_TYPE.IP_ADDRESS },
  source_mac: { title: "Source MAC", type: FILTER_TYPE.MAC_ADDRESS },
  destination_address: { title: "Peer", type: FILTER_TYPE.IP_ADDRESS },
  max_payload_bytes: { title: "Max Payload (Bytes)", type: FILTER_TYPE.NUMERIC },
  packet_count: { title: "Packet Count", type: FILTER_TYPE.NUMERIC }
}
