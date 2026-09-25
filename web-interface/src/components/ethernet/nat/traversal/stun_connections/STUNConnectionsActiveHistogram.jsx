import React, {useContext, useEffect, useState} from "react";
import NATService from "../../../../../services/ethernet/NATService";
import {TapContext} from "../../../../../App";
import {disableTapSelector, enableTapSelector} from "../../../../misc/TapSelector";
import GenericWidgetLoadingSpinner from "../../../../widgets/GenericWidgetLoadingSpinner";
import SimpleLineChart from "../../../../widgets/charts/SimpleLineChart";

const natService = new NATService();

export default function STUNConnectionsActiveHistogram({timeRange, setTimeRange, filters, revision}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setHistogram(null);

    natService.getConnectionsActiveHistogram(setHistogram, timeRange, filters, selectedTaps)
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