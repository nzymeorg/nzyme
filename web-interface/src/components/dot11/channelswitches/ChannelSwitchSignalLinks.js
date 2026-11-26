import ApiRoutes from "../../../util/ApiRoutes";
import {serializeTimeRange} from "../../shared/timerange/TimeRangeUrl";
import {filtersToQueryParameters} from "../../shared/filtering/FilterQueryParameters";
import {filteredLink, macFilter} from "../../ethernet/assets/details/AssetProfileHelpers";
import {inTimeRange} from "../disco/signal/DiscoSignalLinks";

export function channelSwitchSignalLink(bssids, timeRange) {
  return inTimeRange(filteredLink(ApiRoutes.DOT11.CHANNEL_SWITCHES.SIGNAL,
      {bssid: bssids.flatMap((bssid) => macFilter("bssid", bssid).bssid)}), timeRange);
}

export function channelSwitchSignalStartLink(timeRange) {
  return ApiRoutes.DOT11.CHANNEL_SWITCHES.SIGNAL + "?tr_timerange="
      + encodeURIComponent(JSON.stringify(serializeTimeRange(timeRange)));
}

export function channelSwitchAnnouncementsLink(filters, timeRange) {
  return inTimeRange(filteredLink(ApiRoutes.DOT11.CHANNEL_SWITCHES.INDEX, filtersToQueryParameters(filters) || {}),
      timeRange);
}
