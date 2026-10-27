import React from "react";

export const dhcpSpoofingFindingTypeToName = (type) => {
  switch (type) {
    case "ROGUE_SERVER": return "Rogue Server";
    case "PARAMETERS_CHANGED": return "Config Change";
    default: return type;
  }
}

export default function DhcpSpoofingFindingType({type}) {
  const className = type === "ROGUE_SERVER"
    ? "classification classification-hostile"
    : "classification classification-suspicious";

  return <span className={className} title={type}>{dhcpSpoofingFindingTypeToName(type)}</span>
}
