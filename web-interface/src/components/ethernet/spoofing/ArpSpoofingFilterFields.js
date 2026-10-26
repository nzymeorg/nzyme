import {FILTER_TYPE} from "../../shared/filtering/Filters";

export const ARP_SPOOFING_FILTER_FIELDS = {
  address: { title: "Address", type: FILTER_TYPE.IP_ADDRESS },
  spoofer_mac: { title: "Spoofer MAC", type: FILTER_TYPE.MAC_ADDRESS },
  owner_mac: { title: "Owner MAC", type: FILTER_TYPE.MAC_ADDRESS },
  claim_count: { title: "Claims", type: FILTER_TYPE.NUMERIC },
  unsolicited_count: { title: "Unsolicited Replies", type: FILTER_TYPE.NUMERIC },
  victim_count: { title: "Victims", type: FILTER_TYPE.NUMERIC }
}
