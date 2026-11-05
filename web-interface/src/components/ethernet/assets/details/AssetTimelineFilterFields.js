import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const ASSET_TIMELINE_FILTER_FIELDS = {
  type: { title: "Type", type: FILTER_TYPE.ASSET_CHANGE_TYPE },
  value: { title: "Change", type: FILTER_TYPE.STRING }
}
