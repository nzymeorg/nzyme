import React from "react";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import MultiLineChart from "../../../widgets/charts/MultiLineChart";

export default function DHCPTransactionsChart({statistics, timeRange, setTimeRange}) {

  if (!statistics) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  const formatData = data => {
    const successful = {};
    const failed = {};

    // Sort timestamps so the x‑axis is in order
    Object.keys(data).sort().forEach(timestamp => {
      const row = data[timestamp] || {};
      successful[timestamp] = row["successful_transaction_count"] ?? 0;
      failed[timestamp] = row["failed_transaction_count"] ?? 0;
    });

    return {
      successful: successful,
      failed: failed
    };
  };

  return <MultiLineChart
      height={200}
      lineWidth={1}
      customMarginBottom={35}
      seriesNames={{successful: "Successful", failed: "Failed" }}
      data={formatData(statistics)}
      timeRange={timeRange}
      setTimeRange={setTimeRange}
  />

}