import React, {useContext, useEffect, useState} from "react";
import Dot11Service from "../../../services/Dot11Service";
import {TapContext} from "../../../App";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import TwoColumnHistogram from "../../widgets/histograms/TwoColumnHistogram";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {DISCO_FILTER_FIELDS} from "./DiscoFilterFields";

const dot11Service = new Dot11Service();

export default function DiscoMacHistogram({histogramType, columnTitle, timeRange, filters, setFilters, bssids, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [data, setData] = useState(null);

  const [orderColumn, setOrderColumn] = useState("value");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const filterField = histogramType === "bssids" ? "bssid" : histogramType.slice(0, -1);

  useEffect(() => {
    setData(null);
    dot11Service.getDiscoMacHistogram(histogramType, organizationId, tenantId, timeRange, filters, selectedTaps,
        bssids, orderColumn, orderDirection, limit, 0, setData);
  }, [histogramType, organizationId, tenantId, timeRange, filters, selectedTaps, bssids, orderColumn, orderDirection, limit, revision]);

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.total === 0) {
    return <div className="alert alert-info mb-0 mt-2">No disconnection frames recorded.</div>
  }

  return <TwoColumnHistogram data={data}
                             columnTitles={[columnTitle, "Frames"]}
                             columnFilterElements={[
                               setFilters ? {field: filterField, fields: DISCO_FILTER_FIELDS, setFilters: setFilters} : null,
                               null
                             ]}
                             orderColumn={orderColumn}
                             setOrderColumn={setOrderColumn}
                             orderDirection={orderDirection}
                             setOrderDirection={setOrderDirection}
                             limit={limit}
                             setLimit={setLimit} />

}
