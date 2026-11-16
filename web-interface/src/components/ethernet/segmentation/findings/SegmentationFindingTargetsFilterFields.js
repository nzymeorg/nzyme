import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const SEGMENTATION_FINDING_TARGETS_FILTER_FIELDS = {
  address: { title: "Address", type: FILTER_TYPE.IP_ADDRESS },
  l4_type: { title: "Type", type: FILTER_TYPE.L4_SESSION_TYPE },
  port: { title: "Port", type: FILTER_TYPE.NUMERIC },
  protocol: { title: "Protocol", type: FILTER_TYPE.STRING },
  outcome: { title: "Outcome", type: FILTER_TYPE.SEGMENTATION_OUTCOME },
  sessions: { title: "Sessions", type: FILTER_TYPE.NUMERIC }
}
