import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const VPN_FILTER_FIELDS = {
  session_key: { title: "Tunnel ID", type: FILTER_TYPE.STRING },
  client_address: { title: "Client IP Address", type: FILTER_TYPE.IP_ADDRESS },
  client_mac: { title: "Client MAC Address", type: FILTER_TYPE.STRING },
  server_address: { title: "Server IP Address", type: FILTER_TYPE.IP_ADDRESS },
  server_mac: { title: "Server MAC Address", type: FILTER_TYPE.STRING },
  server_port: { title: "Server Port", type: FILTER_TYPE.NUMERIC },
  type: { title: "VPN Type", type: FILTER_TYPE.STRING },
  provider: { title: "Provider", type: FILTER_TYPE.STRING },
  server_name: { title: "Server Name", type: FILTER_TYPE.STRING },
  transport: { title: "Transport", type: FILTER_TYPE.STRING },
  handshake_status: { title: "Handshake Status", type: FILTER_TYPE.STRING },
  status: { title: "Status", type: FILTER_TYPE.STRING },
  message_types: { title: "Message Types", type: FILTER_TYPE.STRING },
  tunneled_bytes: { title: "Tunneled Bytes", type: FILTER_TYPE.NUMERIC },
  duration: { title: "Duration (ms)", type: FILTER_TYPE.NUMERIC }
}
