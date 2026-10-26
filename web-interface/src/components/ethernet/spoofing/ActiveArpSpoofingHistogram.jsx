import React from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import SimpleScatterChart from "../../widgets/charts/SimpleScatterChart";

export default function ActiveArpSpoofingHistogram({histogram, timeRange, setTimeRange}) {

  if (!histogram) {
    return <GenericWidgetLoadingSpinner height={200} />;
  }

  const series = {x: [], y: [], type: "scatter", mode: "lines+markers", name: "Active findings"};
  let any = false;

  Object.keys(histogram).sort().forEach((key) => {
    series.x.push(new Date(key));
    // Empty buckets get no marker. Plotly skips null points.
    series.y.push(histogram[key] > 0 ? histogram[key] : null);
    if (histogram[key] > 0) {
      any = true;
    }
  });

  if (!any) {
    return <div className="alert alert-info mb-0">No ARP poisoning in the selected time range.</div>
  }

  return (
    <SimpleScatterChart
      height={200}
      series={[series]}
      yaxistitle="Active findings"
      setTimeRange={setTimeRange}
      timeRange={timeRange}
      doNotPersistTimeRange={true} />
  )

}
