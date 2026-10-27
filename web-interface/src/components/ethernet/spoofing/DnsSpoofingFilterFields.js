import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const DNS_SPOOFING_FILTER_FIELDS = {
  server_address: { title: "Resolver Address", type: FILTER_TYPE.IP_ADDRESS },
  spoofer_mac: { title: "Spoofer MAC", type: FILTER_TYPE.MAC_ADDRESS },
  owner_mac: { title: "Owner MAC", type: FILTER_TYPE.MAC_ADDRESS },
  forged_count: { title: "Forged Replies", type: FILTER_TYPE.NUMERIC },
  won_count: { title: "Races Won", type: FILTER_TYPE.NUMERIC },
  name_count: { title: "Names", type: FILTER_TYPE.NUMERIC },
  victim_count: { title: "Victims", type: FILTER_TYPE.NUMERIC }
}
