import React from "react";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import SimpleLineChart from "../../../widgets/charts/SimpleLineChart";

export default function ARPRequestToReplyRatioChart({statistics, timeRange, setTimeRange}) {

  if (!statistics) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  const formatData = function(data) {
    const result = {}

    Object.keys(data).sort().forEach(function(key) {
      result[key] = data[key]["request_to_reply_ratio"];
    })

    return result
  }

  return <SimpleLineChart
      height={200}
      lineWidth={1}
      scattermode="markers"
      data={formatData(statistics)}
      timeRange={timeRange}
      setTimeRange={setTimeRange}
  />

}