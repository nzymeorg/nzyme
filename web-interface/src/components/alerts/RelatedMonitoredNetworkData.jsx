import {Link} from "react-router-dom";
import React from "react";
import LoadingSpinner from "../misc/LoadingSpinner";
import ApiRoutes from "../../util/ApiRoutes";

function RelatedMonitoredNetworkData(props) {

  const ssid = props.ssid;

  if (!ssid) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <dl className="mb-0">
          <dt>Monitored Network</dt>
          <dd>
            <Link to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(ssid.uuid)}>
              {ssid.ssid}
            </Link>
          </dd>
        </dl>
      </React.Fragment>
  )

}

export default RelatedMonitoredNetworkData;