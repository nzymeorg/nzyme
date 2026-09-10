import React, {useEffect, useState} from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import AssetsService from "../../../services/ethernet/AssetsService";
import SimpleLineChart from "../../widgets/charts/SimpleLineChart";

const assetsService = new AssetsService();

export default function ActiveAssetsHistogram({timeRange, setTimeRange, organizationId, tenantId, revision}) {

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    setHistogram(null);
    assetsService.getActiveAssetsHistogram(organizationId, tenantId, timeRange, setHistogram)
  }, [timeRange, organizationId, tenantId, revision]);

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

  return <SimpleLineChart
    height={200}
    lineWidth={1}
    data={formatData(histogram)}
    timeRange={timeRange}
    setTimeRange={setTimeRange} />

}