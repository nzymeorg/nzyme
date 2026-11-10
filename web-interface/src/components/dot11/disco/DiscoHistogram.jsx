import React, {useContext, useEffect, useState} from "react";
import Dot11Service from "../../../services/Dot11Service";
import {TapContext} from "../../../App";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import DiscoFramesChart from "./DiscoFramesChart";

const dot11Service = new Dot11Service();

export default function DiscoHistogram({timeRange, setTimeRange, bssids, urlKey = undefined}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    setHistogram(null);
    dot11Service.getDiscoHistogram(timeRange, null, selectedTaps, bssids, null, setHistogram);
  }, [timeRange, selectedTaps, bssids]);

  if (!histogram) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  return <DiscoFramesChart histogram={histogram} timeRange={timeRange} setTimeRange={setTimeRange} urlKey={urlKey} />

}
