import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const ICMP_EXCLUSIONS_FILTER_FIELDS = {
  name: { title: "Name", type: FILTER_TYPE.STRING },
  description: { title: "Description", type: FILTER_TYPE.STRING },
  enabled: { title: "Enabled", type: FILTER_TYPE.BOOLEAN },
  source_cidr: { title: "Source Address", type: FILTER_TYPE.STRING },
  source_mac: { title: "Source MAC", type: FILTER_TYPE.STRING },
  gateway_cidr: { title: "Gateway Address", type: FILTER_TYPE.STRING },
  match_count: { title: "Suppressed Detections", type: FILTER_TYPE.NUMERIC }
}
