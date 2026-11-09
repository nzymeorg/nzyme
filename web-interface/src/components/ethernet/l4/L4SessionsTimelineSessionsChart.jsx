import React from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import MultiLineChart from "../../widgets/charts/MultiLineChart";

export default function L4SessionsTimelineSessionsChart({timeline, timeRange, setTimeRange}) {

  if (!timeline) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  const formatData = (buckets) => {
    const sessions = {};

    Object.keys(buckets).sort().forEach((timestamp) => {
      sessions[timestamp] = buckets[timestamp].sessions;
    });

    return { sessions: sessions };
  }

  return <MultiLineChart
      height={200}
      lineWidth={1}
      customMarginBottom={35}
      customMarginLeft={60}
      bucketSize={timeline.bucket_size_ms}
      seriesNames={{sessions: "Sessions"}}
      data={formatData(timeline.buckets)}
      timeRange={timeRange}
      setTimeRange={setTimeRange} />

}
