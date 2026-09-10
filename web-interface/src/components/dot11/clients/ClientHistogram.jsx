import React from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";
import SimpleLineChart from "../../widgets/charts/SimpleLineChart";

function ClientHistogram({histogram, timeRange, setTimeRange, monitorsReady}) {

  const formatData = function(data) {
    const result = {}

    Object.keys(data).sort().forEach(function(key) {
      result[key] = data[key]["client_count"];
    })

    return result
  }

  if (!histogram || !monitorsReady) {
    return <LoadingSpinner />
  }

  return <SimpleLineChart
      height={200}
      lineWidth={1}
      customMarginBottom={35}
      timeRange={timeRange}
      setTimeRange={setTimeRange}
      data={formatData(histogram.values)}
  />

}

export default ClientHistogram;