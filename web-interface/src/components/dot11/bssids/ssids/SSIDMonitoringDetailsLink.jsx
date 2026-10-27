import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../util/ApiRoutes";

function SSIDMonitoringDetailsLink(props) {

  const show = props.show;
  const uuid = props.uuid;

  if (show) {
    return <Link to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(uuid)}>Open Monitoring Details</Link>
  } else {
    return null;
  }

}

export default SSIDMonitoringDetailsLink;