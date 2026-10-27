import {Link} from "react-router-dom";
import React from 'react';
import AutomaticDot11MacAddressLink from "../../../shared/context/macs/AutomaticDot11MacAddressLink";
import {CLIENT} from "../../../shared/context/macs/Dot11MacAddressType";
import WithPermission from "../../../misc/WithPermission";
import WithoutPermission from "../../../misc/WithoutPermission";
import ApiRoutes from "../../../../util/ApiRoutes";

export default function MonitoredProbeRequestAlertDetails(props) {

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
                    <dd><Link to={ApiRoutes.DOT11.MONITORING.PROBE_REQUESTS.INDEX}>{alert.attributes.ssid}</Link></dd>
                  </WithPermission>

                  <WithoutPermission permission="dot11_monitoring_manage">
                    <dd>{alert.attributes.ssid}</dd>
                  </WithoutPermission>

                  <dt>Client MAC Address</dt>
                  <dd>
                    <AutomaticDot11MacAddressLink type={CLIENT} mac={alert.attributes.client_mac}/>
                  </dd>
                </dl>

                <p className="mb-0 mt-3">
                  A monitored probe request was detected.
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