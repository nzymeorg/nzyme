import React from "react";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import MultiLineChart from "../../../widgets/charts/MultiLineChart";

export default function ARPPacketsChart({statistics, timeRange, setTimeRange}) {

  if (!statistics) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  const formatData = data => {
    const requestCount = {};
    const replyCount = {};

    // Sort timestamps so the x‑axis is in order
    Object.keys(data).sort().forEach(timestamp => {
      const row = data[timestamp] || {};
      requestCount[timestamp] = row["request_count"] ?? 0;
      replyCount[timestamp] = row["reply_count"] ?? 0;
    });

    return {
      request_count: requestCount,
      reply_count: replyCount
    };
  };

  return <MultiLineChart
      height={200}
      lineWidth={1}
      customMarginBottom={35}
      seriesNames={{request_count: "ARP Requests", reply_count: "ARP Replies" }}
      data={formatData(statistics)}
      timeRange={timeRange}
      setTimeRange={setTimeRange}
  />

}