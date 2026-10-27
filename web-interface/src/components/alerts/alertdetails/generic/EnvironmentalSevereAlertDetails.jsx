import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../util/ApiRoutes";
import moment from "moment";

export default function EnvironmentalSevereAlertDetails({ alert }) {

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>
              <dl>
                <dt>Headline</dt>
                <dd>{alert.attributes.headline}</dd>
                <dt>Location</dt>
                <dd>
                  <Link to={ApiRoutes.LOCATIONS.DETAILS(alert.attributes.location_uuid)}>
                    {alert.attributes.location_name}
                  </Link>
                </dd>
                <dt>Sender</dt>
                <dd>{alert.attributes.sender}</dd>
                <dt>Severity</dt>
                <dd>{alert.attributes.severity}</dd>
                <dt>Urgency</dt>
                <dd>{alert.attributes.urgency}</dd>
                <dt>Certainty</dt>
                <dd>{alert.attributes.certainty}</dd>
                <dt>Effective</dt>
                <dd>{alert.attributes.effective === "Unknown" ? "Unknown" : moment(alert.attributes.effective).format()}</dd>
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
                <li className="text-muted">Inaccurate weather forecasting</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}