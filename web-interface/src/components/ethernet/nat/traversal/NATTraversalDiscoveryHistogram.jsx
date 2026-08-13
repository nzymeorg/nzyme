import React from 'react'
import LoadingSpinner from "../../../misc/LoadingSpinner";
import SimpleBarChart from "../../../widgets/charts/SimpleBarChart";

const STATUS_SERIES = [
  { key: 'complete', name: 'Complete' },
  { key: 'incomplete', name: 'Incomplete', color: '#d99a00' },
  { key: 'error', name: 'Error', color: '#cf3b3b' },
]

export default function NATTraversalDiscoveryHistogram({data, setTimeRange}) {
  if (data === null) {
    return <LoadingSpinner />
  }

  const timestamps = Object.keys(data).sort((a, b) => new Date(a) - new Date(b))
  const x = timestamps.map(ts => new Date(ts))

  const finalData = STATUS_SERIES.map(series => ({
    name: series.name,
    x: x,
    y: timestamps.map(ts => (data[ts] ? (data[ts][series.key] ?? 0) : 0)),
    type: 'bar',
    marker: { color: series.color },
  }))

  return (
    <SimpleBarChart
      finalData={finalData}
      lineWidth={1}
      height={200}
      data={data}
      setTimeRange={setTimeRange}
    />
  )
}