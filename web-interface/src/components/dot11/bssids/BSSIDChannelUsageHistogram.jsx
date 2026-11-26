import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../App";
import Dot11Service from "../../../services/Dot11Service";
import LoadingSpinner from "../../misc/LoadingSpinner";
import ActiveChannelsChart from "../util/ActiveChannelsChart";

const dot11Service = new Dot11Service();

function BSSIDChannelUsageHistogram(props) {

  const bssid = props.bssid;
  const timeRange = props.timeRange;

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    dot11Service.getBSSIDActiveChannelHistogram(bssid, timeRange, selectedTaps, setHistogram);
  }, [bssid, timeRange, selectedTaps])

  if (!histogram) {
    return <LoadingSpinner />
  }

  return <ActiveChannelsChart histogram={histogram} />

}

export default BSSIDChannelUsageHistogram;
