import React, {useContext, useEffect, useState} from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Dot11Service from "../../../services/Dot11Service";
import {TapContext} from "../../../App";
import SimpleBarChart from "../../widgets/charts/SimpleBarChart";
import {MonitoredNetworkContext} from "./DiscoPage";

const dot11Service = new Dot11Service();

function DiscoHistogram(props) {

  const monitoredNetworkContext = useContext(MonitoredNetworkContext);

  const discoType = props.discoType;
  const timeRange = props.timeRange;
  const setTimeRange = props.setTimeRange;
  const bssids = props.bssids;

  const [monitoredNetworkId, setMonitoredNetworkId] = useState(props.monitoredNetworkId);

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    setHistogram(null);
    dot11Service.getDiscoHistogram(discoType, timeRange, selectedTaps, bssids, monitoredNetworkId, setHistogram);
  }, [discoType, timeRange, selectedTaps, monitoredNetworkId]);

  useEffect(() => {
    if (monitoredNetworkContext) {
      setMonitoredNetworkId(monitoredNetworkContext.network);
    }
  }, [monitoredNetworkContext]);

  const formatData = function(data) {
    const result = {}

    Object.keys(data).sort().forEach(function(key) {
      result[key] = data[key]["frame_count"];
    })

    return result
  }

  if (!histogram) {
    return <LoadingSpinner />
  }

  return <SimpleBarChart
      height={200}
      lineWidth={1}
      setTimeRange={setTimeRange}
      customMarginBottom={35}
      timeRange={timeRange}
      data={formatData(histogram)}
  />

}

export default DiscoHistogram;