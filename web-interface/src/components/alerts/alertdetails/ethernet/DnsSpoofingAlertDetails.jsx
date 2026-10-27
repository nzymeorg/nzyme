import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";

const list = (csv) => {
  if (!csv) {
    return <span className="text-muted">None</span>
  }

  return csv.split(",").map((v, i) => <span key={i}>{i > 0 ? ", " : ""}<span className="machine-data">{v}</span></span>);
}

export default function DnsSpoofingAlertDetails({alert}) {

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
                  <Link to={ApiRoutes.ETHERNET.SPOOFING.DNS.DETAILS(a.finding_uuid)}>
                    DNS spoofing of {a.server_address} by {a.spoofer_mac}
                  </Link>
                </dd>
                <dt>Impersonated Resolver</dt>
                <dd><span className="machine-data">{a.server_address}</span></dd>
                <dt>Spoofer</dt>
                <dd><span className="machine-data">{a.spoofer_mac}</span></dd>
                <dt>Legitimate Owner</dt>
                <dd><span className="machine-data">{a.owner_mac}</span></dd>
                <dt>Names Spoofed</dt>
                <dd>
                  {numeral(a.name_count).format("0,0")}, for example {list(a.names)}
                </dd>
                <dt>Pointed At</dt>
                <dd>{list(a.answers)}</dd>
                <dt>At Time of Alert</dt>
                <dd>
                  {numeral(a.forged_count).format("0,0")} forged replies, {numeral(a.won_count).format("0,0")} arrived
                  before the real reply, {numeral(a.victim_count).format("0,0")} {a.victim_count === "1" ? "client" : "clients"} affected
                </dd>

                <p className="mb-0 mt-3">
                  A device answered DNS queries that clients had sent to their resolver, forging the resolver&apos;s
                  address as source, while the real replies were still arriving from the device that normally
                  delivers them. A client uses the first reply it gets, so clients that received a forged reply
                  first were sent to an address of the spoofer&apos;s choosing. Find the spoofing device and the
                  affected clients on the finding page.
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
                <li>Router redundancy protocols like VRRP, HSRP or CARP in front of the resolver, when replies
                  come from both the virtual and a physical MAC address.</li>
                <li>Load balancers with direct server return, where queries go to the balancer and replies come
                  straight from a backend.</li>
                <li>A resolver or gateway that was replaced or failed over while the old device was still
                  answering.</li>
                <li>Split DNS setups where two devices legitimately answer from the same address on the same
                  segment. Add an exclusion for the resolver address and the second device.</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
