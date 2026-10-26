import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import ApiRoutes from "../../../util/ApiRoutes";
import L4Address from "../shared/L4Address";

export default function ReconProbedHostsTable({hosts, page, setPage, perPage, orderColumn, setOrderColumn,
                                               orderDirection, setOrderDirection, filterFields, setFilters}) {

  if (!hosts) {
    return <LoadingSpinner />
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters} fields={filterFields} field={field} value={value} />
  }

  if (hosts.hosts.length === 0) {
    return <div className="alert alert-info mb-0">
      No probed hosts match the current filters and time range. Every host that any device tried to reach is
      in this view, so widening the filters shows more.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Host {columnSorting("address")}</th>
          <th>Probing Devices {columnSorting("source_count")}</th>
          <th>Probed Ports {columnSorting("port_count")}</th>
          <th>Unanswered Ports {columnSorting("failed_port_count")}</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {hosts.hosts.map((h) => {
          return (
            <tr key={h.address}>
              <td>
                <Link to={ApiRoutes.ETHERNET.RECON.HOSTS.DETAILS(h.address)}>
                  {h.address_data ? <L4Address address={h.address_data} hidePort={true} withAssetName /> : h.address}
                </Link>
                {filterIcon("address", h.address)}
              </td>
              <td>{numeral(h.source_count).format("0,0")}</td>
              <td>{numeral(h.port_count).format("0,0")}</td>
              <td>
                {numeral(h.failed_port_count).format("0,0")}
                {h.port_count > 0 ?
                  <span className="text-muted"> ({numeral(h.failed_port_count / h.port_count).format("0%")})</span> : null}
              </td>
              <td title={moment(h.first_seen).format()}>{moment(h.first_seen).fromNow()}</td>
              <td title={moment(h.last_seen).format()}>{moment(h.last_seen).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={hosts.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
