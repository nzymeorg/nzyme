import React from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import BeaconType from "../../../ethernet/beacons/BeaconType";
import BeaconClassification from "../../../ethernet/beacons/BeaconClassification";

export default function BeaconAlertDetails({alert}) {

  const explanation = () => {
    switch (alert.detection_type) {
      case "BEACON_NEW":
        return "A device started calling out to a new destination in regular intervals. Review the beacon, " +
          "identify the software behind it and classify it.";
      case "BEACON_NEW_SOURCE":
        return "An additional device started calling out to the destination of an already known beacon.";
      case "BEACON_ACTIVITY_SUSPICIOUS":
        return "A beacon that an operator classified as suspicious recorded new check-ins.";
      case "BEACON_ACTIVITY_MALICIOUS":
        return "A beacon that an operator classified as malicious recorded new check-ins. Devices calling out " +
          "to this destination are likely compromised.";
      default:
        return null;
    }
  }

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>
              <dl>
                <dt>Beacon</dt>
                <dd>
                  <Link to={ApiRoutes.ETHERNET.BEACONS.DETAILS(alert.attributes.beacon_uuid)}>
                    {alert.attributes.beacon_name}
                  </Link>
                </dd>
                <dt>Type</dt>
                <dd><BeaconType type={alert.attributes.beacon_type} /></dd>
                <dt>Destination</dt>
                <dd><span className="machine-data">{alert.attributes.destination}</span></dd>
                <dt>Classification at Time of Alert</dt>
                <dd><BeaconClassification classification={alert.attributes.classification} /></dd>
                {alert.attributes.source_address ?
                  <React.Fragment>
                    <dt>Device</dt>
                    <dd>
                      <span className="machine-data">{alert.attributes.source_address}</span>
                      {alert.attributes.source_mac ? <span className="text-muted machine-data"> ({alert.attributes.source_mac})</span> : null}
                    </dd>
                  </React.Fragment> : null}

                <p className="mb-0 mt-3">{explanation()}</p>
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
                <li>Legitimate software checking for updates, syncing or reporting telemetry in regular intervals.</li>
                <li>Monitoring agents, backup clients and other automation polling a server.</li>
                <li>Mail clients polling for new messages.</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
