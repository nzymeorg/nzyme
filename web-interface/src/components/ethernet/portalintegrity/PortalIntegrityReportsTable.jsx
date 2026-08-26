import React, {useContext, useEffect, useState} from "react";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {TapContext} from "../../../App";
import PortalIntegrityService from "../../../services/ethernet/PortalIntegrityService";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import GenericWidgetLoadingSpinner from "../../widgets/GenericWidgetLoadingSpinner";
import {PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS} from "./PortalIntegrityReportsFilterFields";
import numeral from "numeral";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";
import moment from "moment";
import {truncate} from "../../../util/Tools";
import Paginator from "../../misc/Paginator";
import ApiRoutes from "../../../util/ApiRoutes";

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

  if (data.total === 0) {
    return <div className="mb-0 alert alert-info">No portal integrity reports were found in the selected time range.</div>
  }

  return (
    <>
      <strong>Total:</strong> {numeral(data.total).format("0,0")}

      <table className="table table-sm table-hover table-striped mb-4 mt-3">
        <thead>
        <tr>
          <th>ID {columnSorting("uuid")}</th>
          <th>Probe Name {columnSorting("probe_name")}</th>
          <th>Control URL {columnSorting("control_url")}</th>
          <th>Final URL {columnSorting("last_hop_url")}</th>
          <th>Hop Count {columnSorting("hop_count")}</th>
          <th>Probed At {columnSorting("probed_at")}</th>
        </tr>
        </thead>
        <tbody>
        {data.reports.map((r, i) => {
          return (
            <tr key={i}>
              <td>
                <a href={ApiRoutes.ETHERNET.PORTAL_INTEGRITY.REPORT_DETAILS(r.uuid)}>
                  <FullCopyShortenedId value={r.uuid} />
                </a>
              </td>
              <td>{r.probe_name}</td>
              <td>{r.control_url}</td>
              <td title={r.last_hop_url}>{truncate(r.last_hop_url, 35, false)}</td>
              <td>{numeral(r.hop_count).format("0,0")}</td>
              <td title={moment(r.probed_at).format()}>{moment(r.probed_at).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={data.total} perPage={perPageSel} setPage={setPage} page={page} />
    </>
  )

}