import React from "react";
import numeral from "numeral";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";

const rangeLabel = (range) => {
  switch (range) {
    case "WELL_KNOWN": return "Well-known (0 to 1023)";
    case "REGISTERED": return "Registered (1024 to 49151)";
    case "DYNAMIC": return "Dynamic (49152 to 65535)";
    default: return range;
  }
}

export default function ReconScanTargetBreakdown({scan, summary}) {

  if (!summary) {
    return <GenericWidgetLoadingSpinner height={120} />;
  }

  if (scan.is_port_scan) {
    const ranges = summary.port_ranges || [];

    if (ranges.length === 0) {
      return <div className="alert alert-info mb-0">No port information was recorded for this scan.</div>
    }

    return (
      <table className="table table-sm table-hover table-striped mb-0">
        <thead>
        <tr>
          <th>Port Range</th>
          <th>Probed</th>
          <th>Answered</th>
        </tr>
        </thead>
        <tbody>
        {ranges.map((r) => {
          return (
            <tr key={r.range}>
              <td>{rangeLabel(r.range)}</td>
              <td>{numeral(r.probed).format("0,0")}</td>
              <td>{numeral(r.answered).format("0,0")}</td>
            </tr>
          )
        })}
        </tbody>
      </table>
    )
  }

  const networks = summary.networks || [];
  const isArp = scan.type === "ARP_SWEEP" || scan.type === "ICMP_SWEEP" || scan.type === "DNS_REVERSE_SWEEP"
    || scan.type === "OS_FINGERPRINTING";

  if (networks.length === 0) {
    return <div className="alert alert-info mb-0">No target information was recorded for this scan.</div>
  }

  return (
    <div style={{maxHeight: 260, overflowY: "auto"}}>
      <table className="table table-sm table-hover table-striped mb-0">
        <thead>
        <tr>
          <th>Network</th>
          <th>Hosts Probed</th>
          <th>Hosts Answered</th>
          {isArp ? null : <th>Ports</th>}
        </tr>
        </thead>
        <tbody>
        {networks.map((n) => {
          return (
            <tr key={n.network}>
              <td><span className="machine-data">{n.network}</span></td>
              <td>{numeral(n.hosts).format("0,0")}</td>
              <td>{numeral(n.answered_hosts).format("0,0")}</td>
              {isArp ? null : <td>{numeral(n.ports).format("0,0")}</td>}
            </tr>
          )
        })}
        </tbody>
      </table>
    </div>
  )

}
