import React from "react";

export const nameResolutionProtocolToName = (protocol) => {
  switch (protocol) {
    case "LLMNR": return "LLMNR";
    case "MDNS": return "mDNS";
    case "NBTNS": return "NBT-NS";
    default: return protocol;
  }
}

export default function NameResolutionProtocols({protocols}) {

  if (!protocols || protocols.length === 0) {
    return <span className="text-muted">None</span>
  }

  return <span>{protocols.map(nameResolutionProtocolToName).join(", ")}</span>

}
