import {OUTCOMES} from "../../../ethernet/segmentation/SegmentationFormat";

export const ALLOWED_SEGMENTATION_OUTCOME_VALUES = ["CONNECTED", "REJECTED", "NO_REPLY", "SENT"];

export const SEGMENTATION_OUTCOME_SELECT_OPTIONS = ALLOWED_SEGMENTATION_OUTCOME_VALUES.map((o) => {
  return {name: OUTCOMES[o].title, value: o};
});
