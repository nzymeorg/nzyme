import React from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import SimpleBarChart from "../../widgets/charts/SimpleBarChart";
import Store from "../../../util/Store";

export default function ChannelSwitchesChart({histogram, timeRange, setTimeRange}) {

  if (!histogram) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  const darkMode = Store.get("dark_mode");

  const series = [
    {name: "With Anomalies", color: darkMode ? "#e6e6e6" : "#1d30d7", value: (b) => b.anomalous_frame_count},
    {name: "Without Anomalies", color: darkMode ? "#8a8aa0" : "#8ea2ff", value: (b) => b.frame_count - b.anomalous_frame_count}
  ];

  const timestamps = Object.keys(histogram).sort();

  const finalData = series.map((s) => {
    return {
      name: s.name,
      x: timestamps.map((t) => new Date(t)),
      y: timestamps.map((t) => s.value(histogram[t])),
      type: "bar",
      marker: {color: s.color}
    }
  });

  return <SimpleBarChart
      height={200}
      customMarginBottom={35}
      customMarginLeft={45}
      timeRange={timeRange}
      setTimeRange={setTimeRange}
      finalData={finalData} />

}
