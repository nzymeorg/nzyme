import React, {useEffect, useState} from "react";
import numeral from "numeral";
import ClusterService from "../../../../../services/ClusterService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
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

const BUCKET_MS = 15 * 60 * 1000

function translucent(hex, alpha) {
  const value = parseInt(hex.slice(1), 16)
  return `rgba(${(value >> 16) & 255}, ${(value >> 8) & 255}, ${value & 255}, ${alpha})`
}

function bucketOf(buckets, key) {
  const start = Math.floor(new Date(key).getTime() / BUCKET_MS) * BUCKET_MS
  if (!buckets.has(start)) {
    buckets.set(start, {start: start, low: null, high: null, line: null, sum: 0, count: 0})
  }

  return buckets.get(start)
}

function widen(bucket, value) {
  bucket.low = bucket.low === null ? value : Math.min(bucket.low, value)
  bucket.high = bucket.high === null ? value : Math.max(bucket.high, value)
}

function heapBuckets(used, maximum, afterCollection) {
  const buckets = new Map()

  Object.keys(used).forEach((key) => {
    if (maximum[key] && maximum[key].average > 0) {
      widen(bucketOf(buckets, key), used[key].average * 100.0 / maximum[key].average)
    }
  })

  Object.keys(afterCollection || {}).forEach((key) => {
    const bucket = bucketOf(buckets, key)
    const percent = afterCollection[key].minimum
    bucket.line = bucket.line === null ? percent : Math.min(bucket.line, percent)
  })

  // The heap is at its lowest right after a collection, and the samples rarely hit that moment.
  buckets.forEach((bucket) => {
    if (bucket.low !== null && bucket.line !== null) {
      bucket.low = Math.min(bucket.low, bucket.line)
    }
  })

  return buckets
}

function averageBuckets(values) {
  const buckets = new Map()

  Object.keys(values).forEach((key) => {
    const bucket = bucketOf(buckets, key)
    widen(bucket, values[key].average)
    bucket.sum += values[key].average
    bucket.count += 1
    bucket.line = bucket.sum / bucket.count
  })

  return buckets
}

function stretchesOf(buckets) {
  const stretches = []

  Array.from(buckets.values()).sort((a, b) => a.start - b.start).forEach((bucket) => {
    const stretch = stretches[stretches.length - 1]
    if (stretch && bucket.start - stretch[stretch.length - 1].start <= BUCKET_MS) {
      stretch.push(bucket)
    } else {
      stretches.push([bucket])
    }
  })

  return stretches
}

function pointsOf(stretch, hasValue) {
  const points = stretch.filter(hasValue)
  const last = stretch[stretch.length - 1]

  if (points.length > 0 && points[points.length - 1] === last) {
    points.push({...last, start: last.start + BUCKET_MS})
  }

  return points
}

function tracesOf(buckets, rangeName, lineName, colors) {
  const range = {x: [], y: []}
  const peak = {x: [], y: [], low: []}
  const line = {x: [], y: []}
  const single = {x: [], y: []}

  // A gap is a point without a value. The chart component reads the time of every point.
  const gap = (trace, at) => {
    trace.x.push(new Date(at))
    trace.y.push(null)
  }

  stretchesOf(buckets).forEach((stretch) => {
    const end = stretch[stretch.length - 1].start + BUCKET_MS

    const ranged = pointsOf(stretch, (b) => b.high !== null)
    if (ranged.length > 0) {
      ranged.forEach((b) => { range.x.push(new Date(b.start)); range.y.push(b.high) })
      ranged.slice().reverse().forEach((b) => { range.x.push(new Date(b.start)); range.y.push(b.low) })
      gap(range, end)

      ranged.forEach((b) => { peak.x.push(new Date(b.start)); peak.y.push(b.high); peak.low.push(b.low) })
      gap(peak, end)
      peak.low.push(null)
    }

    const lined = pointsOf(stretch, (b) => b.line !== null)
    if (lined.length > 0) {
      lined.forEach((b) => { line.x.push(new Date(b.start)); line.y.push(b.line) })
      gap(line, end)

      if (stretch.filter((b) => b.line !== null).length === 1) {
        single.x.push(new Date(lined[0].start))
        single.y.push(lined[0].line)
      }
    }
  })

  const traces = [
    {
      name: rangeName, legendgroup: "range", x: range.x, y: range.y, type: "scatter", mode: "lines",
      line: {width: 0, color: colors.range}, fill: "toself", fillcolor: translucent(colors.range, colors.fill),
      hoverinfo: "skip"
    },
    {
      legendgroup: "range", showlegend: false, x: peak.x, y: peak.y, customdata: peak.low, type: "scatter",
      mode: "lines", line: {width: 1, color: colors.range},
      hovertemplate: rangeName + ": %{customdata:.1f}% to %{y:.1f}%<extra></extra>"
    }
  ]

  if (line.x.length > 0) {
    traces.push({
      name: lineName, legendgroup: "line", x: line.x, y: line.y, type: "scatter", mode: "lines",
      line: {width: 2, color: colors.line},
      hovertemplate: lineName + ": %{y:.1f}%<extra></extra>"
    })
  }

  if (single.x.length > 0) {
    traces.push({
      legendgroup: "line", showlegend: false, x: single.x, y: single.y, type: "scatter", mode: "markers",
      marker: {size: 5, color: colors.line}, hoverinfo: "skip"
    })
  }

  return traces
}

