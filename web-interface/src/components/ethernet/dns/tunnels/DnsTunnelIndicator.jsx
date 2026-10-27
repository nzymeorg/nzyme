import React from "react";

export const indicatorToName = (indicator) => {
  switch (indicator) {
    case "HIGH_ENTROPY": return "High Entropy";
    case "LONG_NAMES": return "Long Names";
    case "UNUSUAL_RECORD_TYPES": return "Unusual Record Types";
    default: return indicator;
  }
}

const indicatorToDescription = (indicator) => {
  switch (indicator) {
    case "HIGH_ENTROPY":
      return "The average entropy of the names below the registered domain is at or above the entropy threshold.";
    case "LONG_NAMES":
      return "The average length of the names below the registered domain is at or above the length threshold.";
    case "UNUSUAL_RECORD_TYPES":
      return "The share of TXT, NULL, CNAME, MX, KEY and SRV queries is at or above the record type share threshold.";
    default:
      return indicator;
  }
}

export default function DnsTunnelIndicator({indicator, filterElement = null}) {
  return (
    <span>
      <span title={indicatorToDescription(indicator)}>{indicatorToName(indicator)}</span>
      {filterElement}
    </span>
  )
}

export function DnsTunnelIndicators({indicators, filterIcon = null}) {
  if (!indicators || indicators.length === 0) {
    return <span className="text-muted">None</span>
  }

  return (
    <React.Fragment>
      {indicators.map((i, idx) =>
        <React.Fragment key={i}>
          {idx > 0 ? ", " : null}
          <DnsTunnelIndicator indicator={i} filterElement={filterIcon ? filterIcon("indicator", i) : null} />
        </React.Fragment>
      )}
    </React.Fragment>
  )
}
