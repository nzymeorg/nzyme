import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const SUGGESTION_SOURCES = {
  DHCP: {title: "DHCP server", description: "The network a trusted DHCP server hands out addresses for."},
  ROUTER_ADVERTISEMENT: {title: "IPv6 router", description: "A prefix a trusted IPv6 router advertises."},
  OBSERVED: {title: "Observed", description: "Addresses that were seen talking and are in no segment."}
};

const sourceTitle = (suggestion) => {
  return SUGGESTION_SOURCES[suggestion.source] ? SUGGESTION_SOURCES[suggestion.source].title : suggestion.source;
}

export const SEGMENT_SUGGESTIONS_FILTER_FIELDS = {
  source: { title: "Source", type: FILTER_TYPE.STRING, value: sourceTitle },
  dhcp_server: { title: "DHCP Server or Router", type: FILTER_TYPE.STRING, value: (s) => s.dhcp_server },
  network: { title: "Network", type: FILTER_TYPE.STRING, value: (s) => s.network },
  hosts: { title: "Hosts Seen", type: FILTER_TYPE.NUMERIC, value: (s) => s.hosts }
}

export const SEGMENT_SUGGESTIONS_SORT_COLUMNS = {
  source: sourceTitle,
  network: (s) => s.network,
  hosts: (s) => s.hosts
}
