import React from "react";
import {truncate} from "../../../util/Tools";

export default function AssetHostnames({hostnames, filterElement = undefined}) {

  const additional = () => {
    if (hostnames.length < 2) {
      return null
    }

    return <span>(+{hostnames.length-1})</span>
  }

  if (hostnames === null || hostnames.length === 0) {
    return <span className="text-muted">None</span>;
  }

  return (
      <span title={hostnames.join(", ")}>
        <span className="hostname">{truncate(hostnames[0], 35, false)}</span>{' '}{additional()}{filterElement}
      </span>
  )

}