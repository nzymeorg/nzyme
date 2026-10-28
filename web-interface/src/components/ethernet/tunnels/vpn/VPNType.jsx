import React from "react";

export default function VPNType({type, provider}) {

  const providerSuffix = provider ? <span className="text-muted"> ({provider})</span> : null;

  switch (type) {
    case "WireGuard":
      return <span>WireGuard{providerSuffix}</span>
    case "OpenVPN":
      return <span>OpenVPN{providerSuffix}</span>
    case "DERP":
      return <span title="Relayed through a DERP server.">DERP{providerSuffix}</span>
    default:
      return <span className="text-muted">{type}{providerSuffix}</span>
  }

}
