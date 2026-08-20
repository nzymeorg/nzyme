import {FILTER_TYPE} from "../../../../shared/filtering/Filters";

export const STUN_CONNECTIONS_FILTER_FIELDS = {
  successful: { title: "Successful", type: FILTER_TYPE.BOOLEAN },
  active: { title: "Active", type: FILTER_TYPE.BOOLEAN },
  negotiation_key_sha256: { title: "ID", type: FILTER_TYPE.STRING },
  source_mac: { title: "Source MAC", type: FILTER_TYPE.MAC_ADDRESS },
  source_address: { title: "Source Address", type: FILTER_TYPE.IP_ADDRESS },
  destination_mac: { title: "Destination MAC", type: FILTER_TYPE.MAC_ADDRESS },
  destination_address: { title: "Destination Address", type: FILTER_TYPE.IP_ADDRESS },
  is_turn: { title: "TURN", type: FILTER_TYPE.BOOLEAN },
  bytes_exchanged: { title: "Bytes", type: FILTER_TYPE.NUMERIC },
}