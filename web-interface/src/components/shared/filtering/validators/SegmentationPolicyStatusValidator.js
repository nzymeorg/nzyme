import {POLICY_STATUSES} from "../../../ethernet/segmentation/SegmentationFormat";

export const ALLOWED_SEGMENTATION_POLICY_STATUS_VALUES = ["ACTIVE", "AUDIT", "PAUSED"];

export const SEGMENTATION_POLICY_STATUS_SELECT_OPTIONS = ALLOWED_SEGMENTATION_POLICY_STATUS_VALUES.map((s) => {
  return {name: POLICY_STATUSES[s].title, value: s};
});
