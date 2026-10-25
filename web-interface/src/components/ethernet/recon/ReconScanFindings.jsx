import React from "react";
import moment from "moment";
import numeral from "numeral";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import IPAddressLink from "../shared/IPAddressLink";

export default function ReconScanFindings({scan, summary}) {

  if (!summary) {
    return <GenericWidgetLoadingSpinner height={120} />;
  }

  const stealthProbes = scan.enrichment && scan.enrichment.stealth_probe_count ? scan.enrichment.stealth_probe_count : 0;
  const stealthNote = stealthProbes > 0 ?
    <p className="text-muted mb-2">
      {numeral(stealthProbes).format("0,0")} of the probes were stealth probes (NULL, FIN, Xmas or crafted ACK
      segments). Closed ports answer those with a reset and open ports stay silent, so for this scan the silent
      ports are the finding.
    </p> : null;

  // A stealth port scan learns from silence: the ports that sent no reset are open or filtered.
  if (scan.is_port_scan && stealthProbes > 0 && stealthProbes >= scan.probe_count) {
    const ports = summary.unanswered_ports || [];
    const total = summary.unanswered_ports_total || 0;

    if (ports.length === 0) {
      return (
        <React.Fragment>
          {stealthNote}
          <div className="alert alert-success mb-0">
            Every port answered with a reset. The scanner learned that all probed ports are closed.
          </div>
        </React.Fragment>
      )
    }

    return (
      <React.Fragment>
        {stealthNote}
        <p>
          <strong>{numeral(total).format("0,0")}</strong> {total === 1 ? "port" : "ports"} stayed silent
          {scan.target_address ? <span> on <span className="machine-data">{scan.target_address}</span></span> : null}.
          The scanner now knows these ports are open or filtered:
        </p>

        <div style={{maxHeight: 260, overflowY: "auto"}}>
          <table className="table table-sm table-hover table-striped mb-0">
            <thead>
            <tr>
              <th>Port</th>
              <th>Probes</th>
              <th>First Probed</th>
              <th>Last Probed</th>
            </tr>
            </thead>
            <tbody>
            {ports.map((p) => {
              return (
                <tr key={p.port}>
                  <td className="machine-data">{p.port}</td>
                  <td>{numeral(p.probe_count).format("0,0")}</td>
                  <td title={moment(p.first_seen).format()}>{moment(p.first_seen).fromNow()}</td>
                  <td title={moment(p.last_seen).format()}>{moment(p.last_seen).fromNow()}</td>
                </tr>
              )
            })}
            </tbody>
          </table>
        </div>

        {ports.length < total ?
          <p className="text-muted mb-0 mt-2">
            Showing {numeral(ports.length).format("0,0")} of {numeral(total).format("0,0")} silent ports. The
            targets table below has all of them.
          </p> : null}
      </React.Fragment>
    )
  }

  if (scan.is_port_scan) {
    const ports = summary.answered_ports || [];
    const total = summary.answered_ports_total || 0;

    if (ports.length === 0) {
      return (
        <React.Fragment>
          {stealthNote}
          <div className="alert alert-success mb-0">
            No port answered. The scanner learned nothing about this host except that it exists.
          </div>
        </React.Fragment>
      )
    }

    return (
      <React.Fragment>
        {stealthNote}
        <p>
          <strong>{numeral(total).format("0,0")}</strong> {total === 1 ? "port" : "ports"} answered
          {scan.target_address ? <span> on <span className="machine-data">{scan.target_address}</span></span> : null}.
          The scanner now knows these services exist:
        </p>

        <div style={{maxHeight: 260, overflowY: "auto"}}>
          <table className="table table-sm table-hover table-striped mb-0">
            <thead>
            <tr>
              <th>Port</th>
              <th>Probes</th>
              <th>First Answered</th>
              <th>Last Answered</th>
            </tr>
            </thead>
            <tbody>
            {ports.map((p) => {
              return (
                <tr key={p.port}>
                  <td className="machine-data">{p.port}</td>
                  <td>{numeral(p.probe_count).format("0,0")}</td>
                  <td title={moment(p.first_seen).format()}>{moment(p.first_seen).fromNow()}</td>
                  <td title={moment(p.last_seen).format()}>{moment(p.last_seen).fromNow()}</td>
                </tr>
              )
            })}
            </tbody>
          </table>
        </div>

        {ports.length < total ?
          <p className="text-muted mb-0 mt-2">
            Showing {numeral(ports.length).format("0,0")} of {numeral(total).format("0,0")} answering ports. The
            targets table below has all of them.
          </p> : null}
      </React.Fragment>
    )
  }

  const hosts = summary.answered_hosts || [];
  const total = summary.answered_hosts_total || 0;
  const isArp = scan.type === "ARP_SWEEP" || scan.type === "ICMP_SWEEP" || scan.type === "OS_FINGERPRINTING";

  if (hosts.length === 0) {
    return (
      <React.Fragment>
        {stealthNote}
        <div className="alert alert-success mb-0">
          No host answered. The scanner learned nothing about this network.
        </div>
      </React.Fragment>
    )
  }

  return (
    <React.Fragment>
      {stealthNote}
      <p>
        <strong>{numeral(total).format("0,0")}</strong> {total === 1 ? "host" : "hosts"} answered.
        {isArp ? " The scanner now knows these devices exist:" : " The scanner now knows these hosts and services exist:"}
      </p>

      <div style={{maxHeight: 260, overflowY: "auto"}}>
        <table className="table table-sm table-hover table-striped mb-0">
          <thead>
          <tr>
            <th>Host</th>
            {isArp ? null : <th>Answering Ports</th>}
          </tr>
          </thead>
          <tbody>
          {hosts.map((h) => {
            return (
              <tr key={h.address}>
                <td><IPAddressLink ip={h.address} /></td>
                {isArp ? null :
                  <td className="machine-data">
                    {h.ports.length === 0 ? <span className="text-muted">n/a</span> : h.ports.join(", ")}
                  </td>}
              </tr>
            )
          })}
          </tbody>
        </table>
      </div>

      {hosts.length < total ?
        <p className="text-muted mb-0 mt-2">
          Showing {numeral(hosts.length).format("0,0")} of {numeral(total).format("0,0")} answering hosts. The
          targets table below has all of them.
        </p> : null}
    </React.Fragment>
  )

}
