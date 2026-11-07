import React from "react";
import numeral from "numeral";
import {linkEvidenceDescription, linkEvidenceTitle} from "./AssetIdentityHelpers";
import TcpFingerprint from "./TcpFingerprint";

export default function AssetLinkEvidenceTable({evidence}) {

  if (!evidence || evidence.length === 0) {
    return <div className="alert alert-info mb-0">No evidence recorded.</div>
  }

  const value = (e) => {
    if (!e.value) {
      return <span className="text-muted">n/a</span>;
    }

    if (e.signal === "TCP_STACK" || e.signal === "TCP_SERVER_FINGERPRINT") {
      return e.value.split(", ").map((fp, i) => <React.Fragment key={fp}>{i > 0 ? <br /> : null}<TcpFingerprint value={fp} /></React.Fragment>);
    }

    return <span className="machine-data">{e.value}</span>;
  }

  return (
    <table className="table table-sm table-hover table-striped mb-0">
      <thead>
      <tr>
        <th>Evidence</th>
        <th>Weight</th>
        <th>Value</th>
      </tr>
      </thead>
      <tbody>
      {evidence.map((e) => {
        return (
          <tr key={e.signal}>
            <td>
              <strong>{linkEvidenceTitle(e.signal)}</strong>
              <br />
              <span className="text-muted small">{linkEvidenceDescription(e.signal)}</span>
            </td>
            <td className={e.weight < 0 ? "text-danger" : ""}>{numeral(e.weight).format("+0,0")}</td>
            <td>{value(e)}</td>
          </tr>
        )
      })}
      </tbody>
    </table>
  )

}
