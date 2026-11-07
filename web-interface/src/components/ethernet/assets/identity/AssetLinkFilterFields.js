import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const ASSET_LINK_FILTER_FIELDS = {
  predecessor_mac: { title: "Previous MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  successor_mac: { title: "New MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  kind: { title: "Kind", type: FILTER_TYPE.STRING },
  status: { title: "Status", type: FILTER_TYPE.STRING },
  confidence: { title: "Confidence", type: FILTER_TYPE.NUMERIC }
}
