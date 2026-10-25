import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";
import ReconScanType from "../../../ethernet/recon/ReconScanType";

export default function ReconScanAlertDetails({alert}) {

  const a = alert.attributes;

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-md-6">
          <div className="card">
            <div className="card-body">
              <h3>Details</h3>
              <dl>
                <dt>Scan</dt>
                <dd>
                  <Link to={a.scan_type === "OS_FINGERPRINTING"
                    ? ApiRoutes.ETHERNET.RECON.FINGERPRINTING.DETAILS(a.scan_uuid)
                    : ApiRoutes.ETHERNET.RECON.SCANS.DETAILS(a.scan_uuid)}>
                    <ReconScanType type={a.scan_type} /> from {a.source}
                  </Link>
                </dd>
                <dt>Scanning Device</dt>
                <dd>
                  <span className="machine-data">{a.source}</span>
                  {a.source_mac && a.source_mac !== a.source ?
                    <span className="text-muted machine-data"> ({a.source_mac})</span> : null}
                </dd>
                <dt>Target</dt>
                <dd><span className="machine-data">{a.target}</span></dd>
                <dt>Probes at Time of Alert</dt>
                <dd>
                  {numeral(a.probe_count).format("0,0")} probes against{' '}
                  {numeral(a.host_count).format("0,0")} {a.host_count === "1" ? "host" : "hosts"}
                  {a.scan_type !== "ARP_SWEEP" ? <span> and {numeral(a.port_count).format("0,0")} ports</span> : null},{' '}
                  {numeral(a.open_count).format("0,0")} answered
                </dd>

                <p className="mb-0 mt-3">
                  {alert.detection_type === "RECON_FINGERPRINTING_NEW" ?
                    "A device sent probes that only operating system detection tools send. This usually follows a " +
                    "port scan and means someone is identifying what the target runs. Identify the device and decide " +
                    "whether this was expected."
                    : "A device started probing your network. Review which targets answered, identify the device and " +
                    "decide whether the scan was expected. Exclude the device if it probes the network on purpose."}
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
                <li>Vulnerability scanners, asset discovery and monitoring systems probing the network on purpose.</li>
                <li>Peer-to-peer software like BitTorrent contacting many peers that do not answer.</li>
                <li>Gateways and servers resolving many addresses with ARP after a restart.</li>
                <li>Misconfigured software retrying connections against hosts that no longer exist.</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
