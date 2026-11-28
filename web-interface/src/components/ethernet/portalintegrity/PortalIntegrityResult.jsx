import React from "react";

const EXPECTATIONS = {
  NOT_MONITORED: "Not Monitored",
  PORTAL: "Captive Portal",
  NO_PORTAL: "No Captive Portal"
};

const REASONS = {
  URL: "URL",
  ADDRESS: "address",
  CERTIFICATE: "certificate"
};

export const PORTAL_EXPECTATIONS = Object.keys(EXPECTATIONS);

export const portalExpectationToName = (expectation) => {
  return EXPECTATIONS[expectation] ? EXPECTATIONS[expectation] : expectation;
}

export const portalReasonsToText = (reasons) => {
  return (reasons ? reasons : []).map((r) => REASONS[r] ? REASONS[r] : r).join(", ");
}

export default function PortalIntegrityResult({controlUrl}) {

  const c = controlUrl;

  if (c.result === "NOT_MONITORED") {
    return <span className="text-muted">Not Monitored</span>
  }

  switch (c.result) {
    case "NOT_REPORTING":
      return <span className="text-danger" title="The captive portal probe has missed several cycles.">Not Reporting</span>
    case "NOT_IN_TAP_CONFIGURATION":
      return (
        <span className="text-muted" title="The tap no longer runs a captive portal probe that requests this control URL. No alerts are raised.">
          Not in Tap Configuration
        </span>
      )
    case "AS_EXPECTED":
      return <span className="text-success">As Expected</span>
    case "PORTAL_MISSING":
      return <span className="text-danger">Portal Missing</span>
    case "PORTAL_UNEXPECTED":
      return <span className="text-danger">Unexpected Portal</span>
    case "PORTAL_WRONG":
      return <span className="text-danger" title={"Differs in " + portalReasonsToText(c.reasons)}>Wrong Portal</span>
    case "REQUEST_FAILED":
      return <span className="text-warning" title={c.last_error ? c.last_error : undefined}>Request Failed</span>
    default:
      return <span>{c.result}</span>
  }

}
