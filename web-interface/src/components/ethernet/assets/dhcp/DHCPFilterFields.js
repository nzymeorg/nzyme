import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const DHCP_FILTER_FIELDS = {
  transaction_type: { title: "Transaction Type", type: FILTER_TYPE.STRING },
  ip_version: { title: "IP Version", type: FILTER_TYPE.NUMERIC },
  client_mac: { title: "Client MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  client_tag: { title: "Client Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  client_duid: { title: "Client DUID", type: FILTER_TYPE.STRING },
  server_mac: { title: "Server MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  server_tag: { title: "Server Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  requested_ip: { title: "Requested IP Address", type: FILTER_TYPE.IP_ADDRESS },
  fingerprint: { title: "Fingerprint", type: FILTER_TYPE.STRING }
}