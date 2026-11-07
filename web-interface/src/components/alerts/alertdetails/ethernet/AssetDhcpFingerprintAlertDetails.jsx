import React, {useContext} from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import {UserContext} from "../../../../App";
import {userHasPermission} from "../../../../util/Tools";

export default function AssetDhcpFingerprintAlertDetails({alert}) {

  const a = alert.attributes;

  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "ethernet_assets_manage");

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
                <dt>Transaction Type</dt>
                <dd>{a.transaction_type}</dd>
                <dt>Known Fingerprint</dt>
                <dd><span className="machine-data">{a.existing_fingerprint}</span></dd>
                <dt>New Fingerprint</dt>
                <dd><span className="machine-data">{a.new_fingerprint}</span></dd>
                {canManage ?
                  <React.Fragment>
                    <dt>Exclusions</dt>
                    <dd>
                      <Link to={ApiRoutes.ETHERNET.ASSETS.DHCP.EXCLUSIONS.CREATE + "?mac=" + a.mac}>
                        Exclude this asset from DHCP fingerprint alerts
                      </Link>
                    </dd>
                  </React.Fragment> : null}

                <p className="mb-0 mt-3">
                  The device requested a DHCP lease with a different set of options than before. The options a
                  DHCP client sends are characteristic of its operating system, so a new fingerprint usually means
                  a different operating system is now using the MAC address: a reinstalled machine, a dual-boot
                  system that booted the other system, or another device that took over the address.
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
                <li>Dual-boot machines, which present one fingerprint per operating system. Exclude the asset.</li>
                <li>Operating system upgrades that change the DHCP client, and devices in PXE or recovery boot,
                  whose firmware sends its own DHCP requests before the operating system takes over.</li>
                <li>Virtual machine hosts and docking stations whose MAC address is shared by changing guests or
                  laptops.</li>
                <li>Clients that send different options for different transaction types, for example when
                  renewing a lease.</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
