import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const SPOOFING_EXCLUSIONS_FILTER_FIELDS = {
  name: { title: "Name", type: FILTER_TYPE.STRING },
  description: { title: "Description", type: FILTER_TYPE.STRING },
  enabled: { title: "Enabled", type: FILTER_TYPE.BOOLEAN },
  type: { title: "Type", type: FILTER_TYPE.SPOOFING_TYPE },
  address_cidr: { title: "Claimed Address", type: FILTER_TYPE.STRING },
  mac: { title: "Device MAC", type: FILTER_TYPE.STRING },
  match_count: { title: "Suppressed Findings", type: FILTER_TYPE.NUMERIC }
}
