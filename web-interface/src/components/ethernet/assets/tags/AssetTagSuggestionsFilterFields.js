import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const ASSET_TAG_SUGGESTIONS_FILTER_FIELDS = {
  tag: { title: "Suggested Tag", type: FILTER_TYPE.STRING, value: (s) => s.tag },
  mac: { title: "MAC Address", type: FILTER_TYPE.STRING, value: (s) => s.asset.mac },
  name: { title: "Asset Name", type: FILTER_TYPE.STRING, value: (s) => s.asset.name },
  sessions: { title: "Sessions", type: FILTER_TYPE.NUMERIC, value: (s) => s.sessions }
}

export const ASSET_TAG_SUGGESTIONS_SORT_COLUMNS = {
  tag: (s) => s.tag,
  mac: (s) => s.asset.mac,
  sessions: (s) => s.sessions
}
