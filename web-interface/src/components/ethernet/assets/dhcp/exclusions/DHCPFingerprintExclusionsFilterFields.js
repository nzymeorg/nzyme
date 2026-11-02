import {FILTER_TYPE} from "../../../../shared/filtering/Filters";

export const DHCP_FINGERPRINT_EXCLUSIONS_FILTER_FIELDS = {
  name: { title: "Name", type: FILTER_TYPE.STRING },
  description: { title: "Description", type: FILTER_TYPE.STRING },
  enabled: { title: "Enabled", type: FILTER_TYPE.BOOLEAN },
  mac: { title: "MAC Address", type: FILTER_TYPE.STRING },
  match_count: { title: "Suppressed Alerts", type: FILTER_TYPE.NUMERIC }
}
