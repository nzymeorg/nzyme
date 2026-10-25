import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../App";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import TwoColumnHistogram from "../../widgets/histograms/TwoColumnHistogram";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import IcmpService from "../../../services/ethernet/IcmpService";
import {ICMP_FILTER_FIELDS} from "./IcmpFilterFields";

const icmpService = new IcmpService();

export default function IcmpTypesHistogram({timeRange, filters, setFilters, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();
  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [orderColumn, setOrderColumn] = useState("value");
  const [orderDirection, setOrderDirection] = useState("DESC");
  const [data, setData] = useState(null);

  useEffect(() => {
    setData(null);
    icmpService.getTypes(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection,
      limit, 0, selectedTaps, setData);
  }, [organizationId, tenantId, selectedTaps, limit, orderColumn, orderDirection, filters, timeRange, revision]);

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.total === 0) {
    return <div className="alert alert-info mb-0 mt-2">No ICMP traffic in the selected time range.</div>
  }

  return <TwoColumnHistogram data={data}
                             columnTitles={["Message Type", "Packets"]}
                             columnFilterElements={[
                               {field: "icmp_type", metadataField: "icmp_type", fields: ICMP_FILTER_FIELDS, setFilters: setFilters},
                               null
                             ]}
                             customChartMarginLeft={200}
                             orderColumn={orderColumn}
                             setOrderColumn={setOrderColumn}
                             orderDirection={orderDirection}
                             setOrderDirection={setOrderDirection}
                             limit={limit}
                             setLimit={setLimit} />

}
