import React, {useContext, useEffect, useState} from "react";
import Dot11Service from "../../../services/Dot11Service";
import {TapContext} from "../../../App";
import LoadingSpinner from "../../misc/LoadingSpinner";
import SimpleLineChart from "../../widgets/charts/SimpleLineChart";

const dot11Service = new Dot11Service();

function BSSIDAndSSIDChart(props) {

  const parameter = props.parameter;
  const timeRange = props.timeRange;
  const filters = props.filters;
  const revision = props.revision;
  const setTimeRange = props.setTimeRange;
  const monitorsReady = props.monitorsReady;

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    setHistogram(null);
    dot11Service.getBSSIDAndSSIDHistogram(timeRange, filters, selectedTaps, setHistogram);
  }, [selectedTaps, filters, timeRange, revision])

  const formatData = function(data) {
    const result = {}

    Object.keys(data).sort().forEach(function(key) {
      result[key] = data[key][parameter];
    })

    return result
  }

  if (!histogram || monitorsReady === false) {
    return <LoadingSpinner />
  }

  return <SimpleLineChart
          height={200}
          lineWidth={1}
          customMarginBottom={35}
          setTimeRange={setTimeRange}
          bucketSize={histogram.bucket_size_ms}
          data={formatData(histogram.values)} />

}

export default BSSIDAndSSIDChart;