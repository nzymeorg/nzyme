import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const SEGMENTATION_POLICIES_FILTER_FIELDS = {
  name: { title: "Name", type: FILTER_TYPE.STRING },
  description: { title: "Description", type: FILTER_TYPE.STRING },
  status: { title: "Status", type: FILTER_TYPE.SEGMENTATION_POLICY_STATUS },
  alert_on_connected: { title: "Alerts on Connections", type: FILTER_TYPE.BOOLEAN },
  alert_on_attempted: { title: "Alerts on Attempts", type: FILTER_TYPE.BOOLEAN },
  crossed_findings: { title: "Crossed Findings", type: FILTER_TYPE.NUMERIC },
  attempted_findings: { title: "Attempted Findings", type: FILTER_TYPE.NUMERIC }
}
