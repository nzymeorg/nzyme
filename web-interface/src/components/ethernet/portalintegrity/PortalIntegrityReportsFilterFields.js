import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS = {

  uuid: { title: "ID", type: FILTER_TYPE.UUID },
  probe_name: { title: "Probe Name", type: FILTER_TYPE.STRING },
  probe_interface: { title: "Probe Interface", type: FILTER_TYPE.STRING },
  probe_mac: { title: "Probe MAC Address", type: FILTER_TYPE.STRING },
  dhcp_server_address: { title: "DHCP Server Address", type: FILTER_TYPE.IP_ADDRESS },
  assigned_address: { title: "Assigned IP Address", type: FILTER_TYPE.IP_ADDRESS },
  gateway_address: { title: "Assigned Gateway Address", type: FILTER_TYPE.IP_ADDRESS },
  dns_servers: { title: "Assigned DNS Server Address", type: FILTER_TYPE.IP_ADDRESS },
  control_url: { title: "Control URL", type: FILTER_TYPE.STRING },
  last_hop_url: { title: "Final URL", type: FILTER_TYPE.STRING },
  hop_count: { title: "Hop Count", type: FILTER_TYPE.NUMERIC }

}