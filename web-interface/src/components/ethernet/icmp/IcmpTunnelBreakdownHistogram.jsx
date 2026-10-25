import React, {useEffect, useState} from "react";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import TwoColumnHistogram from "../../widgets/histograms/TwoColumnHistogram";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import IcmpService from "../../../services/ethernet/IcmpService";
import {ICMP_TUNNELS_FILTER_FIELDS} from "./IcmpTunnelsFilterFields";

const icmpService = new IcmpService();

// by: PEER | SOURCE. The value is the number of distinct hosts on the other side, so a peer with many sources
// stands out as a likely C2 endpoint. Bars link back by the matching address filter.
export default function IcmpTunnelBreakdownHistogram({by, columnTitle, valueTitle, filters, setFilters, taps, timeRange, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [orderColumn, setOrderColumn] = useState("value");
  const [orderDirection, setOrderDirection] = useState("DESC");
  const [data, setData] = useState(null);

  useEffect(() => {
    setData(null);
    icmpService.getTunnelsBreakdown(organizationId, tenantId, by, filters, taps, timeRange, orderColumn, orderDirection, limit, 0, setData);
  }, [organizationId, tenantId, by, limit, orderColumn, orderDirection, filters, taps, timeRange, revision]);

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.total === 0) {
    return <div className="alert alert-info mb-0 mt-2">No suspected tunnels in the selected time range.</div>
  }

  const filterElement = {field: by === "PEER" ? "destination_address" : "source_address", valueSubField: "address",
    fields: ICMP_TUNNELS_FILTER_FIELDS, setFilters: setFilters};

  return <TwoColumnHistogram data={data}
                             columnTitles={[columnTitle, valueTitle]}
                             columnFilterElements={[filterElement, null]}
                             customChartMarginLeft={140}
                             orderColumn={orderColumn}
                             setOrderColumn={setOrderColumn}
                             orderDirection={orderDirection}
                             setOrderDirection={setOrderDirection}
                             limit={limit}
                             setLimit={setLimit} />

}
