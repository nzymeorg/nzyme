import React from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import MultiLineChart from "../../widgets/charts/MultiLineChart";

export default function L4SessionsTotalSessionsChart({statistics, timeRange, setTimeRange}) {

  if (!statistics) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  console.log(statistics);

  const formatData = data => {
    const sessionsTcp = {};
    const sessionsUdp = {};
    const sessionsTotal = {}

    // Sort timestamps so the x‑axis is in order
    Object.keys(data).sort().forEach(timestamp => {
      const row = data[timestamp] || {};

      const tcp = row["sessions_tcp"] ?? 0;
      const udp = row["sessions_udp"] ?? 0;

      console.log(tcp);

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
      seriesNames={{sessions_tcp: "TCP Sessions", sessions_udp: "UDP Sessions/Conversations", sessions_total: "Total Sessions" }}
      data={formatData(statistics.statistics)}
      setTimeRange={setTimeRange}
      timeRange={timeRange}
  />

}