import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const DHCP_SPOOFING_FILTER_FIELDS = {
  type: { title: "Type", type: FILTER_TYPE.DHCP_SPOOFING_FINDING_TYPE },
  server_mac: { title: "Server MAC", type: FILTER_TYPE.MAC_ADDRESS },
  server_address: { title: "Server Address", type: FILTER_TYPE.IP_ADDRESS },
  response_count: { title: "Responses", type: FILTER_TYPE.NUMERIC },
  transaction_count: { title: "Transactions", type: FILTER_TYPE.NUMERIC },
  victim_count: { title: "Victims", type: FILTER_TYPE.NUMERIC }
}
