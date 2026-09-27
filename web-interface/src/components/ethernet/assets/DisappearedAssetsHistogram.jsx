import React, {useEffect, useState} from "react";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import AssetsService from "../../../services/ethernet/AssetsService";
import {DEFAULT_LIMIT} from "../../widgets/LimitSelector";
import ThreeColumnHistogram from "../../widgets/histograms/ThreeColumnHistogram";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {ASSET_FILTER_FIELDS} from "./AssetFilterFields";

const assetsService = new AssetsService();

export default function DisappearedAssetsHistogram({timeRange, filters, setFilters, revision}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [limit, setLimit] = useState(DEFAULT_LIMIT);
  const [histogram, setHistogram] = useState(null);

  useEffect(() => {
    setHistogram(null);
    assetsService.getRecentlyDisappearedAssetsHistogram(organizationId, tenantId, timeRange, filters, limit, 0, setHistogram);
  }, [timeRange, filters, organizationId, tenantId, revision, limit]);

  if (histogram === null) {
    return <GenericWidgetLoadingSpinner height={300}/>
  }

  if (histogram.total === 0) {
    return (
      <div className="alert alert-info mb-0 mt-2">
        No assets found.
      </div>
    )
  }

  return <ThreeColumnHistogram data={histogram}
                               columnTitles={["Asset", "Hostname", "Last Seen"]}
                               columnFilterElements={[
                                 {field: "mac", fields: ASSET_FILTER_FIELDS, setFilters: setFilters},
                                 {field: "hostname", fields: ASSET_FILTER_FIELDS, setFilters: setFilters},
                                 null
                               ]}
                               customChartMarginLeft={250}
                               limit={limit}
                               setLimit={setLimit} />
}