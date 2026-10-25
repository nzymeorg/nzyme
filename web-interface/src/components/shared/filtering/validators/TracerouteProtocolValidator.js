// Values are the IP protocol number as a string, since the protocol column is an integer.
export const TRACEROUTE_PROTOCOL_OPTIONS = [
  {value: "1", name: "ICMP (1)"},
  {value: "6", name: "TCP (6)"},
  {value: "17", name: "UDP (17)"}
];

export const ALLOWED_TRACEROUTE_PROTOCOL_VALUES = TRACEROUTE_PROTOCOL_OPTIONS.map((o) => o.value);

export const TRACEROUTE_PROTOCOL_SELECT_OPTIONS = TRACEROUTE_PROTOCOL_OPTIONS;
