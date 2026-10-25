import React from "react";

const typeName = (t) => t === "REDIRECT" ? "Redirect" : t === "TRACEROUTE" ? "Traceroute" : t;

export default function IcmpExclusionCriteria({exclusion}) {

  const criteria = [];

  if (exclusion.detection_types && exclusion.detection_types.length > 0) {
    criteria.push({label: "Type", value: exclusion.detection_types.map(typeName).join(", ")});
  }
  if (exclusion.source_cidr) {
    criteria.push({label: "Source", value: exclusion.source_cidr});
  }
  if (exclusion.source_mac) {
    criteria.push({label: "Source MAC", value: exclusion.source_mac});
  }
  if (exclusion.gateway_cidr) {
    criteria.push({label: "Gateway", value: exclusion.gateway_cidr});
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
