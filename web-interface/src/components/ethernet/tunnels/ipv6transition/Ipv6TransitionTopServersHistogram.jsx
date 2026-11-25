import React, {useContext, useEffect, useState} from "react";
import {DEFAULT_LIMIT} from "../../../widgets/LimitSelector";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import ThreeColumnHistogram from "../../../widgets/histograms/ThreeColumnHistogram";
import Ipv6TransitionService from "../../../../services/ethernet/Ipv6TransitionService";
import {IPV6_TRANSITION_FILTER_FIELDS} from "./Ipv6TransitionFilterFields";

const ipv6TransitionService = new Ipv6TransitionService();

export default function Ipv6TransitionTopServersHistogram({timeRange, filters, setFilters, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [histogram, setHistogram] = useState(null);

  const [orderColumn, setOrderColumn] = useState("value1");
  const [orderDirection, setOrderDirection] = useState("DESC");

  useEffect(() => {
    setHistogram(null);

    ipv6TransitionService.getTopServersHistogram(
      setHistogram, organizationId, tenantId, timeRange, orderColumn, orderDirection, limit, 0, filters, selectedTaps
    );
  }, [selectedTaps, organizationId, tenantId, limit, timeRange, filters, orderColumn, orderDirection, revision]);

  if (!histogram) {
    return <LoadingSpinner />
  }

  if (histogram.total === 0) {
    return (
      <div className="alert alert-info mb-0 mt-2">
        No IPv6 transition tunnels recorded.
      </div>
    )
  }

  return <ThreeColumnHistogram data={histogram}
                               columnTitles={["Server", "Connections", "Bytes Exchanged"]}
                               columnFilterElements={[
                                 {field: "server_address", valueSubField: "address", fields: IPV6_TRANSITION_FILTER_FIELDS, setFilters: setFilters},
                                 null, null
                               ]}
                               orderColumn={orderColumn}
                               setOrderColumn={setOrderColumn}
                               orderDirection={orderDirection}
                               setOrderDirection={setOrderDirection}
                               orderColumnOneIsKey={true}
                               limit={limit}
                               setLimit={setLimit} />

}
