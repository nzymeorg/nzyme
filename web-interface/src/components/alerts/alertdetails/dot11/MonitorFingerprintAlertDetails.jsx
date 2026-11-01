import React from "react";

function MonitorFingerprintAlertDetails(props) {

  const alert = props.alert;

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Details</h3>
                <dl>
                  <dt>Affected BSSID</dt>
                  <dd>{alert.attributes.bssid}</dd>
                  <dt>Observed Fingerprint</dt>
                  <dd>{alert.attributes.fingerprint}</dd>

                  <p className="mb-0 mt-3">
                    The network is being broadcast with a fingerprint not found in the list of expected fingerprints.
                    This might suggest a potential attacker is mimicking your network.
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
                  <li>A legitimate change of the access point configuration took place and the Nzyme configuration has
                    not been updated.</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default MonitorFingerprintAlertDetails;