import React from "react";

export default function DnsTunnelExclusionCriteria({exclusion}) {

  const criteria = [];

  if (exclusion.domain) {
    criteria.push({label: "Domain", value: exclusion.domain});
  }

  if (exclusion.client_address_cidr) {
    criteria.push({label: "Client", value: exclusion.client_address_cidr});
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
