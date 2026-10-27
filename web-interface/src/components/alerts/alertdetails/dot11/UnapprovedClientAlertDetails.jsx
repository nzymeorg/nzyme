import {Link} from "react-router-dom";
import React from "react";
import WithPermission from "../../../misc/WithPermission";
import ApiRoutes from "../../../../util/ApiRoutes";
import WithoutPermission from "../../../misc/WithoutPermission";
import Dot11MacAddress from "../../../shared/context/macs/Dot11MacAddress";

export default function UnapprovedClientAlertDetails(props) {

  const alert = props.alert;

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>
              <dl>
                <dt>SSID</dt>

                <WithPermission permission="dot11_monitoring_manage">
                  <dd>
                    <Link to={ApiRoutes.DOT11.MONITORING.SSID_DETAILS(alert.dot11_monitored_network_id)}>
                      {alert.attributes.monitored_network}
                    </Link>
                  </dd>
                </WithPermission>

                <WithoutPermission permission="dot11_monitoring_manage">
                  <dd>{alert.attributes.monitored_network}</dd>
                </WithoutPermission>

                <dt>Client MAC Address</dt>
                <dd><Dot11MacAddress type="CLIENT"
                                     href={ApiRoutes.DOT11.CLIENTS.DETAILS(alert.attributes.client_mac)}
                                     address={alert.attributes.client_mac}/></dd>
              </dl>

              <p className="mb-0">
                An unapproved client connected to your monitored
                network &quot;{alert.attributes.monitored_network}&quot;
              </p>
            </div>
          </div>
        </div>

        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Possible false-positives</h3>

              <p className="text-muted">
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