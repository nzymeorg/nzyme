import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const DNS_TUNNEL_FILTER_FIELDS = {
  client_address: { title: "Client Address", type: FILTER_TYPE.IP_ADDRESS },
  client_mac: { title: "Client MAC", type: FILTER_TYPE.MAC_ADDRESS },
  domain: { title: "Domain", type: FILTER_TYPE.STRING },
  query_count: { title: "Queries", type: FILTER_TYPE.NUMERIC },
  distinct_name_count: { title: "Distinct Names", type: FILTER_TYPE.NUMERIC },
  negative_count: { title: "Negative Replies", type: FILTER_TYPE.NUMERIC },
  indicator: { title: "Indicator", type: FILTER_TYPE.STRING }
}
