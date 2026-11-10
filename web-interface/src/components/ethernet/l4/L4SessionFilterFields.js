import {FILTER_TYPE} from "../../shared/filtering/Filters";

const transformSessionState = (state) => {
  return state.toUpperCase();
}

export const L4_SESSIONS_FILTER_FIELDS = {
  l4_type: { title: "Type", type: FILTER_TYPE.L4_SESSION_TYPE },
  state: { title: "Session State", type: FILTER_TYPE.L4_SESSION_STATE, value_transform: transformSessionState },
  // Match the source or the destination.
  address: { title: "Address", type: FILTER_TYPE.IP_ADDRESS },
  address_geo_asn_number: { title: "Address ASN Number", type: FILTER_TYPE.NUMERIC },
  address_geo_country_code: { title: "Address Country Code", type: FILTER_TYPE.STRING },
  address_geo_city: { title: "Address City", type: FILTER_TYPE.STRING },
  source_mac: { title: "Source MAC", type: FILTER_TYPE.STRING },
  source_address: { title: "Source Address", type: FILTER_TYPE.IP_ADDRESS },
  source_address_geo_asn_number: { title: "Source Address ASN Number", type: FILTER_TYPE.NUMERIC },
  source_address_geo_country_code: { title: "Source Address Country Code", type: FILTER_TYPE.STRING },
  source_address_geo_city: { title: "Source Address City", type: FILTER_TYPE.STRING },
  source_port: { title: "Source Port", type: FILTER_TYPE.NUMERIC },
  destination_mac: { title: "Destination MAC", type: FILTER_TYPE.STRING },
  destination_address: { title: "Destination Address", type: FILTER_TYPE.IP_ADDRESS },
  destination_address_geo_asn_number: { title: "Destination Address ASN Number", type: FILTER_TYPE.NUMERIC },
  destination_address_geo_country_code: { title: "Destination Address Country Code", type: FILTER_TYPE.STRING },
  destination_address_geo_city: { title: "Destination Address City", type: FILTER_TYPE.STRING },
  destination_port: { title: "Destination Port", type: FILTER_TYPE.NUMERIC },
  bytes_rx_count: { title: "RX Bytes", type: FILTER_TYPE.NUMERIC },
  bytes_tx_count: { title: "TX Bytes", type: FILTER_TYPE.NUMERIC },
  segments_count: { title: "Segments", type: FILTER_TYPE.NUMERIC },
  session_key: { title: "Session Key", type: FILTER_TYPE.STRING },
  fingerprint: { title: "Fingerprint", type: FILTER_TYPE.STRING },
  duration: { title: "Duration (ms)", type: FILTER_TYPE.NUMERIC },
  tags: { title: "Tags", type: FILTER_TYPE.STRING_ARRAY }
}