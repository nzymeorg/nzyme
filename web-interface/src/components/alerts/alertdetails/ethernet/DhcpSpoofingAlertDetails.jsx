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

export default function DhcpSpoofingAlertDetails({alert}) {

  const a = alert.attributes;
  const rogue = alert.detection_type === "SPOOFING_DHCP_ROGUE_SERVER";

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
                  <Link to={ApiRoutes.ETHERNET.SPOOFING.DHCP.DETAILS(a.finding_uuid)}>
                    {rogue ? "Rogue DHCP server " : "Changed configuration from "}{a.server_address}
                  </Link>
                </dd>
                <dt>Server</dt>
                <dd><span className="machine-data">{a.server_address}</span> (<span className="machine-data">{a.server_mac}</span>)</dd>
                <dt>Gateway Handed Out</dt>
                <dd>{list(a.routers)}</dd>
                <dt>DNS Servers Handed Out</dt>
                <dd>{list(a.dns_servers)}</dd>
                {!rogue ?
                  <React.Fragment>
                    <dt>Expected Gateway</dt>
                    <dd>{list(a.expected_routers)}</dd>
                    <dt>Expected DNS Servers</dt>
                    <dd>{list(a.expected_dns_servers)}</dd>
                  </React.Fragment> : null}
                <dt>At Time of Alert</dt>
                <dd>
                  {numeral(a.transaction_count).format("0,0")} transactions,{' '}
                  {numeral(a.victim_count).format("0,0")} {a.victim_count === "1" ? "client" : "clients"} affected
                </dd>

                <p className="mb-0 mt-3">
                  {rogue ?
                    "A DHCP server that is not trusted answered clients on the network. Clients that accept its " +
                    "lease use the gateway and DNS servers it hands out, which lets whoever runs it read, change " +
                    "or redirect their traffic. Identify the device on the finding page and trust it there if it " +
                    "turns out to be legitimate."
                    : "A trusted DHCP server handed out a gateway, DNS server, domain or subnet mask that differs " +
                    "from what it handed out before. This is either an intended change or an attacker forging " +
                    "replies with the server's addresses. Check the finding page; accept the configuration " +
                    "there if the change was intended."}
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

              {rogue ?
                <ul className="mb-0">
                  <li>A new or replaced DHCP server, or a redundant server that was not seen during the learning
                    period. Trust it.</li>
                  <li>A DHCP server whose network card was replaced, which changes its MAC address.</li>
                  <li>Consumer routers, access points or virtual machines with a DHCP server that someone connected
                    to the network by mistake. These are not malicious, but they still hand out wrong
                    configurations and should be removed.</li>
                  <li>Lab or guest networks with their own DHCP servers that a tap can see. Add an exclusion.</li>
                </ul>
                : <ul className="mb-0">
                  <li>An intended change of the gateway, DNS servers or domain on the server. Accept the new
                    configuration on the finding page.</li>
                  <li>A server with several address pools or scopes that hand out different gateways, for example
                    across VLANs that a tap sees through a relay.</li>
                  <li>A DHCP relay that rewrites options.</li>
                </ul>}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
