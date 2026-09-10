import React from "react";
import SimpleLineChart from "../../widgets/charts/SimpleLineChart";
import SimpleBarChart from "../../widgets/charts/SimpleBarChart";
import LoadingSpinner from "../../misc/LoadingSpinner";

function ClientActivityHistogram({histogram, parameter, type, timeRange, setTimeRange, urlKey = undefined}) {

  if (histogram === null) {
    return <LoadingSpinner />
  }

  const formatData = function(data) {
    const result = {}

    Object.keys(data).sort().forEach(function(key) {
      result[key] = data[key][parameter];
    })

    return result
  }

  switch (type) {
    case "bar":
      return <SimpleBarChart
          height={200}
          lineWidth={1}
          customMarginBottom={35}
          customMarginRight={20}
          urlKey={urlKey}
          timeRange={timeRange}
          setTimeRange={setTimeRange}
          data={formatData(histogram)} />
    case "line":
    default:
      return <SimpleLineChart
          height={200}
          lineWidth={1}
          customMarginBottom={35}
          customMarginRight={20}
          urlKey={urlKey}
          timeRange={timeRange}
          setTimeRange={setTimeRange}
          data={formatData(histogram)} />
  }

}

export default ClientActivityHistogram;