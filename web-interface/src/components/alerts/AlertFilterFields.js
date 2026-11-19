import {FILTER_TYPE} from "../shared/filtering/Filters";
import ApiRoutes from "../../util/ApiRoutes";

export const ALERT_FILTER_FIELDS = {
  status: { title: "Status (Active, Inactive, Resolved)", type: FILTER_TYPE.STRING },
  type: { title: "Detection Type", type: FILTER_TYPE.STRING },
  subsystem: { title: "Subsystem", type: FILTER_TYPE.STRING },
  location: { title: "Location", type: FILTER_TYPE.STRING },
  details: { title: "Details", type: FILTER_TYPE.STRING },
  affected_address: { title: "Affected IP Address", type: FILTER_TYPE.IP_ADDRESS },
  affected_mac: { title: "Affected MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  attribute: { title: "Any Attribute Value", type: FILTER_TYPE.STRING },
  reactivations: { title: "Re-activations", type: FILTER_TYPE.NUMERIC }
}

export const alertsAtLocationLink = (locationName) => {
  return ApiRoutes.ALERTS.INDEX + "?filters=" + encodeURIComponent(JSON.stringify({
    location: [{field: "location", operator: "equals", value: locationName}]
  }));
}
