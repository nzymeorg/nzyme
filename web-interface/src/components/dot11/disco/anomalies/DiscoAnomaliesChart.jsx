import React from "react";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import SimpleBarChart from "../../../widgets/charts/SimpleBarChart";
import Store from "../../../../util/Store";

const BARS = 96;

const spanOf = (timeRange) => {
  if (timeRange.type === "relative") {
    const now = new Date().getTime();
    return {from: now - timeRange.minutes * 60000, to: now};
  }

  if (timeRange.type === "absolute") {
    return {from: new Date(timeRange.from).getTime(), to: new Date(timeRange.to).getTime()};
  }

  return null;
}

const barsOf = (histogram, timeRange) => {
  const timestamps = Object.keys(histogram).sort();
  const span = spanOf(timeRange);

  if (!span || span.to <= span.from) {
    return {x: timestamps.map((t) => new Date(t)), y: timestamps.map((t) => histogram[t].total)};
  }

  const width = (span.to - span.from) / BARS;
  const totals = {};
  timestamps.forEach((t) => {
    const bar = Math.min(Math.max(Math.floor((new Date(t).getTime() - span.from) / width), 0), BARS - 1);
    totals[bar] = (totals[bar] || 0) + histogram[t].total;
  });

  const bars = Object.keys(totals).map((bar) => parseInt(bar, 10)).sort((a, b) => a - b);

  return {
    x: bars.map((bar) => new Date(span.from + (bar + 0.5) * width)),
    y: bars.map((bar) => totals[bar]),
    width: width * 0.9
  };
}

export default function DiscoAnomaliesChart({histogram, timeRange, setTimeRange}) {

  if (!histogram) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  if (Object.keys(histogram).length === 0) {
    return <div className="alert alert-info mb-0">No new anomalies.</div>
  }

  const finalData = [{
    name: "New Anomalies",
    ...barsOf(histogram, timeRange),
    type: "bar",
    marker: {color: Store.get("dark_mode") ? "#e6e6e6" : "#1d30d7"}
  }];

  return <SimpleBarChart
      height={200}
      customMarginBottom={35}
      timeRange={timeRange}
      setTimeRange={setTimeRange}
      finalData={finalData} />

}
