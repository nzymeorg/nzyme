import React from "react";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";
import {spoofingTypeToName} from "../SpoofingType";

const detailsLink = (f) => {
  switch (f.type) {
    case "DHCP": return ApiRoutes.ETHERNET.SPOOFING.DHCP.DETAILS(f.uuid);
    case "NAME_RESOLUTION": return ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.DETAILS(f.uuid);
    default: return ApiRoutes.ETHERNET.SPOOFING.ARP.DETAILS(f.uuid);
  }
}

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
              <a href={detailsLink(f)} target="_blank" rel="noreferrer">
                {spoofingTypeToName(f.type)}
                {f.type === "NAME_RESOLUTION" ? " by " : f.type === "DHCP" ? " by server " : " of "}
                <span className="machine-data">{f.type === "NAME_RESOLUTION" ? f.mac : f.address}</span>
              </a>{' '}
              {f.type === "NAME_RESOLUTION" ?
                (f.address ? <span className="text-muted">(<span className="machine-data">{f.address}</span>)</span> : null)
                : <span className="text-muted">({f.type === "DHCP" ? "MAC" : "by"} <span className="machine-data">{f.mac}</span>)</span>}
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
