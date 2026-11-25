import React from "react";
import numeral from "numeral";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import {dot11FrequencyToChannel} from "../../../../util/Tools";

function MonitorMultipleChannelsAlertDetails(props) {

  const attributes = props.alert.attributes;

  const frequencies = (attributes.frequencies ? attributes.frequencies.split(",") : [])
      .map((frequency) => parseInt(frequency, 10))
      .filter((frequency) => frequency);

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Details</h3>
                <dl>
                  <dt>Affected BSSID</dt>
                  <dd>
                    <Link to={ApiRoutes.DOT11.NETWORKS.BSSIDS.DETAILS(attributes.bssid)}>{attributes.bssid}</Link>
                  </dd>
                  <dt>Channels</dt>
                  <dd>
                    {frequencies.length === 0 ? <span className="text-muted">Unknown</span>
                        : frequencies.map((frequency) => {
                          return dot11FrequencyToChannel(frequency) + " (" + numeral(frequency).format("0,0") + " MHz)"
                        }).join(", ")}
                  </dd>
                </dl>

                <p className="mb-0 mt-2">
                  Beacons of a monitored access point keep naming two channels as their own. An access point is on
                  one channel at a time, so a second transmitter is using its BSSID.
                </p>
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
                  <li>Two or more access points use the same BSSID.</li>
                  <li>An access point changes between two channels several times within minutes.</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default MonitorMultipleChannelsAlertDetails;
