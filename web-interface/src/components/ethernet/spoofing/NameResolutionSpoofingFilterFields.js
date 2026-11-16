import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const NAME_RESOLUTION_SPOOFING_FILTER_FIELDS = {
  responder_mac: { title: "Responder MAC", type: FILTER_TYPE.MAC_ADDRESS },
  responder_tag: { title: "Responder Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  name_count: { title: "Names", type: FILTER_TYPE.NUMERIC },
  response_count: { title: "Responses", type: FILTER_TYPE.NUMERIC },
  victim_count: { title: "Victims", type: FILTER_TYPE.NUMERIC }
}
