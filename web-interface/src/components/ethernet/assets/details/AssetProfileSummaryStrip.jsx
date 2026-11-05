import React from "react";
import moment from "moment";
import AssetActiveIndicator from "../AssetActiveIndicator";
import {describeChange} from "./AssetProfileHelpers";

export default function AssetProfileSummaryStrip({summary}) {

  const fact = (label, value, detail = null, last = false, explanation = null) => (
    <div className={"py-1 " + (last ? "" : "pe-4 me-4 border-end")}>
      <div className="small text-muted text-uppercase">{label}</div>
      <div>
        {value}
        {detail ? <span className="text-muted"> &middot; {detail}</span> : null}
      </div>
      {explanation ? <div className="small text-muted">{explanation}</div> : null}
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

  if (!summary) {
    const pending = <span className="text-muted">&hellip;</span>;
    return bar(
      <React.Fragment>
        {fact("Status", pending)}
        {fact("Active Alerts", pending)}
        {fact("Exclusions", pending)}
        {fact("Last Change", pending, null, true)}
      </React.Fragment>
    );
  }

  const alerts = summary.active_alerts > 0
    ? <span className="text-danger">{summary.active_alerts}</span>
    : <span>0</span>;

  const exclusions = summary.exclusions > 0 ? summary.exclusions : <span className="text-muted">None</span>;

  const lastChange = summary.last_change_at
    ? <span title={moment(summary.last_change_at).format()}>{moment(summary.last_change_at).fromNow()}</span>
    : <span className="text-muted">None recorded</span>;

  const change = summary.last_change_at
    ? describeChange(summary.last_change_type, summary.last_change_value, summary.last_change_detail)
    : null;

  const lastChangeDetail = change
    ? <span>
        {change.action}
        {change.value ? <span> {change.machineData ? <span className="machine-data">{change.value}</span> : change.value}</span> : null}
      </span>
    : null;

  return bar(
    <React.Fragment>
      {fact("Status", <AssetActiveIndicator active={summary.is_active} />, "last seen " + moment(summary.last_seen).fromNow())}
      {fact("Active Alerts", alerts, summary.total_alerts + " total")}
      {fact("Exclusions", exclusions, summary.exclusions > 0 ? "matching this asset" : null)}
      {fact("Last Change", lastChange, lastChangeDetail, true, change ? change.explanation : null)}
    </React.Fragment>
  );

}
