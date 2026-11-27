import moment from "moment";
import ApiRoutes from "../../../../util/ApiRoutes";
import {Presets} from "../../../shared/timerange/TimeRange";
import {serializeTimeRange} from "../../../shared/timerange/TimeRangeUrl";
import {filteredLink} from "../../../ethernet/assets/details/AssetProfileHelpers";

// The access points page shows the last 24 hours by default, which would be empty for a network last seen earlier.
export default function accessPointsOfSsidLink(ssid, lastSeen) {
  const age = moment().diff(moment(lastSeen), "minutes");

  let timeRange = Presets.ALL_TIME;
  if (age < Presets.RELATIVE_HOURS_24.minutes) {
    timeRange = Presets.RELATIVE_HOURS_24;
  } else if (age < Presets.RELATIVE_DAYS_7.minutes) {
    timeRange = Presets.RELATIVE_DAYS_7;
  }

  const filters = {advertised_ssid: [{field: "advertised_ssid", operator: "equals", value: ssid}]};

  return filteredLink(ApiRoutes.DOT11.NETWORKS.BSSIDS.INDEX, filters)
      + "&tr_timerange=" + encodeURIComponent(JSON.stringify(serializeTimeRange(timeRange)));
}