function rangeChart(buckets, rangeName, lineName, top, trigger, triggerText, darkMode) {
  const colors = darkMode
      ? {range: "#9baee5", line: "#e8c95a", fill: 0.3, trigger: "#f9f9f9"}
      : {range: "#1d30d7", line: "#9467bd", fill: 0.18, trigger: "#111111"}

  return <MultiLineChart
      height={240}
      finalData={tracesOf(buckets, rangeName, lineName, colors)}
      timeRange={Presets.RELATIVE_HOURS_24}
      yRange={[0, top]}
      legend={{orientation: "h", x: 0, y: 1.02, yanchor: "bottom"}}
      customMarginLeft={50}
      customMarginRight={10}
      customMarginTop={30}
      ticksuffix="%"
      tickformat={".1f"}
      shapes={[{
        type: "line", xref: "paper", x0: 0, x1: 1, yref: "y", y0: trigger, y1: trigger,
        line: {color: colors.trigger, width: 1, dash: "dash"}
      }]}
      annotations={[{
        xref: "paper", x: 0, xanchor: "left", yref: "y", y: trigger, yanchor: "bottom",
        text: triggerText, showarrow: false, font: {size: 10}
      }]}
  />
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

  return rangeChart(heapBuckets(used, maximum, afterCollection), "In Use", "After Collection", 108,
      FULL_AFTER_COLLECTION_PERCENT, "Indicator triggers (after collection)", props.darkMode)
})

const TimeLostChart = React.memo(function TimeLostChart(props) {
  const values = useGaugeHistogram(props.nodeId, props.metricName)

  if (values === undefined) {
    return <LoadingSpinner />
  }

  if (!values) {
    return <div className="alert alert-info mt-2 mb-0">{props.noData}</div>
  }

  const buckets = averageBuckets(values)
  const highest = Array.from(buckets.values()).reduce((max, bucket) => Math.max(max, bucket.high), 0)

  // The trigger stays in view on a healthy node and peaks are never cut off.
  const top = Math.min(100, Math.max(props.trigger * 1.5, Math.ceil(highest * 1.1)))

  return rangeChart(buckets, "Range", "Average", top, props.trigger, "Indicator triggers (average)", props.darkMode)
})

function NodeHeapHealthCard(props) {

  const node = props.node

  // The charts are memoized and only pick up a mode change through a prop.
  const darkMode = Boolean(Store.get("dark_mode"))

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
              <HeapChart nodeId={node.uuid} darkMode={darkMode} />
            </div>

            <div className="col-12 col-xxl-4">
              <h4>Time in Garbage Collection Pauses</h4>
              {lastTenMinutes("Last 10 minutes", node.gc_pause_time_percent,
                  "No measurements in the last 10 minutes.")}
              <TimeLostChart nodeId={node.uuid}
                             darkMode={darkMode}
                             metricName="gc_pause_time_percent"
                             trigger={PAUSE_TIME_PERCENT}
                             noData="No measurements in the last 24 hours." />
            </div>

            <div className="col-12 col-xxl-4">
              <h4>Time Stalled Waiting for Memory</h4>
              {lastTenMinutes("Last 10 minutes", node.memory_pressure_percent,
                  "Not measured in the last 10 minutes.")}
              <TimeLostChart nodeId={node.uuid}
                             darkMode={darkMode}
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
