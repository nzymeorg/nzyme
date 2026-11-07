import React from "react";

export default function FindingActiveIndicator({active}) {

  if (active) {
    return <i className="fa-solid fa-circle text-danger blink" title="Seen inside the current detection window." />
  }

  return <i className="fa-solid fa-circle text-muted" title="Not seen inside the current detection window." />

}
