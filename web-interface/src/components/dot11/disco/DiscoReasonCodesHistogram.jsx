import React, {useContext, useEffect, useState} from "react";
import Dot11Service from "../../../services/Dot11Service";
import {TapContext} from "../../../App";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import TwoColumnHistogram from "../../widgets/histograms/TwoColumnHistogram";
import {DISCO_FILTER_FIELDS} from "./DiscoFilterFields";

const dot11Service = new Dot11Service();

export default function DiscoReasonCodesHistogram({timeRange, filters, setFilters, bssids, revision}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [data, setData] = useState(null);

  const [orderColumn, setOrderColumn] = useState("value");
  const [orderDirection, setOrderDirection] = useState("DESC");

  useEffect(() => {
    setData(null);
    dot11Service.getDiscoReasonCodes(timeRange, filters, selectedTaps, bssids, orderColumn, orderDirection, limit, 0, setData);
  }, [timeRange, filters, selectedTaps, bssids, orderColumn, orderDirection, limit, revision]);

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.total === 0) {
    return <div className="alert alert-info mb-0 mt-2">No disconnection frames recorded.</div>
  }

  return <TwoColumnHistogram data={data}
                             columnTitles={["Reason Code", "Frames"]}
                             columnFilterElements={[
                               setFilters ? {field: "reason_code", fields: DISCO_FILTER_FIELDS, setFilters: setFilters, metadataField: "reason_code"} : null,
                               null
                             ]}
                             customChartMarginLeft={350}
                             orderColumn={orderColumn}
                             setOrderColumn={setOrderColumn}
                             orderDirection={orderDirection}
                             setOrderDirection={setOrderDirection}
                             limit={limit}
                             setLimit={setLimit} />

}
