import React from "react";

export const dhcpSpoofingFindingTypeToName = (type) => {
  switch (type) {
    case "ROGUE_SERVER": return "Rogue Server";
    case "PARAMETERS_CHANGED": return "Config Change";
    default: return type;
  }
}

export default function DhcpSpoofingFindingType({type}) {
  const className = type === "ROGUE_SERVER" ? "badge bg-danger" : "badge bg-warning text-dark";

  return <span className={className} title={type}>{dhcpSpoofingFindingTypeToName(type)}</span>
}
