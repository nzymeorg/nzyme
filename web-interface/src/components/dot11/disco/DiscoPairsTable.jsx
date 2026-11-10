import React, {useContext, useEffect, useState} from "react";
import Dot11Service from "../../../services/Dot11Service";
import {TapContext} from "../../../App";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import ThreeColumnHistogram from "../../widgets/histograms/ThreeColumnHistogram";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import {DISCO_FILTER_FIELDS} from "./DiscoFilterFields";

const dot11Service = new Dot11Service();

export default function DiscoPairsTable({timeRange, filters, setFilters, bssids, highlightValue, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [data, setData] = useState(null);

  const [orderColumn, setOrderColumn] = useState("value3");
  const [orderDirection, setOrderDirection] = useState("DESC");

  useEffect(() => {
    setData(null);
    dot11Service.getDiscoPairs(organizationId, tenantId, timeRange, filters, selectedTaps, bssids,
        orderColumn, orderDirection, limit, 0, setData);
  }, [organizationId, tenantId, timeRange, filters, selectedTaps, bssids, orderColumn, orderDirection, limit, revision]);

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.total === 0) {
    return <div className="alert alert-info mb-0 mt-2">No disconnection frames recorded.</div>
  }

  return <ThreeColumnHistogram data={data}
                               columnTitles={["Transmitter", "Receiver", "Frames"]}
                               columnFilterElements={[
                                 setFilters ? {field: "transmitter", fields: DISCO_FILTER_FIELDS, setFilters: setFilters} : null,
                                 setFilters ? {field: "receiver", fields: DISCO_FILTER_FIELDS, setFilters: setFilters} : null,
                                 null
                               ]}
                               customChartMarginLeft={235}
                               orderColumn={orderColumn}
                               setOrderColumn={setOrderColumn}
                               orderDirection={orderDirection}
                               setOrderDirection={setOrderDirection}
                               highlightValue={highlightValue}
                               limit={limit}
                               setLimit={setLimit} />

}
