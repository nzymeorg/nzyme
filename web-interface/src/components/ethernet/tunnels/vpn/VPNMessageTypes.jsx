import React from "react";

export default function VPNMessageTypes({messageTypes}) {

  if (!messageTypes || messageTypes.length === 0) {
    return <span className="text-muted">n/a</span>
  }

  return <span>{messageTypes.join(", ")}</span>

}
