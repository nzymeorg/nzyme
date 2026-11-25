import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const CHANNEL_SWITCH_FILTER_FIELDS = {
  bssid: { title: "BSSID", type: FILTER_TYPE.MAC_ADDRESS },
  frame_type: { title: "Frame Type", type: FILTER_TYPE.CHANNEL_SWITCH_FRAME_TYPE },
  frequency: { title: "Frequency (MHz)", type: FILTER_TYPE.NUMERIC },
  new_channel: { title: "Announced Channel", type: FILTER_TYPE.NUMERIC },
  new_frequency: { title: "Announced Frequency (MHz)", type: FILTER_TYPE.NUMERIC },
  switch_mode: { title: "Switch Mode", type: FILTER_TYPE.NUMERIC },
  count: { title: "Countdown", type: FILTER_TYPE.NUMERIC },
  frame_count: { title: "Frames", type: FILTER_TYPE.NUMERIC },
  peak_rate: { title: "Peak Rate (Frames per Second)", type: FILTER_TYPE.NUMERIC },
  signal_strength: { title: "Signal Strength (dBm)", type: FILTER_TYPE.NUMERIC },
  anomaly: { title: "Anomaly", type: FILTER_TYPE.CHANNEL_SWITCH_ANOMALY },
  has_anomalies: { title: "Has Anomalies", type: FILTER_TYPE.BOOLEAN },
  outcome: { title: "Outcome", type: FILTER_TYPE.CHANNEL_SWITCH_OUTCOME }
}
