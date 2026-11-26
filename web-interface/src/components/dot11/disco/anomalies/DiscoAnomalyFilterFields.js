import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const DISCO_ANOMALY_FILTER_FIELDS = {
  anomaly_type: { title: "Anomaly", type: FILTER_TYPE.DISCO_ANOMALY_TYPE },
  bssid: { title: "BSSID", type: FILTER_TYPE.MAC_ADDRESS },
  client: { title: "Client", type: FILTER_TYPE.MAC_ADDRESS },
  frame_count: { title: "Frames", type: FILTER_TYPE.NUMERIC }
}
