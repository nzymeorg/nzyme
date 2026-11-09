import React, {useContext, useEffect, useState} from "react";
import L4Service from "../../../services/ethernet/L4Service";
import {TapContext} from "../../../App";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import {L4_SESSIONS_FILTER_FIELDS} from "./L4SessionFilterFields";
import ThreeColumnHistogram from "../../widgets/histograms/ThreeColumnHistogram";

const l4Service = new L4Service();

export default function L4SessionsDurationHistogram({filters, setFilters, timeRange, revision}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [data, setData] = useState(null);

  useEffect(() => {
    setData(null);
    l4Service.getDurationHistogram(selectedTaps, filters, timeRange, setData)
  }, [selectedTaps, filters, timeRange, revision])

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.values.every((v) => v.column_two.value === 0)) {
    return (
        <div className="alert alert-info mb-0 mt-2">
          No sessions matching filters recorded.
        </div>
    )
  }

  return <ThreeColumnHistogram data={data}
                               columnFilterElements={[
                                 {field: "duration", fields: L4_SESSIONS_FILTER_FIELDS, setFilters: setFilters, metadataField: "filter_value_ms", metadataOperatorField: "filter_operator"},
                                 null,
                                 null
                               ]}
                               columnTitles={["Duration", "Sessions", "Traffic"]}
                               customChartColumnValue="column_two"
                               customChartMarginLeft={160} />

}
