import React from "react";

import numeral from "numeral";
import {dot11FrequencyToChannel} from "../../../../util/Tools";

function MonitorChannelAlertDetails(props) {

  const alert = props.alert;

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Details</h3>
                <dl>
                  <dt>Observed Channel</dt>
                  <dd>
                    {dot11FrequencyToChannel(alert.attributes.frequency)}{' '}
                    ({numeral(alert.attributes.frequency).format("0,0")} MHz)
                  </dd>

                  <p className="mb-0 mt-3">
                    The network was advertised on a channel not present in the list of expected channels. This might
                    suggest a potential attacker is being inattentive and isn't restricting spoofing exclusively to channels
                    used by legitimate access points.
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
                  <li>The access point configuration was legitimately altered, and the Nzyme network monitoring
                    configuration hasn't been updated accordingly.</li>
                  <li>Many access points may automatically select new channels based on RF spectrum congestion. Ensure
                    all potential channels are included in the Nzyme network monitoring configuration.</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default MonitorChannelAlertDetails;