import React, {useEffect, useState} from "react";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import TwoColumnHistogram from "../../widgets/histograms/TwoColumnHistogram";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import IcmpService from "../../../services/ethernet/IcmpService";
import {ICMP_UNREACHABLES_FILTER_FIELDS} from "./IcmpUnreachablesFilterFields";

const icmpService = new IcmpService();

// by: PORT | HOST | SOURCE. PORT carries its filter value in the bar metadata; the two address
// breakdowns carry it in the value sub field.
export default function IcmpUnreachableBreakdownHistogram({by, columnTitle, filters, setFilters, taps, timeRange, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [orderColumn, setOrderColumn] = useState("value");
  const [orderDirection, setOrderDirection] = useState("DESC");
  const [data, setData] = useState(null);

  useEffect(() => {
    setData(null);
    icmpService.getUnreachablesBreakdown(organizationId, tenantId, by, filters, taps, timeRange, orderColumn, orderDirection, limit, 0, setData);
  }, [organizationId, tenantId, by, limit, orderColumn, orderDirection, filters, taps, timeRange, revision]);

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.total === 0) {
    return <div className="alert alert-info mb-0 mt-2">No port unreachables in the selected time range.</div>
  }

  const filterElement = by === "PORT"
    ? {field: "port", metadataField: "port", fields: ICMP_UNREACHABLES_FILTER_FIELDS, setFilters: setFilters}
    : {field: by === "HOST" ? "host" : "source", valueSubField: "address",
       fields: ICMP_UNREACHABLES_FILTER_FIELDS, setFilters: setFilters};

  return <TwoColumnHistogram data={data}
                             columnTitles={[columnTitle, "Packets"]}
                             columnFilterElements={[filterElement, null]}
                             customChartMarginLeft={140}
                             orderColumn={orderColumn}
                             setOrderColumn={setOrderColumn}
                             orderDirection={orderDirection}
                             setOrderDirection={setOrderDirection}
                             limit={limit}
                             setLimit={setLimit} />

}
