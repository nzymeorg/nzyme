import React from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import {DISCO_ANOMALY_TYPES} from "../../../dot11/disco/anomalies/DiscoAnomalyTypes";
import PivotLink from "../../../shared/PivotLink";
import {
  discoAnomaliesLink,
  discoAnomalySignalTimeRange,
  discoSignalLink,
  isAnomalyOfDisconnectionFrames
} from "../../../dot11/disco/signal/DiscoSignalLinks";

// Alerts of a recorded anomaly carry its type. Alerts of the frame count threshold of a monitored network do not.
function MonitorDiscoAnomalyAlertDetails(props) {

  const alert = props.alert;
  const attributes = alert.attributes;

  if (attributes.anomaly_type) {
    const type = DISCO_ANOMALY_TYPES[attributes.anomaly_type];
    const timeRange = discoAnomalySignalTimeRange(alert.created_at, alert.last_seen);

    return (
        <React.Fragment>
          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <h3>Details</h3>
                  <dl>
                    <dt>Anomaly</dt>
                    <dd>
                      <Link to={ApiRoutes.DOT11.DISCO.ANOMALIES.DETAILS(attributes.anomaly_uuid)}>
                        {type ? type.name : attributes.anomaly_title}
                      </Link>
                    </dd>
                    <dt>Affected BSSID</dt>
                    <dd>
                      <Link to={ApiRoutes.DOT11.NETWORKS.BSSIDS.DETAILS(attributes.bssid)}>{attributes.bssid}</Link>
                    </dd>
                    <dt>Client</dt>
                    <dd>
                      {attributes.client ?
                          <Link to={ApiRoutes.DOT11.CLIENTS.DETAILS(attributes.client)}>{attributes.client}</Link>
                          : <span className="text-muted">All Clients</span>}
                    </dd>
                    <dt>Recorded by Tap</dt>
                    <dd>{attributes.tap_name}</dd>
                    <dt>Anomalies of This Type for This BSSID</dt>
                    <dd>
                      <PivotLink to={discoAnomaliesLink(attributes.bssid, attributes.anomaly_type, timeRange)}>
                        Anomalies
                      </PivotLink>
                    </dd>
                    {isAnomalyOfDisconnectionFrames(attributes.anomaly_type) ?
                        <React.Fragment>
                          <dt>Signal Strength of the Frames</dt>
                          <dd>
                            <PivotLink to={discoSignalLink(
                                attributes.client ? [attributes.bssid, attributes.client] : [attributes.bssid], timeRange)}>
                              Signal Strength Investigation
                            </PivotLink>
                          </dd>
                        </React.Fragment> : null}
                  </dl>

                  {type ? <p className="mb-0 mt-2">{type.meaning}</p> : null}

                  <p className="mb-0 mt-2">
                    The anomaly above raised this alert last.
                  </p>
                </div>
              </div>
            </div>

            {type ?
                <div className="col-md-6">
                  <div className="card">
                    <div className="card-body">
                      <h3>Possible false-positives</h3>

                      <p>
                        These circumstances can lead to false-positive alerts of this type:
                      </p>

                      <ul className="mb-0">
                        {type.falsePositives.map((falsePositive, i) => <li key={i}>{falsePositive}</li>)}
                      </ul>
                    </div>
                  </div>
                </div> : null}
          </div>
        </React.Fragment>
    )
  }

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Details</h3>
                <dl>
                  <dt>Recorded by Tap</dt>
                  <dd>{alert.attributes.tap_name}</dd>

                  <p className="mb-0 mt-2">
                    One of the monitored access points experienced an unusually high number of
                    deauthentication/disassociation events. Such events are commonly associated with malicious WiFi
                    attacks, indicating a potential ongoing threat.
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
                  <li>The deauthentication anomaly detection method for the monitored network may not be selected or
                  configured optimally, resulting in mis-identification of anomalies.</li>
                  <li>A sudden increase in WiFi clients can lead to an increase in legitimate deauthentication traffic
                  that could be mis-identified as an anomaly.</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default MonitorDiscoAnomalyAlertDetails;
