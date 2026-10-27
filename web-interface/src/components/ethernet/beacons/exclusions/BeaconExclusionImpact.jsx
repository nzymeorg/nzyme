import {Link} from "react-router-dom";
import React from "react";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";

export default function BeaconExclusionImpact({impact, compact = false}) {

  if (!impact) {
    return null;
  }

  if (impact.matching_beacons === 0) {
    if (compact) {
      return <span className="text-muted">None</span>
    }

    return (
      <div className="alert alert-secondary mb-0">
        No existing beacons match these criteria. The exclusion only affects future detection.
      </div>
    )
  }

  const partial = impact.matching_beacons - impact.complete_beacons;

  const summary = (
    <React.Fragment>
      <strong>{numeral(impact.matching_beacons).format("0,0")}</strong>{' '}
      {impact.matching_beacons === 1 ? "beacon" : "beacons"}
      {partial > 0 ? <span> ({numeral(partial).format("0,0")} only partially, by some devices)</span> : null},{' '}
      <strong>{numeral(impact.matching_sources).format("0,0")}</strong>{' '}
      {impact.matching_sources === 1 ? "device" : "devices"}
    </React.Fragment>
  )

  if (compact) {
    return summary;
  }

  return (
    <div className="alert alert-warning mb-0">
      <p>Matches {summary} that already exist:</p>

      <ul className="mb-0">
        {impact.beacons.map((b) => {
          return (
            <li key={b.uuid}>
              <Link to={ApiRoutes.ETHERNET.BEACONS.DETAILS(b.uuid)} target="_blank" rel="noreferrer">{b.name}</Link>{' '}
              <span className="text-muted">
                ({b.type} to <span className="machine-data">{b.destination}</span>
                {b.complete ? "" : ", " + b.matching_sources + " of " + b.total_sources + " devices"})
              </span>
            </li>
          )
        })}
        {impact.beacons.length < impact.matching_beacons ?
          <li className="text-muted">
            and {numeral(impact.matching_beacons - impact.beacons.length).format("0,0")} more
          </li> : null}
      </ul>
    </div>
  )

}
