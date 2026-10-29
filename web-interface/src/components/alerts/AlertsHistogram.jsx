import React, {useEffect, useState} from "react";
import GenericWidgetLoadingSpinner from "../widgets/GenericWidgetLoadingSpinner";
import SimpleLineChart from "../widgets/charts/SimpleLineChart";
import DetectionAlertsService from "../../services/DetectionAlertsService";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";

const alertsService = new DetectionAlertsService();

export default function AlertsHistogram({type, timeRange, setTimeRange, filters, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    setHistogram(null);

    if (type === "active") {
      alertsService.getActiveAlertsHistogram(setHistogram, organizationId, tenantId, timeRange, filters);
    } else {
      alertsService.getNewAlertsHistogram(setHistogram, organizationId, tenantId, timeRange, filters);
    }
  }, [type, organizationId, tenantId, timeRange, filters, revision]);

  if (histogram === null) {
    return <GenericWidgetLoadingSpinner height={200} />
  }

  const formatData = (data) => {
    const result = {}

    Object.keys(data).sort().forEach((key) => {
      result[key] = data[key]
    })

    return result
  }

  return <SimpleLineChart
      height={200}
      lineWidth={1}
      bucketSize={histogram.bucket_size_ms}
      data={formatData(histogram.buckets)}
      timeRange={timeRange}
      setTimeRange={setTimeRange} />

}
