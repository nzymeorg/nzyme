import React, {useContext, useEffect, useState} from "react";
import AssetsService from "../../../../services/ethernet/AssetsService";
import {DEFAULT_LIMIT} from "../../../widgets/LimitSelector";
import {TapContext} from "../../../../App";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import ThreeColumnHistogram from "../../../widgets/histograms/ThreeColumnHistogram";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {ARP_FILTER_FIELDS} from "./ARPFilterFields";

const assetsService = new AssetsService();

export default function ARPResponderPairsHistogram({timeRange, filters, setFilters, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [data, setData] = useState(null);

  const [orderColumn, setOrderColumn] = useState("value3");
  const [orderDirection, setOrderDirection] = useState("DESC");

  useEffect(() => {
    setData(null);
    assetsService.getArpResponderPairs(organizationId, tenantId, timeRange, orderColumn, orderDirection, filters, limit, 0, selectedTaps, setData);
  }, [organizationId, tenantId, orderColumn, orderDirection, selectedTaps, limit, filters, timeRange, revision])

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.total === 0) {
    return (
        <div className="alert alert-info mb-0 mt-2">
          No ARP responses recorded.
        </div>
    )
  }

  return <ThreeColumnHistogram data={data}
                               columnTitles={["ARP Sender", "ARP Target", "Requests"]}
                               columnFilterElements={[
                                 {field: "arp_sender_mac", fields: ARP_FILTER_FIELDS, setFilters: setFilters},
                                 {field: "arp_target_mac", fields: ARP_FILTER_FIELDS, setFilters: setFilters},
                                 null
                               ]}
                               customChartMarginLeft={250}
                               orderColumn={orderColumn}
                               setOrderColumn={setOrderColumn}
                               orderDirection={orderDirection}
                               setOrderDirection={setOrderDirection}
                               limit={limit}
                               setLimit={setLimit} />

}