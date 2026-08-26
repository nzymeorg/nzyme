import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS = {

  uuid: { title: "ID", type: FILTER_TYPE.UUID },
  probe_name: { title: "Probe Name", type: FILTER_TYPE.STRING },
  control_url: { title: "Control URL", type: FILTER_TYPE.STRING },
  last_hop_url: { title: "Final URL", type: FILTER_TYPE.STRING },
  hop_count: { title: "Hop Count", type: FILTER_TYPE.NUMERIC }

}