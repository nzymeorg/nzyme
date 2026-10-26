import React from "react";
import {spoofingTypeToName} from "../SpoofingType";

export default function SpoofingExclusionCriteria({exclusion}) {

  const criteria = [];

  if (exclusion.types && exclusion.types.length > 0) {
    criteria.push({label: "Type", value: exclusion.types.map(spoofingTypeToName).join(", ")});
  }

  if (exclusion.address_cidr) {
    criteria.push({label: "Claimed address", value: exclusion.address_cidr});
  }

  if (exclusion.mac) {
    criteria.push({label: "Device MAC", value: exclusion.mac});
  }

  if (criteria.length === 0) {
    return <span className="text-muted">No criteria</span>
  }

  return (
    <React.Fragment>
      {criteria.map((c, i) => {
        return (
          <React.Fragment key={i}>
            {i > 0 ? <span className="text-muted"> and </span> : null}
            <span className="text-nowrap">{c.label} <span className="machine-data">{c.value}</span></span>
          </React.Fragment>
        )
      })}
    </React.Fragment>
  )

}
