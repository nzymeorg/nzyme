import React, {useContext, useEffect, useState} from "react";
import VpnService from "../../../../services/ethernet/VpnService";
import {TapContext} from "../../../../App";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../../misc/Paginator";
import VPNTunnelsTableRow from "./VPNTunnelsTableRow";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import numeral from "numeral";
import ColumnSorting from "../../../shared/ColumnSorting";

const vpnService = new VpnService();

export default function VPNTunnelsTable(props) {

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

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  useEffect(() => {
    setData(null);
    vpnService.findAllTunnels(organizationId, tenantId, timeRange, filters, orderColumn, orderDirection, selectedTaps, perPage, (page-1)*perPage, setData);
  }, [organizationId, tenantId, selectedTaps, filters, orderColumn, orderDirection, timeRange, page, revision]);

  if (!data) {
    return <GenericWidgetLoadingSpinner height={150} />
  }

  if (data.tunnels.length === 0) {
    return <div className="mb-0 alert alert-info">No VPN tunnels were observed during selected time range.</div>
  }

  return (
      <React.Fragment>
        <strong>Total:</strong> {numeral(data.total).format("0,0")}

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th>Tunnel ID {columnSorting("tunnel_id")}</th>
            <th>Client Address {columnSorting("client_address")}</th>
            <th>Client MAC {columnSorting("client_mac")}</th>
            <th>Server Address {columnSorting("server_address")}</th>
            <th>Server MAC {columnSorting("server_mac")}</th>
            <th>Type {columnSorting("type")}</th>
            <th>Status {columnSorting("connection_status")}</th>
            <th>Bytes {columnSorting("tunneled_bytes")}</th>
            <th>Duration {columnSorting("duration")}</th>
            <th>Established At {columnSorting("established_at")}</th>
          </tr>
          </thead>
          <tbody>
          {data.tunnels.map((tunnel, i) => {
            return <VPNTunnelsTableRow tunnel={tunnel} key={i} setFilters={setFilters} />
          })}
          </tbody>
        </table>

        <Paginator itemCount={data.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}
