import React from "react";
import {formatBytes, formatCount} from "../assets/details/AssetProfileHelpers";

export default function TopologySummaryStrip({summary, timeline}) {

  const fact = (label, value, last = false) => (
    <div className={"py-1 " + (last ? "" : "pe-4 me-4 border-end")}>
      <div className="small text-muted text-uppercase">{label}</div>
      <div>{value}</div>
    </div>
  )

  const bar = (facts) => (
    <div className="row mt-3">
      <div className="col-12">
        <div className="card">
          <div className="card-body py-2">
            <div className="d-flex flex-wrap align-items-center">
              {facts}
            </div>
          </div>
        </div>
      </div>
    </div>
  )

  const pending = <span className="text-muted">&hellip;</span>;

  const totals = timeline ? timeline.totals : null;
  const sessions = totals
    ? totals.sessions_internal + totals.sessions_outbound + totals.sessions_inbound + totals.sessions_external : null;
  const bytes = totals
    ? totals.bytes_internal + totals.bytes_outbound + totals.bytes_inbound + totals.bytes_external : null;

  return bar(
    <React.Fragment>
      {fact("Internal Addresses", summary ? formatCount(summary.internal_hosts) : pending)}
      {fact("External Addresses", summary ? formatCount(summary.external_hosts) : pending)}
      {fact("Autonomous Systems", summary ? formatCount(summary.external_networks) : pending)}
      {fact("Countries", summary ? formatCount(summary.external_countries) : pending)}
      {fact("Sessions", totals ? formatCount(sessions) : pending)}
      {fact("Traffic", totals ? formatBytes(bytes) : pending, true)}
    </React.Fragment>
  );

}
