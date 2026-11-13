import React from "react";

export default function DisplayStatus({display}) {

  if (display.expired) {
    return <span className="text-danger">Expired</span>
  }

  if (display.online) {
    return <span>Online</span>
  }

  if (!display.last_seen_at) {
    return <span className="text-muted">Never connected</span>
  }

  return <span className="text-warning">Offline</span>

}
