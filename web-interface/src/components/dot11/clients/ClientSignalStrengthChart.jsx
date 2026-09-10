import React, {useContext} from "react";
import {TapContext} from "../../../App";
import {singleTapSelected} from "../../../util/Tools";
import SimpleLineChart from "../../widgets/charts/SimpleLineChart";
import LoadingSpinner from "../../misc/LoadingSpinner";

function ClientSignalStrengthChart({data, timeRange, setTimeRange}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const formatData = function(data) {
    const result = {}

    data.forEach(function(key) {
      result[key["timestamp"]] = key["signal_strength"];
    })

    return result
  }

  if (!singleTapSelected(selectedTaps)) {
    return (
        <div className="alert alert-info mb-0">
          This chart appears only when a single tap is selected. Feeding data from multiple taps would not yield
          meaningful results.
        </div>
    )
  }

  if (!data) {
    return <LoadingSpinner />
  }

  if (data.length === 0) {
    return (
        <div className="alert alert-info mb-0">
          No data recorded in requested time frame.
        </div>
    )
  }

  return <SimpleLineChart
      height={200}
      lineWidth={1}
      customMarginBottom={35}
      customMarginRight={20}
      scattermode="markers"
      setTimeRange={setTimeRange}
      timeRange={timeRange}
      data={formatData(data)} />

}

export default ClientSignalStrengthChart;