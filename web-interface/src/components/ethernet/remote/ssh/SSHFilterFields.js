import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const SSH_FILTER_FIELDS = {
  session_key: { title: "Session ID", type: FILTER_TYPE.STRING },
  client_address: { title: "Client IP Address", type: FILTER_TYPE.IP_ADDRESS },
  client_mac: { title: "Client MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  client_tag: { title: "Client Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  client_version_software: { title: "Client Version (Software)", type: FILTER_TYPE.STRING },
  client_version_comments: { title: "Client Version (Comments)", type: FILTER_TYPE.STRING },
  server_address: { title: "Server IP Address", type: FILTER_TYPE.IP_ADDRESS },
  server_mac: { title: "Server MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  server_tag: { title: "Server Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  server_version_software: { title: "Server Version (Software)", type: FILTER_TYPE.STRING },
  server_version_comments: { title: "Server Version (Comments)", type: FILTER_TYPE.STRING },
  connection_status: { title: "Status", type: FILTER_TYPE.STRING },
  tunneled_bytes: { title: "Tunneled Bytes", type: FILTER_TYPE.NUMERIC },
  duration: { title: "Duration (ms)", type: FILTER_TYPE.NUMERIC }
}