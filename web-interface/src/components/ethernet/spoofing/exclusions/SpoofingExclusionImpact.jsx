import React from "react";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";
import {spoofingTypeToName} from "../SpoofingType";

export default function SpoofingExclusionImpact({impact, compact = false}) {

  if (!impact) {
    return null;
  }

  if (impact.matching_findings === 0) {
    if (compact) {
      return <span className="text-muted">None</span>
    }

    return (
      <div className="alert alert-secondary mb-0">
        No existing findings match these criteria. The exclusion only affects future detection.
      </div>
    )
  }

  const summary = (
    <React.Fragment>
      <strong>{numeral(impact.matching_findings).format("0,0")}</strong>{' '}
      {impact.matching_findings === 1 ? "finding" : "findings"}
    </React.Fragment>
  )

  if (compact) {
    return summary;
  }

  return (
    <div className="alert alert-warning mb-0">
      <p>Matches {summary} that already exist:</p>

      <ul className="mb-0">
        {impact.findings.map((f) => {
          return (
            <li key={f.uuid}>
              <a href={ApiRoutes.ETHERNET.SPOOFING.ARP.DETAILS(f.uuid)} target="_blank" rel="noreferrer">
                {spoofingTypeToName(f.type)} of <span className="machine-data">{f.address}</span>
              </a>{' '}
              <span className="text-muted">(by <span className="machine-data">{f.mac}</span>)</span>
            </li>
          )
        })}
        {impact.findings.length < impact.matching_findings ?
          <li className="text-muted">
            and {numeral(impact.matching_findings - impact.findings.length).format("0,0")} more
          </li> : null}
      </ul>
    </div>
  )

}
