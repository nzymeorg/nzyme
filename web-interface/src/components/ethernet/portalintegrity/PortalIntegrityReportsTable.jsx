import React, {useContext, useEffect, useState} from "react";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {TapContext} from "../../../App";
import PortalIntegrityService from "../../../services/ethernet/PortalIntegrityService";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {STUN_CONNECTIONS_FILTER_FIELDS} from "../nat/traversal/stun_connections/STUNConnectionsFilterFields";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import {PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS} from "./PortalIntegrityReportsFilterFields";

const portalIntegrityService = new PortalIntegrityService();

export default function PortalIntegrityReportsTable({timeRange, filters, setFilters, revision, perPage}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [orderColumn, setOrderColumn] = useState("probed_at");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [data, setData] = useState(null);

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const perPageSel = perPage ? perPage : 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setData(null);
    portalIntegrityService.findAllReports(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, selectedTaps, perPageSel, (page-1)*perPageSel, setData);
  }, [organizationId, tenantId, selectedTaps, timeRange, filters, orderColumn, orderDirection, page, revision]);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const macFilter = (address, fieldName) => {
    if (!address) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters}
                            fields={PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS}
                            field={fieldName}
                            value={address.address} />
  }

  if (!data) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  /*if (data.total === 0) {
    return <div className="mb-0 alert alert-info">No portal integrity reports were found in the selected time range.</div>
  }*/

  return null;

}