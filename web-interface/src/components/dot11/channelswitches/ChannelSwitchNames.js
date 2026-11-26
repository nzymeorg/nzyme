export const CHANNEL_SWITCH_FRAME_TYPES = ["BEACON", "PROBE_RESPONSE", "ACTION"];

export const CHANNEL_SWITCH_ANOMALIES = [
  "INVALID_CHANNEL",
  "REPEATED_TIMESTAMP",
  "TIMESTAMP_MISMATCH",
  "INTERLEAVED_BEACONS",
  "SIGNAL_MISMATCH",
  "STATIC_COUNT",
  "TRAILING_ELEMENT"
];

export const CHANNEL_SWITCH_OUTCOMES = ["SWITCHED", "STAYED", "BOTH_CHANNELS", "UNKNOWN"];

export const CHANNEL_SWITCH_MONITOR_RULES = [
  "INVALID_CHANNEL",
  "REPEATED_TIMESTAMP",
  "TIMESTAMP_MISMATCH",
  "INTERLEAVED_BEACONS",
  "SIGNAL_MISMATCH",
  "BURST_RATE",
  "NOT_FOLLOWED"
];

export function channelSwitchMonitorRuleToName(rule) {
  switch (rule) {
    case "BURST_RATE":
      return "Rate Threshold";
    case "NOT_FOLLOWED":
      return "Not Followed";
    default:
      return channelSwitchAnomalyToName(rule);
  }
}

export function channelSwitchMonitorRuleDescription(rule) {
  switch (rule) {
    case "BURST_RATE":
      return "Beacons announce a switch at the configured rate or faster.";
    case "NOT_FOLLOWED":
      return "The access point was still on its channel after the announced switch.";
    default:
      return channelSwitchAnomalyDescription(rule);
  }
}

export function channelSwitchFrameTypeToName(type) {
  switch (type) {
    case "BEACON":
      return "Beacon";
    case "PROBE_RESPONSE":
      return "Probe Response";
    case "ACTION":
      return "Action";
    default:
      return type;
  }
}

export function channelSwitchAnomalyToName(anomaly) {
  switch (anomaly) {
    case "INVALID_CHANNEL":
      return "Invalid Channel";
    case "REPEATED_TIMESTAMP":
      return "Repeated Timestamp";
    case "TIMESTAMP_MISMATCH":
      return "Timestamp Mismatch";
    case "INTERLEAVED_BEACONS":
      return "Interleaved Beacons";
    case "SIGNAL_MISMATCH":
      return "Signal Strength Mismatch";
    case "STATIC_COUNT":
      return "Static Count";
    case "TRAILING_ELEMENT":
      return "Trailing Element";
    default:
      return anomaly;
  }
}

export function channelSwitchAnomalyDescription(anomaly) {
  switch (anomaly) {
    case "INVALID_CHANNEL":
      return "The access point cannot operate on the announced channel.";
    case "REPEATED_TIMESTAMP":
      return "Many beacons carried the same timestamp.";
    case "TIMESTAMP_MISMATCH":
      return "The timestamps of the beacons with the announcement do not continue the clock of the access point.";
    case "INTERLEAVED_BEACONS":
      return "Beacons without the announcement kept arriving between frames with it.";
    case "SIGNAL_MISMATCH":
      return "Frames with the announcement arrived much stronger or weaker than the beacons of the access point.";
    case "STATIC_COUNT":
      return "The countdown did not change over many beacons.";
    case "TRAILING_ELEMENT":
      return "The announcement was appended as the last element of the beacon.";
    default:
      return null;
  }
}

export function channelSwitchOutcomeToName(outcome) {
  switch (outcome) {
    case "SWITCHED":
      return "Switched";
    case "STAYED":
      return "Stayed";
    case "BOTH_CHANNELS":
      return "Both Channels";
    case "UNKNOWN":
      return "Unknown";
    default:
      return outcome;
  }
}
