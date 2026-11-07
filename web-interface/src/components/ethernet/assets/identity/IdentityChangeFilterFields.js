import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const IDENTITY_CHANGE_FILTER_FIELDS = {
  mac: { title: "MAC Address", type: FILTER_TYPE.MAC_ADDRESS },
  score: { title: "Score", type: FILTER_TYPE.NUMERIC },
  previous_fingerprint: { title: "Previous TCP Fingerprint", type: FILTER_TYPE.STRING },
  new_fingerprint: { title: "New TCP Fingerprint", type: FILTER_TYPE.STRING }
}
