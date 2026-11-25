import {
  CHANNEL_SWITCH_ANOMALIES,
  CHANNEL_SWITCH_FRAME_TYPES,
  CHANNEL_SWITCH_OUTCOMES,
  channelSwitchAnomalyToName,
  channelSwitchFrameTypeToName,
  channelSwitchOutcomeToName
} from "../../../dot11/channelswitches/ChannelSwitchNames";

export const ALLOWED_CHANNEL_SWITCH_FRAME_TYPE_VALUES = CHANNEL_SWITCH_FRAME_TYPES;
export const ALLOWED_CHANNEL_SWITCH_ANOMALY_VALUES = CHANNEL_SWITCH_ANOMALIES;
export const ALLOWED_CHANNEL_SWITCH_OUTCOME_VALUES = CHANNEL_SWITCH_OUTCOMES;

export const CHANNEL_SWITCH_FRAME_TYPE_SELECT_OPTIONS = CHANNEL_SWITCH_FRAME_TYPES.map((type) => {
  return {name: channelSwitchFrameTypeToName(type), value: type};
});

export const CHANNEL_SWITCH_ANOMALY_SELECT_OPTIONS = CHANNEL_SWITCH_ANOMALIES.map((anomaly) => {
  return {name: channelSwitchAnomalyToName(anomaly), value: anomaly};
});

export const CHANNEL_SWITCH_OUTCOME_SELECT_OPTIONS = CHANNEL_SWITCH_OUTCOMES.map((outcome) => {
  return {name: channelSwitchOutcomeToName(outcome), value: outcome};
});
