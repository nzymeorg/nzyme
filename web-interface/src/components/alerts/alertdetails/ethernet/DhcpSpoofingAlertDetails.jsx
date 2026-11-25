import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";
import {dhcpSpoofingServerNoun} from "../../../ethernet/spoofing/DhcpSpoofingProtocol";

const list = (csv) => {
  if (!csv) {
    return <span className="text-muted">None</span>
  }

  return csv.split(",").map((v, i) => <span key={i}>{i > 0 ? ", " : ""}<span className="machine-data">{v.trim()}</span></span>);
}

export default function DhcpSpoofingAlertDetails({alert}) {

  const a = alert.attributes;
  const router = a.protocol === "RA";
  const dhcp6 = a.protocol === "DHCP6";
  const rogue = alert.detection_type === "SPOOFING_DHCP_ROGUE_SERVER" || alert.detection_type === "SPOOFING_RA_ROGUE_ROUTER";
  const noun = dhcpSpoofingServerNoun(a.protocol);

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
                    {rogue ? "Rogue " + noun + " " : "Changed configuration from "}{a.server_address}
                  </Link>
                </dd>
                <dt>{router ? "Router" : "Server"}</dt>
                <dd><span className="machine-data">{a.server_address}</span> (<span className="machine-data">{a.server_mac}</span>)</dd>
                {router ?
                  <React.Fragment>
                    <dt>Prefixes Advertised</dt>
                    <dd>{list(a.prefixes)}</dd>
                    <dt>Address Configuration and Role</dt>
                    <dd>{a.flags ? a.flags : <span className="text-muted">Unknown</span>}</dd>
                  </React.Fragment>
                  : dhcp6 ? null : <React.Fragment>
                    <dt>Gateway Handed Out</dt>
                    <dd>{list(a.routers)}</dd>
                  </React.Fragment>}
                <dt>DNS Servers Handed Out</dt>
                <dd>{list(a.dns_servers)}</dd>
                {!rogue && router ?
                  <React.Fragment>
                    <dt>Expected Prefixes</dt>
                    <dd>{list(a.expected_prefixes)}</dd>
                    <dt>Expected Address Configuration and Role</dt>
                    <dd>{a.expected_flags ? a.expected_flags : <span className="text-muted">Unknown</span>}</dd>
                  </React.Fragment> : null}
                {!rogue && !router && !dhcp6 ?
                  <React.Fragment>
                    <dt>Expected Gateway</dt>
                    <dd>{list(a.expected_routers)}</dd>
                  </React.Fragment> : null}
                {!rogue ?
                  <React.Fragment>
                    <dt>Expected DNS Servers</dt>
                    <dd>{list(a.expected_dns_servers)}</dd>
                  </React.Fragment> : null}
                <dt>At Time of Alert</dt>
                <dd>
                  {router ?
                    numeral(a.transaction_count).format("0,0") + " router advertisements"
                    : <React.Fragment>
                      {numeral(a.transaction_count).format("0,0")} transactions,{' '}
                      {numeral(a.victim_count).format("0,0")} {a.victim_count === "1" ? "client" : "clients"} affected
                    </React.Fragment>}
                </dd>

                <p className="mb-0 mt-3">
                  {router ?
                    (rogue ?
                      "A device that is not a trusted router sent IPv6 router advertisements. Hosts that accept " +
                      "them configure addresses from its prefixes and may use it as default router and its DNS " +
                      "servers, which lets whoever runs it read, change or redirect their traffic. This also works " +
                      "on networks that only use IPv4, because most hosts have IPv6 enabled. Identify the device " +
                      "on the finding page and trust it there if it turns out to be legitimate."
                      : "A trusted IPv6 router advertised prefixes, DNS servers, a lifetime or flags that differ " +
                      "from what it advertised before. This is either an intended change or an attacker forging " +
                      "advertisements with the router's addresses, for example to take it out of service as " +
                      "default router. Check the finding page and accept the configuration there if the change " +
                      "was intended.")
                    : (rogue ?
                      "A " + noun + " that is not trusted answered clients on the network. Clients that accept its " +
                      "lease use the " + (a.protocol === "DHCP6" ? "" : "gateway and ") + "DNS servers it hands out, " +
                      "which lets whoever runs it read, change or redirect their traffic. Identify the device on " +
                      "the finding page and trust it there if it turns out to be legitimate."
                      : "A trusted " + noun + " handed out a " + (a.protocol === "DHCP6" ? "DNS server or domain"
                        : "gateway, DNS server, domain or subnet mask") + " that differs " +
                      "from what it handed out before. This is either an intended change or an attacker forging " +
                      "replies with the server's addresses. Check the finding page and accept the configuration " +
                      "there if the change was intended.")}
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

              {router ?
                (rogue ?
                  <ul className="mb-0">
                    <li>A new or replaced router, or a redundant router that was not seen during the learning
                      period. Trust it.</li>
                    <li>A router whose network card was replaced, which changes its MAC address.</li>
                    <li>Devices that share their Internet connection, like phones with tethering, Windows Internet
                      Connection Sharing or virtualization hosts. These are not malicious, but hosts on the network
                      follow their advertisements and they should be removed.</li>
                    <li>Lab or guest networks with their own routers that a tap can see. Add an exclusion.</li>
                  </ul>
                  : <ul className="mb-0">
                    <li>An Internet provider that assigned a new prefix, which the router then advertises. Accept
                      the new configuration on the finding page.</li>
                    <li>An intended change of prefixes, DNS servers or flags on the router.</li>
                    <li>A router that is shutting down or restarting and announces a lifetime of zero.</li>
                  </ul>)
                : rogue ?
                <ul className="mb-0">
                  <li>A new or replaced {noun}, or a redundant server that was not seen during the learning
                    period. Trust it.</li>
                  <li>A {noun} whose network card was replaced, which changes its MAC address.</li>
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
