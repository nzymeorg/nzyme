import React, {useContext, useEffect, useState} from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Dot11Service from "../../../services/Dot11Service";
import {TapContext} from "../../../App";
import SimpleBarChart from "../../widgets/charts/SimpleBarChart";
import {MonitoredNetworkContext} from "./DiscoPage";

const dot11Service = new Dot11Service();

function DiscoHistogram({discoType, timeRange, setTimeRange, bssids, monitoredNetworkId = undefined, urlKey = undefined}) {

  const monitoredNetworkContext = useContext(MonitoredNetworkContext);

  const [monitoredNetwork, setMonitoredNetwork] = useState(monitoredNetworkId);

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    setHistogram(null);
    dot11Service.getDiscoHistogram(discoType, timeRange, selectedTaps, bssids, monitoredNetwork, setHistogram);
  }, [discoType, timeRange, selectedTaps, monitoredNetwork]);

  useEffect(() => {
    if (monitoredNetworkContext) {
      setMonitoredNetwork(monitoredNetworkContext.network);
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
      urlKey={urlKey}
      data={formatData(histogram)}
  />

}

export default DiscoHistogram;