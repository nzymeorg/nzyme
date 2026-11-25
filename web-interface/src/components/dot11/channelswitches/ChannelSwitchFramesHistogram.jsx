import React, {useContext, useEffect, useState} from "react";
import Dot11Service from "../../../services/Dot11Service";
import {TapContext} from "../../../App";
import ChannelSwitchesChart from "./ChannelSwitchesChart";

const dot11Service = new Dot11Service();

export default function ChannelSwitchFramesHistogram({timeRange, setTimeRange, filters, revision}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    let active = true;

    setHistogram(null);
    dot11Service.getChannelSwitchFramesHistogram(timeRange, filters, selectedTaps, null, null, (response) => {
      if (active) {
        setHistogram(response);
      }
    });

    return () => {
      active = false;
    }
  }, [timeRange, filters, selectedTaps, revision]);

  return <ChannelSwitchesChart histogram={histogram} timeRange={timeRange} setTimeRange={setTimeRange} />

}
