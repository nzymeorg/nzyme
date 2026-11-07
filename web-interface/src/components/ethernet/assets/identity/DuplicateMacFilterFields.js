import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const DUPLICATE_MAC_FILTER_FIELDS = {
  mac: { title: "MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  stack_count: { title: "Stacks", type: FILTER_TYPE.NUMERIC },
  session_count: { title: "Sessions", type: FILTER_TYPE.NUMERIC }
}
