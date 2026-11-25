import React, {useContext, useEffect, useState} from "react";
import Dot11Service from "../../../services/Dot11Service";
import {TapContext} from "../../../App";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import TwoColumnHistogram from "../../widgets/histograms/TwoColumnHistogram";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {CHANNEL_SWITCH_FILTER_FIELDS} from "./ChannelSwitchFilterFields";

const dot11Service = new Dot11Service();

const HISTOGRAMS = {
  bssids: {columnTitle: "BSSID", filter: {field: "bssid"}},
  channels: {columnTitle: "Announced Channel", filter: {field: "new_channel", metadataField: "new_channel"}}
}

export default function ChannelSwitchHistogram({histogramType, timeRange, filters, setFilters, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [data, setData] = useState(null);

  const [orderColumn, setOrderColumn] = useState("value");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const histogram = HISTOGRAMS[histogramType];

  useEffect(() => {
    let active = true;

    setData(null);
    dot11Service.getChannelSwitchHistogram(histogramType, organizationId, tenantId, timeRange, filters, selectedTaps,
        orderColumn, orderDirection, limit, 0, (response) => {
          if (active) {
            setData(response);
          }
        });

    return () => {
      active = false;
    }
  }, [histogramType, organizationId, tenantId, timeRange, filters, selectedTaps, orderColumn, orderDirection, limit, revision]);

  if (!data) {
    return <GenericWidgetLoadingSpinner height={300} />
  }

  if (data.total === 0) {
    return <div className="alert alert-info mb-0 mt-2">No channel switch announcements recorded.</div>
  }

  return <TwoColumnHistogram data={data}
                             columnTitles={[histogram.columnTitle, "Frames"]}
                             columnFilterElements={[
                               setFilters ? {...histogram.filter, fields: CHANNEL_SWITCH_FILTER_FIELDS, setFilters: setFilters} : null,
                               null
                             ]}
                             orderColumn={orderColumn}
                             setOrderColumn={setOrderColumn}
                             orderDirection={orderDirection}
                             setOrderDirection={setOrderDirection}
                             limit={limit}
                             setLimit={setLimit} />

}
