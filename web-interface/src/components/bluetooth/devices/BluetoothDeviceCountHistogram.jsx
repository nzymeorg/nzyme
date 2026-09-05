import React, {useContext, useEffect, useState} from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import SimpleLineChart from "../../widgets/charts/SimpleLineChart";
import BluetoothService from "../../../services/BluetoothService";
import {TapContext} from "../../../App";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";

const bluetoothService = new BluetoothService();

export default function BluetoothDeviceCountHistogram({timeRange, setTimeRange, filters, revision}) {

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
    bluetoothService.getDeviceCountHistogram(setHistogram, timeRange, filters, selectedTaps)
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

  console.log(histogram);

  return <SimpleLineChart
    height={200}
    lineWidth={1}
    bucketSize={histogram.bucket_size_ms}
    data={formatData(histogram.buckets)}
    setTimeRange={setTimeRange} />

}