import React from "react";

function BanditContactAlertDetails(props) {

  const alert = props.alert;

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Details</h3>
                <dl>
                  <dt>Bandit Name</dt>
                  <dd>
                    {alert.attributes.bandit_name}{' '}
                    <span className="text-muted">
                      ({ (alert.attributes.bandit_is_custom === "true") ? "Custom Bandit" : "Built-In Bandit" })
                    </span>
                  </dd>
                  <dt>Bandit Description</dt>
                  <dd>{alert.attributes.bandit_description}</dd>
                  <dt>BSSID</dt>
                  <dd>{alert.attributes.bssid}</dd>
                  <dt>Fingerprint</dt>
                  <dd>{alert.attributes.fingerprint}</dd>

                  <p className="mb-0 mt-3">
                    A known WiFi attack platform ("Bandit") was detected by Nzyme.
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
                  <li>A mis-configured or too broad bandit definition could trigger an invalid contact.</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default BanditContactAlertDetails;