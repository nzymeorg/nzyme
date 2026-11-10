import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const DISCO_FILTER_FIELDS = {
  disco_type: { title: "Frame Type", type: FILTER_TYPE.DISCO_TYPE },
  transmitter: { title: "Transmitter", type: FILTER_TYPE.MAC_ADDRESS },
  receiver: { title: "Receiver", type: FILTER_TYPE.MAC_ADDRESS },
  bssid: { title: "BSSID", type: FILTER_TYPE.MAC_ADDRESS },
  reason_code: { title: "Reason Code", type: FILTER_TYPE.NUMERIC },
  frequency: { title: "Frequency (MHz)", type: FILTER_TYPE.NUMERIC },
  signal_strength: { title: "Signal Strength (dBm)", type: FILTER_TYPE.NUMERIC },
  is_broadcast: { title: "Sent to Broadcast Address", type: FILTER_TYPE.BOOLEAN },
  transmitter_is_bssid: { title: "Transmitter is BSSID", type: FILTER_TYPE.BOOLEAN },
  bssid_involved: { title: "BSSID is Transmitter or Receiver", type: FILTER_TYPE.BOOLEAN }
}
