import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import SimpleLineChart from "../../../widgets/charts/SimpleLineChart";
import Ipv6TransitionService from "../../../../services/ethernet/Ipv6TransitionService";

const ipv6TransitionService = new Ipv6TransitionService();

export default function Ipv6TransitionActiveTunnelsHistogram({timeRange, setTimeRange, filters, revision}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    setHistogram(null);

    ipv6TransitionService.getActiveTunnelCountHistogram(setHistogram, timeRange, filters, selectedTaps)
  }, [timeRange, filters, selectedTaps, revision]);

  if (histogram === null) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  function formatData (data) {
    const result = {}

    Object.keys(data).sort().forEach(function (key) {
      result[key] = data[key]
    })

    return result
  }

  return <SimpleLineChart
    height={200}
    lineWidth={1}
    bucketSize={histogram.bucket_size_ms}
    data={formatData(histogram.buckets)}
    timeRange={timeRange}
    setTimeRange={setTimeRange} />

}
