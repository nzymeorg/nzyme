import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../util/ApiRoutes";
import numeral from "numeral";
import WithPermission from "../../../misc/WithPermission";
import requiredUserPermissionForMonitorWriteAccess from "../../../monitors/shared/MonitorUserPermission";
import WithoutPermission from "../../../misc/WithoutPermission";
import MonitorType from "../../../monitors/shared/MonitorType";

export default function MonitorTriggeredAlertDetails({ alert }) {

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>
              <dl>
                <dt>Monitor</dt>
                <dd>
                  <WithPermission permission={requiredUserPermissionForMonitorWriteAccess(alert.attributes.monitor_type)}>
                    <Link to={ApiRoutes.ALERTS.MONITORS.DETAILS(alert.attributes.monitor_uuid)}>{alert.attributes.monitor_name}</Link>
                  </WithPermission>
                  <WithoutPermission permission={requiredUserPermissionForMonitorWriteAccess(alert.attributes.monitor_type)}>
                    {alert.attributes.monitor_name}
                  </WithoutPermission>
                </dd>
                <dt>Monitor Type</dt>
                <dd><MonitorType type={alert.attributes.monitor_type} /></dd>
                <dt>Trigger Condition</dt>
                <dd>More than {numeral(alert.attributes.trigger_condition).format("0,0")} {alert.attributes.trigger_condition === 1 ? "result" : "results"}</dd>

                <p className="mb-0 mt-3">
                  The monitor triggered because the search result count exceeded the configured trigger condition.
                </p>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Possible false-positives</h3>

              <p>
                These circumstances can lead to false-positive alerts of this type:
              </p>

              <ul className="mb-0">
                <li className="text-muted">n/a</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}