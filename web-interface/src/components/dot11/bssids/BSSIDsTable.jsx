import React from "react";
import BSSIDRow from "./BSSIDRow";
import Paginator from "../../misc/Paginator";
import numeral from "numeral";
import ColumnSorting from "../../shared/ColumnSorting";

function BSSIDsTable(props) {

  const bssids = props.bssids;
  const timeRange = props.timeRange;
  const setFilters = props.setFilters;

  const page = props.page;
  const setPage = props.setPage;
  const perPage = props.perPage;

  const setOrderColumn = props.setOrderColumn;
  const orderColumn = props.orderColumn;
  const setOrderDirection = props.setOrderDirection;
  const orderDirection = props.orderDirection;

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  if (bssids.length === 0) {
    return <div className="alert alert-info mb-0">
      No access points discovered yet. Make sure a WiFi-enabled nzyme tap is connected and configured properly.
    </div>
  }

  return (
    <React.Fragment>
      <div className="mb-1">
        Total: {numeral(bssids.total).format("0,0")}
      </div>

      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>BSSID {columnSorting("bssid")}</th>
          <th>Signal Strength {columnSorting("signal_strength_average")}</th>
          <th>Mode</th>
          <th>Advertised SSIDs</th>
          <th style={{width: 90}}>Clients {columnSorting("client_count")}</th>
          <th>Security</th>
          <th>OUI</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {Object.keys(bssids.bssids).map(function (key, i) {
            return <BSSIDRow key={'bssid-' + i} bssid={bssids.bssids[key]} timeRange={timeRange} setFilters={setFilters}/>
          })}
        </tbody>
      </table>

      <Paginator itemCount={bssids.total} perPage={perPage} setPage={setPage} page={page}/>
    </React.Fragment>
  )

}

export default BSSIDsTable;