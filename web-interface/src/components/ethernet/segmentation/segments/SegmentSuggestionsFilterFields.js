import {FILTER_TYPE} from "../../../shared/filtering/Filters";

export const SUGGESTION_SOURCES = {
  NETWORK_CONTEXT: {title: "Network context", description: "A network that has context and is no segment yet."},
  DHCP: {title: "DHCP server", description: "The network a trusted DHCP server hands out addresses for."},
  OBSERVED: {title: "Observed", description: "Addresses that were seen talking and are in no segment."}
};

const sourceTitle = (suggestion) => {
  return SUGGESTION_SOURCES[suggestion.source] ? SUGGESTION_SOURCES[suggestion.source].title : suggestion.source;
}

export const SEGMENT_SUGGESTIONS_FILTER_FIELDS = {
  source: { title: "Source", type: FILTER_TYPE.STRING, value: sourceTitle },
  dhcp_server: { title: "DHCP Server", type: FILTER_TYPE.STRING, value: (s) => s.dhcp_server },
  network: { title: "Network", type: FILTER_TYPE.STRING, value: (s) => s.network },
  kind: { title: "Type", type: FILTER_TYPE.SEGMENT_KIND, value: (s) => s.kind },
  name: { title: "Suggested Name", type: FILTER_TYPE.STRING, value: (s) => s.name },
  description: { title: "Suggested Description", type: FILTER_TYPE.STRING, value: (s) => s.description },
  hosts: { title: "Hosts Seen", type: FILTER_TYPE.NUMERIC, value: (s) => s.hosts }
}

export const SEGMENT_SUGGESTIONS_SORT_COLUMNS = {
  source: sourceTitle,
  network: (s) => s.network,
  kind: (s) => s.kind,
  name: (s) => s.name,
  description: (s) => s.description,
  hosts: (s) => s.hosts
}
