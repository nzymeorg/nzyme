import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";
import {nameResolutionProtocolToName} from "../../../ethernet/spoofing/NameResolutionProtocol";

const list = (csv) => {
  if (!csv) {
    return <span className="text-muted">None</span>
  }

  return csv.split(",").map((v, i) => <span key={i}>{i > 0 ? ", " : ""}<span className="machine-data">{v}</span></span>);
}

export default function NameResolutionSpoofingAlertDetails({alert}) {

  const a = alert.attributes;
  const protocols = a.protocols ? a.protocols.split(",").map(nameResolutionProtocolToName).join(", ") : "";

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
                  <Link to={ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.DETAILS(a.finding_uuid)}>
                    Name resolution poisoning by {a.responder_mac}
                  </Link>
                </dd>
                <dt>Responder</dt>
                <dd><span className="machine-data">{a.responder_mac}</span> ({list(a.responder_addresses)})</dd>
                <dt>Protocols</dt>
                <dd>{protocols}</dd>
                <dt>Names Answered</dt>
                <dd>
                  {numeral(a.name_count).format("0,0")}, for example {list(a.names)}
                </dd>
                <dt>At Time of Alert</dt>
                <dd>
                  {numeral(a.response_count).format("0,0")} answers,{' '}
                  {numeral(a.victim_count).format("0,0")} {a.victim_count === "1" ? "client" : "clients"} affected
                </dd>

                <p className="mb-0 mt-3">
                  A device answered name queries of other devices for names that are not its own, pointing them at
                  its own address. Clients that take the answer connect to it believing it is the host they looked
                  for, and Windows clients authenticate to it, which exposes the credentials of their users. Find
                  the device on the finding page and check the clients listed as victims.
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
                <li>A device that legitimately carries several hostnames, for example a server with aliases or a
                  device that changed its hostname several times in a row. Raise the names threshold of the
                  protocol or add an exclusion for the device.</li>
                <li>mDNS gateways and reflectors that repeat the answers of other devices. They usually point names
                  at many different addresses and stay below the threshold, but a reflector in front of a single
                  busy device may not.</li>
                <li>Virtualization hosts and containers that answer for their guests with the host&apos;s address.</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
