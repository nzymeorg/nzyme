import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import React from "react";
import SimpleBarChart from "../../../widgets/charts/SimpleBarChart";

export default function NTPTransactionsHistogram({histogram, setTimeRange}) {

  if (!histogram) {
    return <GenericWidgetLoadingSpinner height={200} />;
  }

  const formatData = function(data) {
    const result = {}

    Object.keys(data).sort().forEach(function(key) {
      result[key] = data[key];
    })

    return result
  }

  return (
    <React.Fragment>
      <SimpleBarChart
        height={200}
        lineWidth={1}
        setTimeRange={setTimeRange}
        data={formatData(histogram)} />
    </React.Fragment>
  )

}