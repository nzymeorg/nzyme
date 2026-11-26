import moment from "moment";
import ApiRoutes from "../../../../util/ApiRoutes";
import {Absolute} from "../../../shared/timerange/TimeRange";
import {serializeTimeRange} from "../../../shared/timerange/TimeRangeUrl";
import {filtersToQueryParameters} from "../../../shared/filtering/FilterQueryParameters";
import {filteredLink, macFilter} from "../../../ethernet/assets/details/AssetProfileHelpers";

export function discoSignalTransmitterFilters(addresses) {
  return {
    transmitter: addresses.flatMap((address) => macFilter("transmitter", address).transmitter)
  }
}

export const inTimeRange = (link, timeRange) => {
  return link + "&tr_timerange=" + encodeURIComponent(JSON.stringify(serializeTimeRange(timeRange)));
}

export function discoSignalLink(addresses, timeRange) {
  return inTimeRange(filteredLink(ApiRoutes.DOT11.DISCO.SIGNAL, discoSignalTransmitterFilters(addresses)), timeRange);
}

export function discoSignalPairLink(transmitter, receiver, timeRange) {
  return inTimeRange(filteredLink(ApiRoutes.DOT11.DISCO.SIGNAL,
      {...macFilter("transmitter", transmitter), ...macFilter("receiver", receiver)}), timeRange);
}

export function discoFramesLink(filters, timeRange) {
  return inTimeRange(filteredLink(ApiRoutes.DOT11.DISCO.INDEX, filtersToQueryParameters(filters) || {}), timeRange);
}

export function discoAnomaliesLink(bssid, anomalyType, timeRange) {
  const filters = macFilter("bssid", bssid);
  if (anomalyType) {
    filters.anomaly_type = [{field: "anomaly_type", operator: "disco_anomaly_type_equals", value: anomalyType}];
  }

  return inTimeRange(filteredLink(ApiRoutes.DOT11.DISCO.ANOMALIES.INDEX, filters), timeRange);
}

const ANOMALY_MINUTES_BEFORE = 10;
const ANOMALY_MINUTES_AFTER = 5;
const ANOMALY_MAXIMUM_MINUTES = 120;

export function discoAnomalySignalTimeRange(firstSeen, lastSeen) {
  return Absolute(
      moment(firstSeen).subtract(ANOMALY_MINUTES_BEFORE, "minutes").toDate(),
      moment.min(moment(lastSeen).add(ANOMALY_MINUTES_AFTER, "minutes"),
          moment(firstSeen).add(ANOMALY_MAXIMUM_MINUTES, "minutes")).toDate()
  );
}

const ANOMALIES_OF_DISCONNECTION_FRAMES = ["UNPROTECTED_FRAME", "MIRRORED_FRAMES", "REPEATED_FRAMES", "SIGNAL_MISMATCH"];

export function isAnomalyOfDisconnectionFrames(anomalyType) {
  return ANOMALIES_OF_DISCONNECTION_FRAMES.includes(anomalyType);
}

// Null for anomalies that are not about disconnection frames. There is nothing to investigate for them.
export function discoAnomalySignalLink(anomaly, addresses = null) {
  if (!isAnomalyOfDisconnectionFrames(anomaly.anomaly_type)) {
    return null;
  }

  const transmitters = addresses ? addresses
      : (anomaly.client ? [anomaly.bssid.mac.address, anomaly.client.mac.address] : [anomaly.bssid.mac.address]);

  return discoSignalLink(transmitters, discoAnomalySignalTimeRange(anomaly.first_seen, anomaly.last_seen));
}

export function discoSignalStartLink(timeRange) {
  return ApiRoutes.DOT11.DISCO.SIGNAL + "?tr_timerange=" + encodeURIComponent(JSON.stringify(serializeTimeRange(timeRange)));
}
