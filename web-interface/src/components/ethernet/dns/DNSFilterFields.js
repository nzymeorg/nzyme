import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const DNS_FILTER_FIELDS = {
  query_value: { title: "Query Value", type: FILTER_TYPE.STRING },
  query_type: { title: "Query Type", type: FILTER_TYPE.DNS_TYPE },
  query_etld: { title: "Query eTLD", type: FILTER_TYPE.STRING },
  client_address: { title: "Client Address", type: FILTER_TYPE.IP_ADDRESS },
  server_address: { title: "Server Address", type: FILTER_TYPE.IP_ADDRESS },
  server_port: { title: "Server Port", type: FILTER_TYPE.PORT_NUMBER },
  client_mac: { title: "Client MAC Address", type: FILTER_TYPE.STRING },
  client_tag: { title: "Client Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  server_mac: { title: "Server MAC Address", type: FILTER_TYPE.STRING },
  server_tag: { title: "Server Asset Tag", type: FILTER_TYPE.ASSET_TAG },
}