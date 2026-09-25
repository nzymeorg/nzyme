import React, {useContext, useEffect, useState} from "react";
import NATService from "../../../../../services/ethernet/NATService";
import {DEFAULT_LIMIT} from "../../../../widgets/LimitSelector";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import ThreeColumnHistogram from "../../../../widgets/histograms/ThreeColumnHistogram";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import {TapContext} from "../../../../../App";
import {STUN_CONNECTIONS_FILTER_FIELDS} from "./STUNConnectionsFilterFields";

const natService = new NATService();

export default function STUNConnectionsTopClientsHistogram({timeRange, filters, setFilters, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [histogram, setHistogram] = useState(null);

  const [orderColumn, setOrderColumn] = useState("value1");
  const [orderDirection, setOrderDirection] = useState("DESC");

  useEffect(() => {
    setHistogram(null);

    natService.getConnectionsTopClientsHistogram(
      setHistogram, organizationId, tenantId, timeRange, orderColumn, orderDirection, limit, 0, filters, selectedTaps
    );
  }, [selectedTaps, organizationId, tenantId, limit, timeRange, filters, orderColumn, orderDirection, revision]);

  if (!histogram) {
    return <LoadingSpinner />
  }

  if (histogram.total === 0) {
    return (
      <div className="alert alert-info mb-0 mt-2">
        No STUN connection attempts recorded.
      </div>
    )
  }

  return <ThreeColumnHistogram data={histogram}
                               columnTitles={["Address", "Connections", "Bytes Exchanged"]}
                               columnFilterElements={[
                                 {field: "source_address", valueSubField: "address", fields: STUN_CONNECTIONS_FILTER_FIELDS, setFilters: setFilters},
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