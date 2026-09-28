import React, {useContext, useEffect, useState} from "react";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import {TapContext} from "../../../../App";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import SimpleLineChart from "../../../widgets/charts/SimpleLineChart";
import SocksService from "../../../../services/ethernet/SocksService";

const socksService = new SocksService();

export default function SOCKSActiveTunnelsHistogram({timeRange, setTimeRange, filters, revision}) {

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

    socksService.getActiveTunnelCountHistogram(setHistogram, timeRange, filters, selectedTaps)
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