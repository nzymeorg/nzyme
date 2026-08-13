import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {DEFAULT_LIMIT} from "../../../widgets/LimitSelector";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import ThreeColumnHistogram from "../../../widgets/histograms/ThreeColumnHistogram";
import NATService from "../../../../services/ethernet/NATService";
import {NAT_TRAVERSAL_DISCOVERY_FILTER_FIELDS} from "./NATTraversalDiscoveryFilterFields";

const natService = new NATService();

export default function NATTraversalDiscoveryTopServersHistogram({filters, setFilters, timeRange, revision}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [data, setData] = useState(null);

  useEffect(() => {
    setData(null);
    natService.getTraversalTopServersHistogram(organizationId, tenantId, timeRange, filters, selectedTaps, limit, 0, setData);
  }, [organizationId, tenantId, selectedTaps, limit, filters, timeRange, revision])

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.total === 0) {
    return (
      <div className="alert alert-info mb-0 mt-2">
        No NAT discovery attempts were observed during selected time range.
      </div>
    )
  }

  return <ThreeColumnHistogram data={data}
                               columnFilterElements={[
                                 {field: "destination_address", valueSubField: "address", fields: NAT_TRAVERSAL_DISCOVERY_FILTER_FIELDS, setFilters: setFilters},
                                 null, null
                               ]}
                               columnTitles={["Server Address", "Server Asset", "Discoveries"]}
                               limit={limit}
                               setLimit={setLimit} />

}