import {Link} from "react-router-dom";
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

export default function PortalIntegrityReportsTable({timeRange, filters, setFilters, revision, perPage, taps}) {

  const [organizationId, tenantId] = useSelectedTenant();

  const [orderColumn, setOrderColumn] = useState("probed_at");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [data, setData] = useState(null);

  const tapContext = useContext(TapContext);
  // The reports of one probe are asked for by its tap, whatever the tap selector says.
  const selectedTaps = taps ? taps : tapContext.taps;

  const perPageSel = perPage ? perPage : 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setPage(1);
  }, [organizationId, tenantId, selectedTaps, timeRange, filters]);

  useEffect(() => {
    let current = true;

    setData(null);
    portalIntegrityService.findAllReports(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, selectedTaps, perPageSel, (page-1)*perPageSel, (response) => {
      if (current) {
        setData(response);
      }
    });

    return () => {
      current = false;
    }
  }, [organizationId, tenantId, selectedTaps, timeRange, filters, orderColumn, orderDirection, page, revision]);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters}
                            fields={PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS}
                            field={field}
                            value={value} />
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
          <th>IP Version</th>
          <th>Control URL {columnSorting("control_url")}</th>
          <th>Final URL {columnSorting("last_hop_url")}</th>
          <th>Hop Count {columnSorting("hop_count")}</th>
          <th>Probed At {columnSorting("probed_at")}</th>
        </tr>
        </thead>
        <tbody>
        {data.reports.map((r, i) => {
          const ipVersion = String(r.assigned_address).includes(":") ? 6 : 4;

          return (
            <tr key={i}>
              <td>
                <Link to={ApiRoutes.ETHERNET.PORTAL_INTEGRITY.REPORT_DETAILS(r.uuid)}>
                  <FullCopyShortenedId value={r.uuid} />
                </Link>

                {filterIcon("uuid", r.uuid)}
              </td>
              <td className="text-break">{r.probe_name}{filterIcon("probe_name", r.probe_name)}</td>
              <td>IPv{ipVersion}{filterIcon("ip_version", ipVersion)}</td>
              <td title={r.control_url}>{truncate(r.control_url, 80, false)}{filterIcon("control_url", r.control_url)}</td>
              <td title={r.last_hop_url ? r.last_hop_url : null}>
                {r.last_hop_url ?
                  truncate(r.last_hop_url, 35, false)
                  : <span className="text-muted">n/a</span>}
                {filterIcon("last_hop_url", r.last_hop_url)}
              </td>
              <td>{numeral(r.hop_count).format("0,0")}{filterIcon("hop_count", r.hop_count)}</td>
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