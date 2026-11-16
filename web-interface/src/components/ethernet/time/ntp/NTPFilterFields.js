import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const NTP_FILTER_FIELDS = {
  transaction_key: { title: "Transaction ID", type: FILTER_TYPE.STRING },
  client_mac: { title: "Client MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  client_tag: { title: "Client Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  client_address: { title: "Client Address", type: FILTER_TYPE.IP_ADDRESS },
  client_port: { title: "Client Port", type: FILTER_TYPE.PORT_NUMBER },
  server_mac: { title: "Server MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  server_tag: { title: "Server Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  server_address: { title: "Server Address", type: FILTER_TYPE.IP_ADDRESS },
  server_port: { title: "Server Port", type: FILTER_TYPE.PORT_NUMBER },
  stratum: { title: "Stratum", type: FILTER_TYPE.NUMERIC },
  reference_id: { title: "Clock Reference", type: FILTER_TYPE.STRING },

}