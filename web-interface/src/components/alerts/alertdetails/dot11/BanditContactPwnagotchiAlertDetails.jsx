import React from "react";

import numeral from "numeral";

function BanditContactPwnagotchiAlertDetails(props) {

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
                    {alert.attributes.bandit_name} <span className="text-muted">(Built-In Bandit)</span>
                  </dd>
                  <dt>Bandit Description</dt>
                  <dd>{alert.attributes.bandit_description}</dd>
                  <dt>Pwnagotchi Identity / UUID</dt>
                  <dd>{alert.attributes.identity}</dd>
                  <dt>Pwnagotchi Name</dt>
                  <dd>{alert.attributes.name}</dd>
                  <dt>Pwnagotchi <i>pwnd</i></dt>
                  <dd>{alert.attributes.pwnd_run} this run, {alert.attributes.pwnd_tot} total</dd>
                  <dt>Pwnagotchi Uptime</dt>
                  <dd>{numeral(alert.attributes.uptime).format("0,0")} seconds</dd>
                  <dt>Pwnagotchi Version</dt>
                  <dd>{alert.attributes.version}</dd>

                  <p className="mb-0 mt-3">
                    The Pwnagotchi WiFi attack platform was detected by Nzyme.
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
                  <li>n/a</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default BanditContactPwnagotchiAlertDetails;