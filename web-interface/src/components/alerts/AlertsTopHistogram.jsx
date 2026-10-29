import React, {useEffect, useState} from "react";
import {DEFAULT_LIMIT} from "../widgets/LimitSelector";
import LoadingSpinner from "../misc/LoadingSpinner";
import TwoColumnHistogram from "../widgets/histograms/TwoColumnHistogram";
import DetectionAlertsService from "../../services/DetectionAlertsService";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import {ALERT_FILTER_FIELDS} from "./AlertFilterFields";

const alertsService = new DetectionAlertsService();

export default function AlertsTopHistogram({aggregation, columnTitle, filterField, valueSubField, timeRange, filters, setFilters, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [histogram, setHistogram] = useState(null);

  const [orderColumn, setOrderColumn] = useState("value");
  const [orderDirection, setOrderDirection] = useState("DESC");

  useEffect(() => {
    setHistogram(null);

    alertsService.getTopHistogram(
        aggregation, setHistogram, organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, limit, 0
    );
  }, [aggregation, organizationId, tenantId, limit, timeRange, filters, orderColumn, orderDirection, revision]);

  if (!histogram) {
    return <LoadingSpinner />
  }

  if (histogram.total === 0) {
    return (
        <div className="alert alert-info mb-0 mt-2">
          No alerts match the current filters and time range.
        </div>
    )
  }

  return <TwoColumnHistogram data={histogram}
                             columnTitles={[columnTitle, "Alerts"]}
                             columnFilterElements={[
                               {field: filterField, valueSubField: valueSubField, fields: ALERT_FILTER_FIELDS, setFilters: setFilters},
                               null
                             ]}
                             orderColumn={orderColumn}
                             setOrderColumn={setOrderColumn}
                             orderDirection={orderDirection}
                             setOrderDirection={setOrderDirection}
                             limit={limit}
                             setLimit={setLimit} />

}
