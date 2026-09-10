import React from 'react'
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import SimpleLineChart from "../../widgets/charts/SimpleLineChart";

export default function DNSStatisticsChart({data, timeRange, setTimeRange, conversion = undefined, valueType = undefined}) {

  const formatData = () => {
    const result = {}

    Object.keys(data).sort().forEach(function (key) {
      if (conversion) {
        result[key] = conversion(data[key])
      } else {
        result[key] = data[key]
      }
    })

    return result
  }

  if (data === null || data === undefined) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  return <SimpleLineChart
        height={150}
        lineWidth={1}
        customMarginLeft={45}
        data={formatData()}
        ticksuffix={valueType ? ' ' + valueType : undefined}
        tickformat={'.2~f'}
        timeRange={timeRange}
        setTimeRange={setTimeRange}
    />
}