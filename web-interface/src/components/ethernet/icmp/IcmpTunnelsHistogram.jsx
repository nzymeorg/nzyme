import React from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import SimpleScatterChart from "../../widgets/charts/SimpleScatterChart";

export default function IcmpTunnelsHistogram({histogram, timeRange, setTimeRange}) {

  if (!histogram) {
    return <GenericWidgetLoadingSpinner height={200} />;
  }

  const series = {name: "Tunnels", x: [], y: [],
    hovertemplate: "%{x}<br>%{y:,} suspected tunnels<extra></extra>"};

  let any = false;
  Object.keys(histogram).sort().forEach((key) => {
    if (histogram[key] > 0) {
      series.x.push(new Date(key));
      series.y.push(histogram[key]);
      any = true;
    }
  });

  if (!any) {
    return <div className="alert alert-info mb-0">No suspected tunnels in the selected time range.</div>
  }

  return (
    <SimpleScatterChart
      height={200}
      series={[series]}
      yaxistitle="Tunnels"
      setTimeRange={setTimeRange}
      timeRange={timeRange}
      doNotPersistTimeRange={true} />
  )

}
