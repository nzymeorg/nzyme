import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const ARP_FILTER_FIELDS = {
  operation: { title: "ARP Operation", type: FILTER_TYPE.STRING },
  arp_sender_mac: { title: "Sender MAC", type: FILTER_TYPE.STRING },
  arp_sender_tag: { title: "Sender Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  arp_sender_address: { title: "Sender Address", type: FILTER_TYPE.IP_ADDRESS },
  arp_target_mac: { title: "Target MAC", type: FILTER_TYPE.STRING },
  arp_target_tag: { title: "Target Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  arp_target_address: { title: "Target Address", type: FILTER_TYPE.IP_ADDRESS }
}