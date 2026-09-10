import React, {useContext, useEffect, useState} from 'react';
import {TapContext} from "../../../../../App";
import DNSService from "../../../../../services/ethernet/DNSService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import SimpleBarChart from "../../../../widgets/charts/SimpleBarChart";
import {convertGenericChartData} from "../../../../../util/Tools";

const dnsService = new DNSService();

export default function DNSTransactionCountChart(props) {

  const timeRange = props.timeRange;
  const filters = props.filters;
  const revision = props.revision;
  const setTimeRange = props.setTimeRange;

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    setHistogram(null);
    dnsService.getTransactionCountChart(timeRange, filters, selectedTaps, setHistogram);
  }, [selectedTaps, timeRange, filters, revision])

  if (!histogram) {
    return <LoadingSpinner />
  }

  return <SimpleBarChart
      height={200}
      lineWidth={1}
      customMarginBottom={35}
      setTimeRange={setTimeRange}
      timeRange={timeRange}
      data={convertGenericChartData(histogram)}
  />

}