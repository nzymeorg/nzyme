import React, {useContext, useEffect, useState} from "react";
import {DEFAULT_LIMIT} from "../../../widgets/LimitSelector";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import ThreeColumnHistogram from "../../../widgets/histograms/ThreeColumnHistogram";
import SocksService from "../../../../services/ethernet/SocksService";
import {SOCKS_FILTER_FIELDS} from "./SOCKSFilterFields";

const socksService = new SocksService();

export default function SOCKSTopServersHistogram({timeRange, filters, setFilters, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [histogram, setHistogram] = useState(null);

  const [orderColumn, setOrderColumn] = useState("value1");
  const [orderDirection, setOrderDirection] = useState("DESC");

  useEffect(() => {
    setHistogram(null);

    socksService.getTopServersHistogram(
      setHistogram, organizationId, tenantId, timeRange, orderColumn, orderDirection, limit, 0, filters, selectedTaps
    );
  }, [selectedTaps, organizationId, tenantId, limit, timeRange, filters, orderColumn, orderDirection, revision]);

  if (!histogram) {
    return <LoadingSpinner />
  }

  if (histogram.total === 0) {
    return (
      <div className="alert alert-info mb-0 mt-2">
        No SOCKS tunnels recorded.
      </div>
    )
  }

  return <ThreeColumnHistogram data={histogram}
                               columnTitles={["Server", "Tunnels", "Bytes Exchanged"]}
                               columnFilterElements={[
                                 {field: "server_address", valueSubField: "address", fields: SOCKS_FILTER_FIELDS, setFilters: setFilters},
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