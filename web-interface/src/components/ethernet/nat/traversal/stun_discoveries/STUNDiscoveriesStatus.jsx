import React from "react";

export default function STUNDiscoveriesStatus({status}) {
  if (status === "COMPLETE") {
    return <i className="fa fa-circle text-success" title="Discovery Session Complete" />
  } else if (status === "ERROR") {
    return <i className="fa fa-circle text-danger" title="Discovery Session Error" />
  } else {
    return <i className="fa fa-circle text-warning" title="Discovery Session Incomplete" />

  }
}