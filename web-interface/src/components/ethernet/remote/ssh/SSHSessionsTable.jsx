import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import SSHService from "../../../../services/ethernet/SSHService";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../../misc/Paginator";
import SSHSessionsTableRow from "./SSHSessionsTableRow";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import ColumnSorting from "../../../shared/ColumnSorting";
import numeral from "numeral";

const sshService = new SSHService();

export default function SSHSessionsTable(props) {

  const [organizationId, tenantId] = useSelectedTenant();

  const timeRange = props.timeRange;
  const filters = props.filters;
  const setFilters = props.setFilters;
  const revision = props.revision;

  const [orderColumn, setOrderColumn] = useState("established_at");
  const [orderDirection, setOrderDirection] = useState("DESC");

  const [data, setData] = useState(null);

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const perPage = props.perPage ? props.perPage : 25;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setData(null);
    sshService.findAllTunnels(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, selectedTaps, perPage, (page-1)*perPage, setData);
  }, [organizationId, tenantId, selectedTaps, timeRange, filters, orderColumn, orderDirection, page, revision]);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  if (!data) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (data.sessions.length === 0) {
    return <div className="mb-0 alert alert-info">No SSH sessions were observed during selected time range.</div>
  }

  return (
      <React.Fragment>
        <strong>Total:</strong> {numeral(data.total).format("0,0")}

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th>Session ID {columnSorting("session_id")}</th>
            <th>Client Address {columnSorting("client_address")}</th>
            <th>Client MAC {columnSorting("client_mac")}</th>
            <th className="hide-narrow">Client Type {columnSorting("client_type")}</th>
            <th>Server Address {columnSorting("server_address")}</th>
            <th>Server MAC {columnSorting("server_mac")}</th>
            <th className="hide-narrow">Server Type {columnSorting("server_type")}</th>
            <th>Status {columnSorting("connection_status")}</th>
            <th>Bytes {columnSorting("tunneled_bytes")}</th>
            <th>Duration {columnSorting("duration")}</th>
            <th>Established At {columnSorting("established_at")}</th>
          </tr>
          </thead>
          <tbody>
          {data.sessions.map((session, i) => {
            return <SSHSessionsTableRow session={session} key={i} setFilters={setFilters}/>
          })}
          </tbody>
        </table>

        <Paginator itemCount={data.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}