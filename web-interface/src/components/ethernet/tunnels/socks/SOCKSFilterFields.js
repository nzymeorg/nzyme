import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const SOCKS_FILTER_FIELDS = {
  session_key: { title: "Tunnel ID", type: FILTER_TYPE.STRING },
  client_address: { title: "Client IP Address", type: FILTER_TYPE.IP_ADDRESS },
  client_mac: { title: "Client MAC Address", type: FILTER_TYPE.STRING },
  client_tag: { title: "Client Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  server_address: { title: "Server IP Address", type: FILTER_TYPE.IP_ADDRESS },
  server_mac: { title: "Server MAC Address", type: FILTER_TYPE.STRING },
  server_tag: { title: "Server Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  tunneled_destination_address: { title: "Destination IP Address", type: FILTER_TYPE.IP_ADDRESS },
  tunneled_destination_host: { title: "Destination Hostname", type: FILTER_TYPE.STRING },
  tunneled_destination_port: { title: "Destination Port", type: FILTER_TYPE.NUMERIC },
  type: { title: "Type", type: FILTER_TYPE.STRING },
  status: { title: "Status", type: FILTER_TYPE.STRING },
  tunneled_bytes: { title: "Tunneled Bytes", type: FILTER_TYPE.NUMERIC },
  duration: { title: "Duration (ms)", type: FILTER_TYPE.NUMERIC }
}