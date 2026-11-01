import React from "react";

function MonitorSecuritySuiteAlertDetails(props) {

  const alert = props.alert;

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Details</h3>
                <dl>
                  <dt>Observed Security Suites</dt>
                  <dd>{alert.attributes.suite}</dd>

                  <p className="mb-0 mt-2">
                    The network was observed to be using unanticipated wireless security suites. This could indicate a
                    potential attacker who may not be carefully adhering to the proper security configuration when
                    attempting to spoof.
                  </p>

                  <p className="mb-0 mt-3">
                    The 802.11 standard, commonly known as WiFi, has evolved its security protocols over the years.
                    Starting with the easily crackable WEP, it transitioned to WPA which introduced TKIP for better
                    encryption and dynamic keying. WPA2 then shifted to the more secure AES encryption in CCMP mode
                    and offered both Personal and Enterprise variants. The latest, WPA3, brings forward enhancements
                    like SAE for robust authentication, 192-bit security for critical networks, Enhanced Open for
                    public networks, and DPP for streamlined device onboarding.
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
                  <li>An authorized security configuration change of an access point might have been the cause. To
                    prevent false alerts, it's crucial to update the Nzyme network monitoring configuration when making
                    changes to monitored networks.</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default MonitorSecuritySuiteAlertDetails;