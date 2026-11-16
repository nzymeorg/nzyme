import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const SEGMENTATION_FLOWS_FILTER_FIELDS = {
  source_address: { title: "Source Address", type: FILTER_TYPE.IP_ADDRESS },
  source_asn: { title: "Source ASN", type: FILTER_TYPE.NUMERIC },
  source_country_code: { title: "Source Country Code", type: FILTER_TYPE.STRING },
  destination_address: { title: "Destination Address", type: FILTER_TYPE.IP_ADDRESS },
  destination_asn: { title: "Destination ASN", type: FILTER_TYPE.NUMERIC },
  destination_country_code: { title: "Destination Country Code", type: FILTER_TYPE.STRING },
  l4_type: { title: "Type", type: FILTER_TYPE.L4_SESSION_TYPE },
  port: { title: "Port", type: FILTER_TYPE.NUMERIC },
  protocol: { title: "Protocol", type: FILTER_TYPE.STRING },
  outcome: { title: "Outcome", type: FILTER_TYPE.SEGMENTATION_OUTCOME },
  sessions: { title: "Sessions", type: FILTER_TYPE.NUMERIC }
}
