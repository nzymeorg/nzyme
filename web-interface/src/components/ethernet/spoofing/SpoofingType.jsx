import React from "react";

export const spoofingTypeToName = (type) => {
  switch (type) {
    case "ARP": return "ARP Poisoning";
    case "DHCP": return "DHCP Spoofing";
    case "NAME_RESOLUTION": return "Name Resolution Poisoning";
    default: return type;
  }
}

export default function SpoofingType({type}) {
  return <span title={type}>{spoofingTypeToName(type)}</span>
}
