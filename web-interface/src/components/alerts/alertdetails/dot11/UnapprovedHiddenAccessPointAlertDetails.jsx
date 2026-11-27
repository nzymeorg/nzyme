import React from "react";
import ApiRoutes from "../../../../util/ApiRoutes";
import Dot11MacAddress from "../../../shared/context/macs/Dot11MacAddress";
import PivotLink from "../../../shared/PivotLink";

export default function UnapprovedHiddenAccessPointAlertDetails(props) {

  const alert = props.alert;

  const hint = (ssids) => ssids ? ssids : <span className="text-muted">None</span>;

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <h3>Details</h3>
                <dl>
                  <dt>BSSID</dt>
                  <dd>
                    <Dot11MacAddress href={ApiRoutes.DOT11.NETWORKS.BSSIDS.DETAILS(alert.attributes.bssid)}
                                     address={alert.attributes.bssid}/>
                    <PivotLink to={ApiRoutes.DOT11.NETWORKS.BSSIDS.TIMELIME(alert.attributes.bssid)}
                               title="Timeline of this BSSID"
                               className="ms-1 small"
                               icon={true} />
                  </dd>

                  <dt>Likely Same Device As</dt>
                  <dd>{hint(alert.attributes.sibling_ssids)}</dd>

                  <dt>Revealed SSIDs</dt>
                  <dd>{hint(alert.attributes.revealed_ssids)}</dd>
                </dl>

                <p>
                  An unapproved access point that advertises without a network name (hidden SSID) was detected.
                </p>

                <p className="mb-0">
                  Both lists of SSIDs are hints. Addresses and SSIDs are chosen by their sender and can be made to
                  match yours.
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
                  <li>
                    Mesh systems, printers and other devices open a hidden network next to their named networks.
                    Named networks with a neighboring address are listed under &quot;Likely Same Device As&quot;.
                  </li>
                  <li>A new access point of a network with a hidden SSID was installed and has not been approved yet.</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}
