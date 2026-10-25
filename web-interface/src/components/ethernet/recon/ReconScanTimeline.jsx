import React from "react";
import moment from "moment";
import numeral from "numeral";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import Store from "../../../util/Store";
import SimpleBarChart from "../../widgets/charts/SimpleBarChart";

export default function ReconScanTimeline({scan, summary}) {

  if (!summary) {
    return <GenericWidgetLoadingSpinner height={200} />;
  }

  const dataKeys = Object.keys(summary.timeline || {}).sort();

  if (dataKeys.length === 0) {
    return <div className="alert alert-info mb-0">No target timing was recorded for this scan.</div>
  }

  const bucketLabel = summary.timeline_bucket === "hour" ? "hour" : "minute";
  const stepMs = summary.timeline_bucket === "hour" ? 3600000 : 60000;

  const MINIMUM_BUCKETS = 12;
  const first = new Date(dataKeys[0]).getTime();
  const last = new Date(dataKeys[dataKeys.length - 1]).getTime();
  const spanBuckets = Math.round((last - first) / stepMs) + 1;
  const padding = Math.max(0, Math.ceil((MINIMUM_BUCKETS - spanBuckets) / 2));

  const buckets = [];
  for (let t = first - padding * stepMs; t <= last + padding * stepMs; t += stepMs) {
    buckets.push(new Date(t));
  }

  const valueAt = (date, field) => {
    const key = dataKeys.find((k) => new Date(k).getTime() === date.getTime());
    return key ? summary.timeline[key][field] : 0;
  }

  const trace = (name, field, color) => {
    return {
      name: name,
      type: "bar",
      x: buckets,
      y: buckets.map((b) => valueAt(b, field)),
      marker: {color: color},
      hovertemplate: "%{x}<br>%{y:,} " + name.toLowerCase() + "<extra></extra>"
    }
  }

  const total = dataKeys.reduce((sum, k) => {
    const b = summary.timeline[k];
    return sum + b.open + b.closed + b.unanswered;
  }, 0);

  const answeredColor = Store.get("dark_mode") ? "#e6e6e6" : "#1d30d7";
  const refusedColor = "#d99a00";
  const unansweredColor = "#6c757d";

  const traces = [
    trace("Unanswered", "unanswered", unansweredColor),
    trace("Answered", "open", answeredColor)
  ];

  const hasRefused = scan.type.startsWith("TCP") || scan.closed_count > 0;
  if (hasRefused) {
    traces.splice(1, 0, trace("Refused", "closed", refusedColor));
  }

  return (
    <React.Fragment>
      <p className="text-muted">
        Targets by the {bucketLabel} they were first probed in.
        {dataKeys.length === 1 ?
          <span> All {numeral(total).format("0,0")} targets were first probed within a single {bucketLabel},
            starting {moment(dataKeys[0]).format("YYYY-MM-DD HH:mm")}. This was a fast scan.</span>
          : null}
      </p>

      <SimpleBarChart height={200} finalData={traces} yaxistitle="Targets" />
    </React.Fragment>
  )

}
