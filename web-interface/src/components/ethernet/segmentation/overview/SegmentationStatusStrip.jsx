import React from "react";
import moment from "moment";
import {formatCount} from "../../assets/details/AssetProfileHelpers";
import TopologySummaryStrip from "../../topology/TopologySummaryStrip";
import {BACKFILL_NOTICE, RESTAMP_NOTICE} from "../SegmentationFormat";

// Data is behind by the settle time by design. It is only called late once it is clearly more than that.
const LATE_FACTOR = 3;
const LATE_MINIMUM_SECONDS = 300;

const currency = (status) => {
  if (status.lag_seconds === null || status.lag_seconds === undefined) {
    return <span className="text-muted" title="No sessions were evaluated yet.">Not evaluated yet</span>;
  }

  const evaluated = status.watermark ? "Evaluated up to " + moment(status.watermark).format("YYYY-MM-DD HH:mm:ss") + ". " : "";
  const title = evaluated + "Expected delay: " + moment.duration(status.settle_seconds, "seconds").humanize() + ".";

  if (status.lag_seconds > Math.max(LATE_MINIMUM_SECONDS, LATE_FACTOR * status.settle_seconds)) {
    return (
        <span title={title}>
          <i className="fa-solid fa-triangle-exclamation text-warning me-1" />
          {moment.duration(status.lag_seconds, "seconds").humanize()} behind
        </span>
    );
  }

  return <span title={title}>Up to date</span>;
}

const lastRun = (status) => {
  if (!status.last_run_at) return <span className="text-muted">Never</span>;

  const details = [];
  if (status.last_run_sessions !== null && status.last_run_sessions !== undefined) {
    details.push(formatCount(status.last_run_sessions) + " sessions");
  }
  if (status.last_run_flows !== null && status.last_run_flows !== undefined) {
    details.push(formatCount(status.last_run_flows) + " flows");
  }
  if (status.last_run_duration_ms !== null && status.last_run_duration_ms !== undefined) {
    details.push(formatCount(status.last_run_duration_ms) + " ms");
  }

  return (
      <span title={moment(status.last_run_at).format() + (details.length > 0 ? ": " + details.join(", ") : "")}>
        {moment(status.last_run_at).fromNow()}
      </span>
  );
}

export default function SegmentationStatusStrip({status, restampPending = false, findingsLimit = null}) {

  const facts = [
    ["Segments", status ? formatCount(status.segment_count) : null],
    ["Policies", status ? formatCount(status.policy_count) : null],
    ["Data", status ? (status.enabled ? currency(status) : <span className="text-muted">Not evaluated</span>) : null],
    ["Last Evaluation", status ? lastRun(status) : null]
  ];

  return (
      <React.Fragment>
        <TopologySummaryStrip facts={facts} />

        {status && status.limit_reached_at && moment(status.limit_reached_at).isAfter(moment().subtract(1, "days")) ?
            <div className="row mt-3">
              <div className="col-12">
                <div className="alert alert-warning mb-0" title={moment(status.limit_reached_at).format()}>
                  An evaluation {moment(status.limit_reached_at).fromNow()} was limited to{' '}
                  {formatCount(status.flows_per_evaluation_limit)} flows and{' '}
                  {formatCount(status.sources_per_policy_per_evaluation_limit)} new sources per policy. The rest
                  was not evaluated.
                </div>
              </div>
            </div> : null}

        {findingsLimit !== null ?
            <div className="row mt-3">
              <div className="col-12">
                <div className="alert alert-warning mb-0">
                  Findings in crossings are limited to the {formatCount(findingsLimit)} most recent findings.
                </div>
              </div>
            </div> : null}

        {status && !status.enabled ?
            <div className="row mt-3">
              <div className="col-12">
                <div className="alert alert-warning mb-0">
                  Segmentation is disabled. New sessions are not evaluated.
                </div>
              </div>
            </div> : null}

        {restampPending || (status && status.restamp_pending) ?
            <div className="row mt-3">
              <div className="col-12">
                <div className="alert alert-info mb-0">
                  <i className="fa-solid fa-circle-notch fa-spin"></i> {RESTAMP_NOTICE}
                </div>
              </div>
            </div> : null}

        {status && status.backfill_pending ?
            <div className="row mt-3">
              <div className="col-12">
                <div className="alert alert-info mb-0">
                  <i className="fa-solid fa-circle-notch fa-spin"></i> {BACKFILL_NOTICE}
                </div>
              </div>
            </div> : null}
      </React.Fragment>
  )

}
