import {Link} from "react-router-dom";
import React from "react";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";
import {reconScanTypeToName} from "../ReconScanType";

export default function ReconExclusionImpact({impact, compact = false}) {

  if (!impact) {
    return null;
  }

  if (impact.matching_scans === 0) {
    if (compact) {
      return <span className="text-muted">None</span>
    }

    return (
      <div className="alert alert-secondary mb-0">
        No existing scans match these criteria. The exclusion only affects future detection.
      </div>
    )
  }

  const summary = (
    <React.Fragment>
      <strong>{numeral(impact.matching_scans).format("0,0")}</strong>{' '}
      {impact.matching_scans === 1 ? "scan" : "scans"}
    </React.Fragment>
  )

  if (compact) {
    return summary;
  }

  return (
    <div className="alert alert-warning mb-0">
      <p>Matches {summary} that already exist:</p>

      <ul className="mb-0">
        {impact.scans.map((s) => {
          return (
            <li key={s.uuid}>
              <Link to={s.type === "OS_FINGERPRINTING"
                   ? ApiRoutes.ETHERNET.RECON.FINGERPRINTING.DETAILS(s.uuid)
                   : ApiRoutes.ETHERNET.RECON.SCANS.DETAILS(s.uuid)}
                 target="_blank" rel="noreferrer">
                {reconScanTypeToName(s.type)} from <span className="machine-data">{s.source}</span>
              </Link>{' '}
              <span className="text-muted">(against <span className="machine-data">{s.target}</span>)</span>
            </li>
          )
        })}
        {impact.scans.length < impact.matching_scans ?
          <li className="text-muted">
            and {numeral(impact.matching_scans - impact.scans.length).format("0,0")} more
          </li> : null}
      </ul>
    </div>
  )

}
