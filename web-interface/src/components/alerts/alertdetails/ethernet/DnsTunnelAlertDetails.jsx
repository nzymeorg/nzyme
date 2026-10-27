import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";
import {indicatorToName} from "../../../ethernet/dns/tunnels/DnsTunnelIndicator";

const list = (csv, transform = (v) => v) => {
  if (!csv) {
    return <span className="text-muted">None</span>
  }

  return csv.split(",").map((v, i) => <span key={i}>{i > 0 ? ", " : ""}<span className="machine-data">{transform(v)}</span></span>);
}

export default function DnsTunnelAlertDetails({alert}) {

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
                  <Link to={ApiRoutes.ETHERNET.DNS.TUNNELS.DETAILS(a.finding_uuid)}>
                    DNS tunnel to {a.domain} from {a.client_address}
                  </Link>
                </dd>
                <dt>Client</dt>
                <dd>
                  <span className="machine-data">{a.client_address}</span>
                  {a.client_mac ? <span className="text-muted"> (<span className="machine-data">{a.client_mac}</span>)</span> : null}
                </dd>
                <dt>Domain</dt>
                <dd><span className="machine-data">{a.domain}</span></dd>
                <dt>Resolvers Used</dt>
                <dd>{list(a.server_addresses)}</dd>
                <dt>Indicators</dt>
                <dd>{list(a.indicators, indicatorToName)}</dd>
                <dt>Names</dt>
                <dd>
                  {numeral(a.distinct_name_count).format("0,0")} distinct, for example {list(a.names)}
                </dd>
                <dt>At Time of Alert</dt>
                <dd>
                  {numeral(a.query_count).format("0,0")} queries, {numeral(a.average_entropy).format("0.00")} bits per
                  character average entropy, {numeral(a.average_name_length).format("0.0")} characters average
                  length below the domain
                </dd>

                <p className="mb-0 mt-3">
                  A client looked up many distinct names under one registered domain, and the names look like
                  encoded data rather than hostnames. This is how a DNS tunnel moves data: the client encodes it
                  in the names it asks for, the authoritative server of the domain decodes it and answers with
                  data of its own. It passes through any firewall that allows DNS. Find the client and the names
                  on the finding page.
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
                <li>Antivirus and reputation services that look up file hashes or URLs as DNS names, and DNS
                  blocklists that encode addresses in names.</li>
                <li>Content delivery networks and cloud services that use long random hostnames.</li>
                <li>Service discovery and certificate validation that use TXT and SRV records heavily.</li>
                <li>Software that generates random subdomains to defeat caching. Add an exclusion for the domain
                  when the service is legitimate.</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
