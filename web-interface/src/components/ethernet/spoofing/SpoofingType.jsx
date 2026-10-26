import React from "react";

export const spoofingTypeToName = (type) => {
  switch (type) {
    case "ARP": return "ARP Poisoning";
    default: return type;
  }
}

export default function SpoofingType({type}) {
  return <span title={type}>{spoofingTypeToName(type)}</span>
}
