// The type numbers of ICMP and ICMPv6 overlap, so an ICMPv6 type carries its version: "6:128". ICMP types are the
// plain number, which is what filters and monitors saved before ICMPv6 hold.
export const ICMP_TYPE_OPTIONS = [
  {value: "0", name: "Echo Reply (0)"},
  {value: "3", name: "Destination Unreachable (3)"},
  {value: "4", name: "Source Quench (4)"},
  {value: "5", name: "Redirect (5)"},
  {value: "8", name: "Echo Request (8)"},
  {value: "9", name: "Router Advertisement (9)"},
  {value: "10", name: "Router Solicitation (10)"},
  {value: "11", name: "Time Exceeded (11)"},
  {value: "12", name: "Parameter Problem (12)"},
  {value: "13", name: "Timestamp Request (13)"},
  {value: "14", name: "Timestamp Reply (14)"},
  {value: "15", name: "Information Request (15)"},
  {value: "16", name: "Information Reply (16)"},
  {value: "17", name: "Address Mask Request (17)"},
  {value: "18", name: "Address Mask Reply (18)"},
  {value: "6:1", name: "Destination Unreachable (ICMPv6 1)"},
  {value: "6:2", name: "Packet Too Big (ICMPv6 2)"},
  {value: "6:3", name: "Time Exceeded (ICMPv6 3)"},
  {value: "6:4", name: "Parameter Problem (ICMPv6 4)"},
  {value: "6:128", name: "Echo Request (ICMPv6 128)"},
  {value: "6:129", name: "Echo Reply (ICMPv6 129)"},
  {value: "6:137", name: "Redirect (ICMPv6 137)"},
  {value: "6:138", name: "Router Renumbering (ICMPv6 138)"},
  {value: "6:139", name: "Node Information Query (ICMPv6 139)"},
  {value: "6:140", name: "Node Information Response (ICMPv6 140)"},
  {value: "6:141", name: "Inverse Neighbor Solicitation (ICMPv6 141)"},
  {value: "6:142", name: "Inverse Neighbor Advertisement (ICMPv6 142)"}
];

export const ALLOWED_ICMP_TYPE_VALUES = ICMP_TYPE_OPTIONS.map((o) => o.value);

export const ICMP_TYPE_SELECT_OPTIONS = ICMP_TYPE_OPTIONS;
