import React, {useEffect, useState} from "react";
import Dot11Service from "../../../services/Dot11Service";
import LoadingSpinner from "../../misc/LoadingSpinner";
import SimpleBarChart from "../../widgets/charts/SimpleBarChart";

const dot11Service = new Dot11Service();

// A single bucket is too narrow to see, so its highlight reaches a share of the shown time to each side.
const highlightPadding = (histogram, timeRange) => {
  const buckets = Object.values(histogram.buckets).map((bucket) => new Date(bucket.bucket).getTime());

  let shownMs = buckets.length > 0 ? Math.max(...buckets) - Math.min(...buckets) : 0;
  if (timeRange.type === "relative") {
    shownMs = timeRange.minutes * 60000;
  } else if (timeRange.type === "absolute") {
    shownMs = new Date(timeRange.to).getTime() - new Date(timeRange.from).getTime();
  }

  return Math.max(histogram.bucket_size_ms / 2, shownMs * 0.005);
}

export default function ChannelSwitchMonitorChart({monitoredNetworkId, simulatedBurstThreshold, tapUuid, timeRange,
                                                   setTimeRange, urlKey, revision}) {

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    if (!tapUuid) {
      return;
    }

    let active = true;

    const onLoaded = (response) => {
      if (active) {
        setHistogram(response);
      }
    }

    setHistogram(null);
    if (simulatedBurstThreshold) {
      dot11Service.getChannelSwitchMonitorSimulationHistogram(monitoredNetworkId, simulatedBurstThreshold, timeRange,
          [tapUuid], onLoaded);
    } else {
      dot11Service.getChannelSwitchMonitorHistogram(monitoredNetworkId, timeRange, [tapUuid], onLoaded);
    }

    return () => {
      active = false;
    }
  }, [monitoredNetworkId, simulatedBurstThreshold, tapUuid, timeRange, revision]);

  if (!tapUuid) {
    return null;
  }

  if (!histogram) {
    return (
        <div className="mt-3">
          <LoadingSpinner />
        </div>
    )
  }

  const padding = highlightPadding(histogram, timeRange);
  const data = {};
  const highlights = [];
  Object.keys(histogram.buckets).sort().forEach((key) => {
    const bucket = histogram.buckets[key];
    data[key] = bucket.frame_count;

    if (bucket.highlighted_frame_count > 0) {
      const timestamp = new Date(bucket.bucket).getTime();
      const previous = highlights[highlights.length - 1];

      // Overlapping highlights would add up to a solid block that hides the bars.
      if (previous && timestamp - padding <= previous.to) {
        previous.to = timestamp + padding;
      } else {
        highlights.push({from: timestamp - padding, to: timestamp + padding});
      }
    }
  });

  const shapes = highlights.map((highlight) => {
    return {
      type: "rect",
      xref: "x",
      yref: "paper",
      layer: "below",
      x0: highlight.from,
      y0: 0,
      x1: highlight.to,
      y1: 1,
      fillcolor: "#EF233C",
      opacity: 0.3,
      line: {width: 0}
    }
  });

  return <SimpleBarChart height={200}
                         lineWidth={1}
                         customMarginBottom={35}
                         customMarginLeft={45}
                         timeRange={timeRange}
                         setTimeRange={setTimeRange}
                         urlKey={urlKey}
                         data={data}
                         shapes={shapes} />

}
