import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";

export default function ArpSpoofingAlertDetails({alert}) {

  const a = alert.attributes;

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>
              <dl>
                <dt>Finding</dt>
                <dd>
                  <Link to={ApiRoutes.ETHERNET.SPOOFING.ARP.DETAILS(a.finding_uuid)}>
                    ARP poisoning of {a.address} by {a.spoofer_mac}
                  </Link>
                </dd>
                <dt>Poisoned Address</dt>
                <dd><span className="machine-data">{a.address}</span></dd>
                <dt>Spoofer</dt>
                <dd><span className="machine-data">{a.spoofer_mac}</span></dd>
                <dt>Legitimate Owner</dt>
                <dd><span className="machine-data">{a.owner_mac}</span></dd>
                <dt>At Time of Alert</dt>
                <dd>
                  {numeral(a.claim_count).format("0,0")} claims, {numeral(a.unsolicited_count).format("0,0")} unsolicited
                  replies, {numeral(a.victim_count).format("0,0")} {a.victim_count === "1" ? "device" : "devices"} affected
                </dd>

                <p className="mb-0 mt-3">
                  A device claimed an address with its own MAC address while the legitimate owner of the address
                  was still claiming it too. Devices that received the spoofer&apos;s replies may now send their
                  traffic for the address to the spoofer, which allows it to read or change that traffic. Identify
                  the spoofing device and the affected devices on the finding page.
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
                <li>Router redundancy protocols like VRRP, HSRP or CARP, when both the virtual and a physical MAC
                  address answer for the shared address.</li>
                <li>Failover clusters and load balancers that take over an address on purpose.</li>
                <li>Virtual machines or containers migrating between hosts while the old host still answers.</li>
                <li>A device that was cloned or misconfigured with an address that is still in use.</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
