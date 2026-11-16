import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const ASSET_TAGS_FILTER_FIELDS = {
  name: { title: "Name", type: FILTER_TYPE.STRING },
  description: { title: "Description", type: FILTER_TYPE.STRING },
  asset_count: { title: "Assets", type: FILTER_TYPE.NUMERIC }
}
