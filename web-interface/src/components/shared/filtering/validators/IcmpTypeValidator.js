// Values are the numeric type as a string, since the icmp_type column is an integer.
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
  {value: "18", name: "Address Mask Reply (18)"}
];

export const ALLOWED_ICMP_TYPE_VALUES = ICMP_TYPE_OPTIONS.map((o) => o.value);

export const ICMP_TYPE_SELECT_OPTIONS = ICMP_TYPE_OPTIONS;
