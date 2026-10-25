import React from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import MultiLineChart from "../../widgets/charts/MultiLineChart";

export default function IcmpPacketsChart({statistics, timeRange, setTimeRange, series}) {

  if (!statistics) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  const formatData = (data) => {
    const result = {};
    Object.keys(series).forEach((key) => result[key] = {});

    Object.keys(data).sort().forEach((timestamp) => {
      const row = data[timestamp] || {};
      Object.keys(series).forEach((key) => {
        result[key][timestamp] = row[key] ?? 0;
      });
    });

    return result;
  };

  return <MultiLineChart
      height={200}
      lineWidth={1}
      customMarginBottom={35}
      seriesNames={series}
      data={formatData(statistics)}
      timeRange={timeRange}
      setTimeRange={setTimeRange}
  />

}
