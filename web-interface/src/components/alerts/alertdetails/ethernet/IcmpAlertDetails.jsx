import React from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";

export default function IcmpAlertDetails({alert}) {

  const a = alert.attributes;
  const type = alert.detection_type;

  const details = () => {
    if (type === "ICMP_REDIRECT") {
      return (
        <React.Fragment>
          <dt>Sender</dt>
          <dd>
            <span className="machine-data">{a.sender_address}</span>
            {a.sender_mac ? <span className="text-muted machine-data"> ({a.sender_mac})</span> : null}
          </dd>
          <dt>Told Host</dt>
          <dd><span className="machine-data">{a.target_address}</span></dd>
          <dt>To Use Gateway</dt>
          <dd><span className="machine-data">{a.gateway_address}</span></dd>
          <dt>Findings</dt>
          <dd><Link to={ApiRoutes.ETHERNET.ICMP.REDIRECTS.INDEX}>Detected ICMP redirects</Link></dd>
        </React.Fragment>
      )
    }

    if (type === "ICMP_TUNNEL") {
      return (
        <React.Fragment>
          <dt>Source</dt>
          <dd>
            <span className="machine-data">{a.source_address}</span>
            {a.source_mac ? <span className="text-muted machine-data"> ({a.source_mac})</span> : null}
          </dd>
          <dt>Peer</dt>
          <dd><span className="machine-data">{a.destination_address}</span></dd>
          <dt>Max Echo Payload</dt>
          <dd>{a.max_payload_bytes} bytes</dd>
          <dt>Findings</dt>
          <dd><Link to={ApiRoutes.ETHERNET.ICMP.TUNNELS.INDEX}>Detected ICMP tunnels</Link></dd>
        </React.Fragment>
      )
    }

    return (
      <React.Fragment>
        <dt>Tracing Host</dt>
        <dd>
          <span className="machine-data">{a.source_address}</span>
          {a.source_mac ? <span className="text-muted machine-data"> ({a.source_mac})</span> : null}
        </dd>
        <dt>Destination</dt>
        <dd><span className="machine-data">{a.destination_address}</span></dd>
        <dt>Routers Answering</dt>
        <dd>{a.router_count}</dd>
        <dt>Findings</dt>
        <dd><Link to={ApiRoutes.ETHERNET.ICMP.TRACEROUTES.INDEX}>Detected traceroutes</Link></dd>
      </React.Fragment>
    )
  }

  const explanation = () => {
    if (type === "ICMP_REDIRECT") {
      return "A device told another host to send traffic through a different gateway. On most networks nothing " +
        "does this legitimately. If the sender is not one of your routers, it is likely trying to intercept traffic.";
    }
    if (type === "ICMP_TUNNEL") {
      return "A pair of hosts exchanged ICMP echo traffic with unusually large or sustained payloads. Tools like " +
        "Loki, icmpsh and ptunnel use this to carry a covert channel for command-and-control or exfiltration " +
        "inside ping traffic.";
    }
    return "A host mapped the path to a destination. Administrators do this routinely, attackers do it to " +
      "understand the network layout after gaining a foothold.";
  }

  const falsePositives = () => {
    if (type === "ICMP_REDIRECT") {
      return (
        <React.Fragment>
          <li>A network segment with two routers where the default gateway points hosts at the other router.</li>
          <li>Misconfigured hosts acting as routers with ICMP redirects enabled.</li>
        </React.Fragment>
      )
    }
    if (type === "ICMP_TUNNEL") {
      return (
        <React.Fragment>
          <li>Large one-off pings or path MTU testing, for example ping with a big packet size.</li>
          <li>Monitoring tools that send sized ping payloads for latency or jitter measurement.</li>
        </React.Fragment>
      )
    }
    return (
      <React.Fragment>
        <li>Administrators and monitoring systems running traceroute or path MTU discovery tools.</li>
        <li>Applications that measure network paths, like some VPN and video conferencing clients.</li>
      </React.Fragment>
    )
  }

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>
              <dl>
                {details()}
                <p className="mb-0 mt-3">{explanation()}</p>
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
                {falsePositives()}
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
