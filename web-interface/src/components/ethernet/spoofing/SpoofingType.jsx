import React from "react";

export const spoofingTypeToName = (type) => {
  switch (type) {
    case "ARP": return "ARP and NDP Spoofing";
    case "DHCP": return "DHCP and Router Spoofing";
    case "NAME_RESOLUTION": return "Name Resolution Poisoning";
    case "DNS": return "DNS Spoofing";
    default: return type;
  }
}

export default function SpoofingType({type}) {
  return <span title={type}>{spoofingTypeToName(type)}</span>
}
