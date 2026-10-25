import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import ColumnSorting from "../../shared/ColumnSorting";
import ApiRoutes from "../../../util/ApiRoutes";
import L4Address from "../shared/L4Address";

export default function ReconTargetsTable({targets, page, setPage, perPage, orderColumn, setOrderColumn,
                                           orderDirection, setOrderDirection}) {

  if (!targets) {
    return <LoadingSpinner />
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  if (targets.targets.length === 0) {
    return <div className="alert alert-info mb-0">
      No hosts were probed in the selected time range. Hosts appear here once any device probes one of their
      ports, whether or not that device crossed a scan threshold.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Target {columnSorting("address")}</th>
          <th>Probing Devices {columnSorting("source_count")}</th>
          <th>Probed Ports {columnSorting("port_count")}</th>
          <th>Unanswered Ports {columnSorting("failed_port_count")}</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {targets.targets.map((t) => {
          return (
            <tr key={t.address}>
              <td>
                <Link to={ApiRoutes.ETHERNET.RECON.TARGETS.DETAILS(t.address)}>
                  {t.address_data ? <L4Address address={t.address_data} hidePort={true} withAssetName /> : t.address}
                </Link>
              </td>
              <td>{numeral(t.source_count).format("0,0")}</td>
              <td>{numeral(t.port_count).format("0,0")}</td>
              <td>
                {numeral(t.failed_port_count).format("0,0")}
                {t.port_count > 0 ?
                  <span className="text-muted"> ({numeral(t.failed_port_count / t.port_count).format("0%")})</span> : null}
              </td>
              <td title={moment(t.first_seen).format()}>{moment(t.first_seen).fromNow()}</td>
              <td title={moment(t.last_seen).format()}>{moment(t.last_seen).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={targets.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
