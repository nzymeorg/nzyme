import React from "react";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import MultiLineChart from "../../../widgets/charts/MultiLineChart";

const SERIES = {
  neighbor_solicitations: "Neighbor Solicitations",
  neighbor_advertisements: "Neighbor Advertisements",
  router_solicitations: "Router Solicitations"
};

export default function NDPMessagesChart({statistics, timeRange, setTimeRange}) {

  if (!statistics) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  const formatData = (data) => {
    const series = {};
    Object.keys(SERIES).forEach((key) => { series[key] = {}; });

    Object.keys(data).sort().forEach((timestamp) => {
      const row = data[timestamp] || {};
      Object.keys(SERIES).forEach((key) => { series[key][timestamp] = row[key] ?? 0; });
    });

    return series;
  };

  return <MultiLineChart
      height={200}
      lineWidth={1}
      customMarginBottom={35}
      seriesNames={SERIES}
      data={formatData(statistics)}
      timeRange={timeRange}
      setTimeRange={setTimeRange}
  />

}
