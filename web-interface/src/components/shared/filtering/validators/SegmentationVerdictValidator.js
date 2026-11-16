import {VERDICTS} from "../../../ethernet/segmentation/SegmentationFormat";

export const ALLOWED_SEGMENTATION_VERDICT_VALUES = ["ALLOWED", "VIOLATION", "INDETERMINATE"];

export const SEGMENTATION_VERDICT_SELECT_OPTIONS = ALLOWED_SEGMENTATION_VERDICT_VALUES.map((v) => {
  return {name: VERDICTS[v].title, value: v};
});
