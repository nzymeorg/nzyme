import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const NDP_MESSAGE_TYPES = {
  NEIGHBOR_SOLICITATION: "Neighbor Solicitation",
  NEIGHBOR_ADVERTISEMENT: "Neighbor Advertisement",
  ROUTER_SOLICITATION: "Router Solicitation"
}

export const NDP_FILTER_FIELDS = {
  message_type: { title: "Message Type", type: FILTER_TYPE.STRING },
  source_mac: { title: "Source MAC", type: FILTER_TYPE.MAC_ADDRESS },
  source_tag: { title: "Source Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  source_address: { title: "Source Address", type: FILTER_TYPE.IP_ADDRESS },
  destination_address: { title: "Destination Address", type: FILTER_TYPE.IP_ADDRESS },
  target_address: { title: "Target Address", type: FILTER_TYPE.IP_ADDRESS },
  source_link_layer_address: { title: "Source Link-Layer Address", type: FILTER_TYPE.MAC_ADDRESS },
  target_link_layer_address: { title: "Target Link-Layer Address", type: FILTER_TYPE.MAC_ADDRESS },
  hop_limit: { title: "Hop Limit", type: FILTER_TYPE.NUMERIC },
  router: { title: "Router Flag", type: FILTER_TYPE.BOOLEAN },
  solicited: { title: "Solicited Flag", type: FILTER_TYPE.BOOLEAN },
  overrides: { title: "Override Flag", type: FILTER_TYPE.BOOLEAN },
  packet_count: { title: "Packet Count", type: FILTER_TYPE.NUMERIC }
}

export const NDP_ROUTER_ADVERTISEMENT_FILTER_FIELDS = {
  source_mac: { title: "Router MAC", type: FILTER_TYPE.MAC_ADDRESS },
  source_tag: { title: "Router Asset Tag", type: FILTER_TYPE.ASSET_TAG },
  source_address: { title: "Router Address", type: FILTER_TYPE.IP_ADDRESS },
  hop_limit: { title: "Hop Limit", type: FILTER_TYPE.NUMERIC },
  managed: { title: "Managed Address Configuration", type: FILTER_TYPE.BOOLEAN },
  other_configuration: { title: "Other Configuration", type: FILTER_TYPE.BOOLEAN },
  preference: { title: "Preference", type: FILTER_TYPE.STRING },
  router_lifetime: { title: "Router Lifetime (Seconds)", type: FILTER_TYPE.NUMERIC },
  packet_count: { title: "Packet Count", type: FILTER_TYPE.NUMERIC }
}
