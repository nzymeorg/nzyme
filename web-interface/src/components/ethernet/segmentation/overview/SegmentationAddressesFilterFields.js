import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const SEGMENTATION_ADDRESSES_FILTER_FIELDS = {
  address: { title: "Address", type: FILTER_TYPE.IP_ADDRESS },
  sessions_from: { title: "Sessions From", type: FILTER_TYPE.NUMERIC },
  sessions_to: { title: "Sessions To", type: FILTER_TYPE.NUMERIC },
  answered: { title: "Answered", type: FILTER_TYPE.NUMERIC }
}
