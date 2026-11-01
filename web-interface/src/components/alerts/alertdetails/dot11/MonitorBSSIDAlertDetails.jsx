import React from "react";

function MonitorBSSIDAlertDetails(props) {

  const alert = props.alert;

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Details</h3>
                <dl>
                  <dt>Observed BSSID</dt>
                  <dd>{alert.attributes.bssid}</dd>

                  <p className="mb-0 mt-3">
                    An access point with an unfamiliar BSSID (hardware address) is broadcasting one of our SSIDs
                    (network name). This might be a rogue access point attempting to entice users to connect by
                    mimicking a legitimate access point from a trusted wireless network. It's worth noting that adept
                    attackers are unlikely to trigger this alert, as they would emulate a legitimate access point by
                    transmitting frames with a spoofed BSSID.
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
                  <li>A new access point was installed and the Nzyme configuration has not been updated yet.</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default MonitorBSSIDAlertDetails;