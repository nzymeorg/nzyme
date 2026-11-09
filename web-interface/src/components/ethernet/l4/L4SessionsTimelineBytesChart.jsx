import React from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import MultiLineChart from "../../widgets/charts/MultiLineChart";

function byteConversion (x) {
  return x / 1024 / 1024
}

export default function L4SessionsTimelineBytesChart({timeline, timeRange, setTimeRange}) {

  if (!timeline) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  const formatData = (buckets) => {
    const bytes = {};

    Object.keys(buckets).sort().forEach((timestamp) => {
      bytes[timestamp] = byteConversion(buckets[timestamp].bytes);
    });

    return { bytes: bytes };
  }

  return <MultiLineChart
      height={200}
      lineWidth={1}
      customMarginBottom={35}
      customMarginLeft={75}
      bucketSize={timeline.bucket_size_ms}
      seriesNames={{bytes: "Bytes"}}
      data={formatData(timeline.buckets)}
      ticksuffix={" MB"}
      tickformat={".2~f"}
      timeRange={timeRange}
      setTimeRange={setTimeRange} />

}
