import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../../util/ApiRoutes";

function NodeClockWarning(props) {

  if (!props.node || !props.node.active || !(props.node.clock_drift_ms < -5000 || props.node.clock_drift_ms > 5000)) {
    return null;
  }

  return (
      <div className="alert alert-danger">
        <i className="fa-solid fa-triangle-exclamation"></i> The local system clock of this node appears to be not
        synchronized with world reference time. Please follow instructions in the{' '}
        <Link to={ApiRoutes.SYSTEM.HEALTH.INDEX}>Health Console</Link> to fix this issue.
      </div>
  )

}

export default NodeClockWarning;
