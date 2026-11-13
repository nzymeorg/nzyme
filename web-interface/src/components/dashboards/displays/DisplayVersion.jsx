import React from "react";

export default function DisplayVersion({display}) {

  if (!display.online || !display.last_reported_version) {
    return <span className="text-muted">n/a</span>
  }

  if (display.last_reported_version === display.node_version) {
    return <span>Up to date</span>
  }

  return (
    <span className="text-warning"
          title={"Loaded " + display.last_reported_version + ", this node runs " + display.node_version}>
      Reload pending
    </span>
  )

}
