import {FINDING_CLASSES} from "../../../ethernet/segmentation/SegmentationFormat";

export const ALLOWED_SEGMENTATION_FINDING_CLASS_VALUES = ["CROSSED", "ATTEMPTED"];

export const SEGMENTATION_FINDING_CLASS_SELECT_OPTIONS = ALLOWED_SEGMENTATION_FINDING_CLASS_VALUES.map((c) => {
  return {name: FINDING_CLASSES[c].title, value: c};
});
