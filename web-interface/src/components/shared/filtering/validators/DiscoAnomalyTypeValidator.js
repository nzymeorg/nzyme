import {DISCO_ANOMALY_TYPES} from "../../../dot11/disco/anomalies/DiscoAnomalyTypes";

export const ALLOWED_DISCO_ANOMALY_TYPE_VALUES = Object.keys(DISCO_ANOMALY_TYPES);

export const DISCO_ANOMALY_TYPE_SELECT_OPTIONS = ALLOWED_DISCO_ANOMALY_TYPE_VALUES.map((type) => {
  return {name: DISCO_ANOMALY_TYPES[type].name, value: type};
});
