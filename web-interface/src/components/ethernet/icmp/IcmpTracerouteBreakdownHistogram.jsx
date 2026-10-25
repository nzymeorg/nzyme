import React, {useEffect, useState} from "react";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import TwoColumnHistogram from "../../widgets/histograms/TwoColumnHistogram";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import IcmpService from "../../../services/ethernet/IcmpService";
import {ICMP_TRACEROUTES_FILTER_FIELDS} from "./IcmpTraceroutesFilterFields";

const icmpService = new IcmpService();

// by: SENDER | DESTINATION | PROTOCOL. The first two are addresses that link back into the filters by their own
// field; PROTOCOL links via the protocol value carried in the bar's metadata.
export default function IcmpTracerouteBreakdownHistogram({by, columnTitle, filters, setFilters, taps, timeRange, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [orderColumn, setOrderColumn] = useState("value");
  const [orderDirection, setOrderDirection] = useState("DESC");
  const [data, setData] = useState(null);

  useEffect(() => {
    setData(null);
    icmpService.getTraceroutesBreakdown(organizationId, tenantId, by, filters, taps, timeRange, orderColumn, orderDirection, limit, 0, setData);
  }, [organizationId, tenantId, by, limit, orderColumn, orderDirection, filters, taps, timeRange, revision]);

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.total === 0) {
    return <div className="alert alert-info mb-0 mt-2">No traceroutes in the selected time range.</div>
  }

  const filterElement = by === "PROTOCOL"
    ? {field: "protocol", metadataField: "protocol", fields: ICMP_TRACEROUTES_FILTER_FIELDS, setFilters: setFilters}
    : {field: by === "SENDER" ? "source_address" : "destination_address", valueSubField: "address",
       fields: ICMP_TRACEROUTES_FILTER_FIELDS, setFilters: setFilters};

  return <TwoColumnHistogram data={data}
                             columnTitles={[columnTitle, "Traceroutes"]}
                             columnFilterElements={[filterElement, null]}
                             customChartMarginLeft={140}
                             orderColumn={orderColumn}
                             setOrderColumn={setOrderColumn}
                             orderDirection={orderDirection}
                             setOrderDirection={setOrderDirection}
                             limit={limit}
                             setLimit={setLimit} />

}
