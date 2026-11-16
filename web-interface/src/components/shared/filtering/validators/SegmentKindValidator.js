import {SEGMENT_KINDS} from "../../../ethernet/segmentation/SegmentationFormat";

export const ALLOWED_SEGMENT_KIND_VALUES = ["INTERNAL", "EXTERNAL"];

export const SEGMENT_KIND_SELECT_OPTIONS = ALLOWED_SEGMENT_KIND_VALUES.map((k) => {
  return {name: SEGMENT_KINDS[k].title, value: k};
});
