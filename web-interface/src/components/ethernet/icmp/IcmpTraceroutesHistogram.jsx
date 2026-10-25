import React from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import SimpleScatterChart from "../../widgets/charts/SimpleScatterChart";

export default function IcmpTraceroutesHistogram({histogram, timeRange, setTimeRange}) {

  if (!histogram) {
    return <GenericWidgetLoadingSpinner height={200} />;
  }

  const series = {name: "Traceroutes", x: [], y: [],
    hovertemplate: "%{x}<br>%{y:,} traceroutes<extra></extra>"};

  // Traceroutes are sparse, so most buckets are zero. Plot only the buckets that have traceroutes.
  Object.keys(histogram).sort().forEach((key) => {
    if (histogram[key] > 0) {
      series.x.push(new Date(key));
      series.y.push(histogram[key]);
    }
  });

  if (series.x.length === 0) {
    return <div className="alert alert-info mb-0">No traceroutes in the selected time range.</div>
  }

  return (
    <SimpleScatterChart
      height={200}
      series={[series]}
      yaxistitle="Traceroutes"
      setTimeRange={setTimeRange}
      timeRange={timeRange}
      doNotPersistTimeRange={true} />
  )

}
