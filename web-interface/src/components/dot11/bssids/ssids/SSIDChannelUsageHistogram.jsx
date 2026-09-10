import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import Dot11Service from "../../../../services/Dot11Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import SimpleBarChart from "../../../widgets/charts/SimpleBarChart";
import numeral from "numeral";
import Store from '../../../../util/Store'

const dot11Service = new Dot11Service();

function SSIDChannelUsageHistogram(props) {

  const bssid = props.bssid;
  const ssid = props.ssid;
  const timeRange = props.timeRange;

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    dot11Service.getSSIDOfBSSIDActiveChannelHistogram(bssid, ssid, timeRange, selectedTaps, setHistogram);
  }, [bssid, ssid, timeRange, selectedTaps])

  const formatData = function(data) {
    const x = [];
    const y = [];

    Object.keys(data).forEach(function (i) {
      x.push(data[i]["channel"] + ": " + numeral(data[i]["frequency"]).format("0,0") + " Mhz");
      y.push(data[i]["frames"]);
    });

    return [
      {
        x: x,
        y: y,
        type: 'bar',
        marker: { color: Store.get('dark_mode') ? '#e6e6e6' : '#5c5d6f' }
      }
    ]
  }

  if (!histogram) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <SimpleBarChart
            height={200}
            customMarginBottom={25}
            timeRange={timeRange}
            finalData={formatData(histogram.channels)} />
      </React.Fragment>
  )


}

export default SSIDChannelUsageHistogram;