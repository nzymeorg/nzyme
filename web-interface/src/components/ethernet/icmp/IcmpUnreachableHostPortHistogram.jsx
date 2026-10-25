import React, {useEffect, useState} from "react";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import ThreeColumnHistogram from "../../widgets/histograms/ThreeColumnHistogram";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import IcmpService from "../../../services/ethernet/IcmpService";
import {ICMP_UNREACHABLES_FILTER_FIELDS} from "./IcmpUnreachablesFilterFields";

const icmpService = new IcmpService();

export default function IcmpUnreachableHostPortHistogram({filters, setFilters, taps, timeRange, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [orderColumn, setOrderColumn] = useState("value2");
  const [orderDirection, setOrderDirection] = useState("DESC");
  const [data, setData] = useState(null);

  useEffect(() => {
    setData(null);
    icmpService.getUnreachablesHostPort(organizationId, tenantId, filters, taps, timeRange, orderColumn, orderDirection, limit, 0, setData);
  }, [organizationId, tenantId, filters, taps, timeRange, orderColumn, orderDirection, limit, revision]);

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.total === 0) {
    return <div className="alert alert-info mb-0 mt-2">No port unreachables in the selected time range.</div>
  }

  return <ThreeColumnHistogram data={data}
                               columnTitles={["Host", "Port", "Packets"]}
                               columnFilterElements={[
                                 {field: "host", valueSubField: "address", fields: ICMP_UNREACHABLES_FILTER_FIELDS, setFilters: setFilters},
                                 {field: "port", fields: ICMP_UNREACHABLES_FILTER_FIELDS, setFilters: setFilters},
                                 null
                               ]}
                               orderColumn={orderColumn}
                               setOrderColumn={setOrderColumn}
                               orderDirection={orderDirection}
                               setOrderDirection={setOrderDirection}
                               orderColumnOneIsKey={true}
                               limit={limit}
                               setLimit={setLimit} />

}
