import React from "react";

export default function VPNHandshakeStatus({status}) {

  switch (status) {
    case "Complete":
      return <span title="Both sides of the handshake were observed.">Complete</span>
    case "Partial":
      return <span title="Only one side of the handshake was observed.">Partial</span>
    case "NotObserved":
      return <span className="text-muted" title="Only established tunnel traffic was observed. The tunnel was likely established before monitoring started.">Not observed</span>
    default:
      return <span className="text-muted">Unknown</span>
  }

}
