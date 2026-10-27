import {FILTER_TYPE} from "../../../../shared/filtering/Filters";

export const DNS_TUNNEL_EXCLUSIONS_FILTER_FIELDS = {
  name: { title: "Name", type: FILTER_TYPE.STRING },
  description: { title: "Description", type: FILTER_TYPE.STRING },
  enabled: { title: "Enabled", type: FILTER_TYPE.BOOLEAN },
  domain: { title: "Domain", type: FILTER_TYPE.STRING },
  client_address_cidr: { title: "Client Address", type: FILTER_TYPE.STRING },
  match_count: { title: "Suppressed Findings", type: FILTER_TYPE.NUMERIC }
}
