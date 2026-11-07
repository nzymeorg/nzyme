import React from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";

export default function AssetNewAlertDetails({alert}) {

  const a = alert.attributes;

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>
              <dl>
                <dt>Asset</dt>
                <dd>
                  {a.asset_uuid ?
                    <Link to={ApiRoutes.ETHERNET.ASSETS.DETAILS(a.asset_uuid)}>Asset {a.mac}</Link>
                    : <span className="machine-data">{a.mac}</span>}
                </dd>
                <dt>MAC Address</dt>
                <dd><span className="machine-data">{a.mac}</span></dd>

                <p className="mb-0 mt-3">
                  A MAC address that Nzyme had not seen before started communicating on the network. Every device
                  shows up like this once. Open the asset page to see what it does, which hostnames it uses and
                  which TCP stack it has, then decide whether it belongs on the network.
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
                <li>Devices that randomize their MAC address, like phones and laptops on Wi-Fi. Each new address
                  is a new asset. The MAC address changes page links such an address to the device it belongs to.</li>
                <li>A tap that was just deployed or moved to a new network segment sees every existing device as
                  new.</li>
                <li>Guest devices and visitors that connect for the first time.</li>
                <li>A network card that was replaced, or a device that moved from Wi-Fi to a wired port.</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
