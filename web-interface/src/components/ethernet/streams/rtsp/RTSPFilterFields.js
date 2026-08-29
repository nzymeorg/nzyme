import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const RTSP_FILTER_FIELDS = {
  id: { title: "ID", type: FILTER_TYPE.STRING },
  type: { title: "Connection Type", type: FILTER_TYPE.L4_CONNECTION_TYPE },
  stream_source_address: { title: "Source IP Address", type: FILTER_TYPE.IP_ADDRESS },
  stream_source_mac: { title: "Source MAC Address", type: FILTER_TYPE.STRING },
  stream_destination_address: { title: "Destination IP Address", type: FILTER_TYPE.IP_ADDRESS },
  stream_destination_mac: { title: "Destination MAC Address", type: FILTER_TYPE.STRING },
  bytes_rx_count: { title: "Transmitted (TX) Bytes", type: FILTER_TYPE.NUMERIC },
  bytes_tx_count: { title: "Received (RX) Bytes", type: FILTER_TYPE.NUMERIC },
  duration_ms: { title: "Duration (Milliseconds)", type: FILTER_TYPE.NUMERIC },
}