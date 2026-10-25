import React from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import SimpleScatterChart from "../../widgets/charts/SimpleScatterChart";

export default function IcmpUnreachablesHistogram({histogram, timeRange, setTimeRange}) {

  if (!histogram) {
    return <GenericWidgetLoadingSpinner height={200} />;
  }

  const series = {name: "Port unreachables", x: [], y: [],
    hovertemplate: "%{x}<br>%{y:,} port unreachables<extra></extra>"};

  let any = false;
  Object.keys(histogram).sort().forEach((key) => {
    series.x.push(new Date(key));
    series.y.push(histogram[key]);
    if (histogram[key] > 0) {
      any = true;
    }
  });

  if (!any) {
    return <div className="alert alert-info mb-0">No port unreachables in the selected time range.</div>
  }

  return (
    <SimpleScatterChart
      height={200}
      series={[series]}
      yaxistitle="Packets"
      setTimeRange={setTimeRange}
      timeRange={timeRange}
      doNotPersistTimeRange={true} />
  )

}
