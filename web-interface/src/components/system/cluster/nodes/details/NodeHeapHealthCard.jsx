import React, {useEffect, useState} from "react";
import numeral from "numeral";
import ClusterService from "../../../../../services/ClusterService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import SimpleLineChart from "../../../../widgets/charts/SimpleLineChart";
import MultiLineChart from "../../../../widgets/charts/MultiLineChart";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import {Presets} from "../../../../shared/timerange/TimeRange";
import Store from "../../../../../util/Store";

const clusterService = new ClusterService()

// The values at which the Node Heap health indicator triggers.
const FULL_AFTER_COLLECTION_PERCENT = 95
const PAUSE_TIME_PERCENT = 10
const MEMORY_PRESSURE_PERCENT = 10

// undefined while loading, null if the node has not reported the metric in the last 24 hours.
function useGaugeHistogram(nodeId, metricName) {
  const [values, setValues] = useState(undefined)

  useEffect(() => {
    const controller = new AbortController()
    setValues(undefined)

    // A request that fails for another reason keeps the chart it had. The next one replaces it.
    const load = () => clusterService.findGaugeMetricHistogramOfNode(
        nodeId,
        metricName,
        (histogram) => setValues(histogram.values),
        (error) => {
          if (error.response && error.response.status === 404) {
            setValues(null)
          }
        },
        controller.signal
    )

    load()
    const id = setInterval(load, 30000)

    return () => {
      controller.abort()
      clearInterval(id)
    }
  }, [nodeId, metricName])

  return values
}

function series(values, field) {
  const result = {}
  Object.keys(values).forEach((key) => { result[key] = values[key][field] })

  return result
}

function highest(data) {
  return Object.values(data).reduce((max, value) => Math.max(max, value), 0)
}

function triggerLabel(percent, text) {
  return [{
    xref: "paper", x: 0, xanchor: "left", yref: "y", y: percent, yanchor: "bottom",
    text: text, showarrow: false, font: {size: 10}
  }]
}

function lastTenMinutes(label, value, missing) {
  if (value === null || value === undefined) {
    return <p className="text-muted mb-0">{missing}</p>
  }

  return <p className="text-muted mb-0">{label}: {numeral(value).format("0.0")}%</p>
}

const HeapChart = React.memo(function HeapChart(props) {
  const used = useGaugeHistogram(props.nodeId, "heap_bytes_used")
  const maximum = useGaugeHistogram(props.nodeId, "heap_bytes_total")
  const afterCollection = useGaugeHistogram(props.nodeId, "heap_after_gc_percent")

  if (used === undefined || maximum === undefined || afterCollection === undefined) {
    return <LoadingSpinner />
  }

  if (!used || !maximum) {
    return <div className="alert alert-info mt-2 mb-0">No measurements in the last 24 hours.</div>
  }

  const inUse = {}
  Object.keys(used).forEach((key) => {
    if (maximum[key] && maximum[key].average > 0) {
      inUse[key] = used[key].average * 100.0 / maximum[key].average
    }
  })

  const data = {in_use: inUse}
  if (afterCollection) {
    data.after_collection = series(afterCollection, "minimum")
  }

  return <MultiLineChart
      height={240}
      data={data}
      seriesNames={{in_use: "In Use", after_collection: "After Collection"}}
      colors={Store.get("dark_mode") ? {in_use: "#9baee5", after_collection: "#e8c95a"} : undefined}
      timeRange={Presets.RELATIVE_HOURS_24}
      yRange={[0, 108]}
      lineWidth={1.5}
      legend={{orientation: "h", x: 0, y: 1.02, yanchor: "bottom"}}
      customMarginLeft={50}
      customMarginRight={10}
      customMarginTop={30}
      ticksuffix="%"
      tickformat={".1f"}
      shapes={[{
        type: "line", xref: "paper", x0: 0, x1: 1, yref: "y",
        y0: FULL_AFTER_COLLECTION_PERCENT, y1: FULL_AFTER_COLLECTION_PERCENT,
        line: {color: Store.get("dark_mode") ? "#f9f9f9" : "#111111", width: 1, dash: "dash"}
      }]}
      annotations={triggerLabel(FULL_AFTER_COLLECTION_PERCENT, "Indicator triggers (after collection)")}
  />
})

const TimeLostChart = React.memo(function TimeLostChart(props) {
  const values = useGaugeHistogram(props.nodeId, props.metricName)

  if (values === undefined) {
    return <LoadingSpinner />
  }

  if (!values) {
    return <div className="alert alert-info mt-2 mb-0">{props.noData}</div>
  }

  const data = series(values, "average")

  // The trigger stays in view on a healthy node and peaks are never cut off.
  const top = Math.min(100, Math.max(props.trigger * 1.5, Math.ceil(highest(data) * 1.1)))

  return <SimpleLineChart
      height={240}
      data={data}
      timeRange={Presets.RELATIVE_HOURS_24}
      yRange={[0, top]}
      customMarginLeft={50}
      customMarginRight={10}
      customMarginTop={30}
      ticksuffix="%"
      tickformat={".1f"}
      horizontalLines={[{y: props.trigger}]}
      annotations={triggerLabel(props.trigger, "Indicator triggers")}
  />
})

function NodeHeapHealthCard(props) {

  const node = props.node

  return (
      <div className="card">
        <div className="card-body">
          <CardTitleWithControls title="JVM Heap and Garbage Collection"
                                 fixedAppliedTimeRange={Presets.RELATIVE_HOURS_24}
                                 helpLink="https://go.nzyme.org/node-heap-charts" />

          <div className="row">
            <div className="col-12 col-xxl-4">
              <h4>Heap in Use</h4>
              {lastTenMinutes("Last 10 minutes, lowest after collection", node.heap_after_gc_percent,
                  "No garbage collection in the last 10 minutes.")}
              <HeapChart nodeId={node.uuid} />
            </div>

            <div className="col-12 col-xxl-4">
              <h4>Time in Garbage Collection Pauses</h4>
              {lastTenMinutes("Last 10 minutes", node.gc_pause_time_percent,
                  "No measurements in the last 10 minutes.")}
              <TimeLostChart nodeId={node.uuid}
                             metricName="gc_pause_time_percent"
                             trigger={PAUSE_TIME_PERCENT}
                             noData="No measurements in the last 24 hours." />
            </div>

            <div className="col-12 col-xxl-4">
              <h4>Time Stalled Waiting for Memory</h4>
              {lastTenMinutes("Last 10 minutes", node.memory_pressure_percent,
                  "Not measured in the last 10 minutes.")}
              <TimeLostChart nodeId={node.uuid}
                             metricName="memory_pressure_percent"
                             trigger={MEMORY_PRESSURE_PERCENT}
                             noData="No measurements in the last 24 hours. The host of this node may not provide pressure stall information." />
            </div>
          </div>
        </div>
      </div>
  )

}

export default NodeHeapHealthCard;
