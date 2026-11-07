import React from "react";
import numeral from "numeral";
import EthernetMacAddress from "../../../../shared/context/macs/EthernetMacAddress";

export default function AssetIdentityExclusionImpact({impact, compact = false}) {

  if (!impact) {
    return null;
  }

  if (impact.matching_assets === 0) {
    if (compact) {
      return <span className="text-muted">None</span>
    }

    return (
      <div className="alert alert-secondary mb-0">
        No known asset matches these criteria. The exclusion covers assets that are discovered later.
      </div>
    )
  }

  const summary = (
    <React.Fragment>
      <strong>{numeral(impact.matching_assets).format("0,0")}</strong>{' '}
      {impact.matching_assets === 1 ? "asset" : "assets"}
    </React.Fragment>
  )

  if (compact) {
    return summary;
  }

  return (
    <div className="alert alert-info mb-0">
      <p>Covers {summary} that are already known:</p>

      <ul className="mb-0">
        {impact.assets.map((a) => {
          return (
            <li key={a.uuid}>
              <EthernetMacAddress addressWithContext={a.mac} withAssetName withAssetLink />
            </li>
          )
        })}
        {impact.assets.length < impact.matching_assets ?
          <li className="text-muted">
            and {numeral(impact.matching_assets - impact.assets.length).format("0,0")} more
          </li> : null}
      </ul>
    </div>
  )

}
