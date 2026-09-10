import React from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import MultiLineChart from "../../widgets/charts/MultiLineChart";

export default function L4SessionsInternalSessionsChart({statistics, timeRange, setTimeRange}) {

  if (!statistics) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  const formatData = data => {
    const sessionsTcp = {};
    const sessionsUdp = {};
    const sessionsTotal = {}

    // Sort timestamps so the x‑axis is in order
    Object.keys(data).sort().forEach(timestamp => {
      const row = data[timestamp] || {};

      const tcp = row["sessions_internal_tcp"] ?? 0;
      const udp = row["sessions_internal_udp"] ?? 0;

      sessionsTcp[timestamp] = tcp;
      sessionsUdp[timestamp] = udp;
      sessionsTotal[timestamp] = tcp + udp;
    });

    return {
      sessions_tcp: sessionsTcp,
      sessions_udp: sessionsUdp,
      sessions_total: sessionsTotal
    };
  };

  return <MultiLineChart
      height={200}
      lineWidth={1}
      customMarginBottom={35}
      customMarginLeft={75}
      seriesNames={{sessions_tcp: "TCP Sessions (Internal)", sessions_udp: "UDP Sessions/Conversations (Internal)", sessions_total: "Total Sessions (Internal)" }}
      data={formatData(statistics.statistics)}
      timeRange={timeRange}
      setTimeRange={setTimeRange}
  />

}