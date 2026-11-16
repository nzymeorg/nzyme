import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const SEGMENTS_FILTER_FIELDS = {
  name: { title: "Name", type: FILTER_TYPE.STRING },
  description: { title: "Description", type: FILTER_TYPE.STRING },
  kind: { title: "Type", type: FILTER_TYPE.SEGMENT_KIND },
  network: { title: "Network", type: FILTER_TYPE.IP_ADDRESS },
  asn: { title: "ASN", type: FILTER_TYPE.NUMERIC }
}
