import {Link} from "react-router-dom";
import React from "react";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import numeral from "numeral";
import moment from "moment";
import ApiRoutes from "../../../util/ApiRoutes";
import MonitorStatusIndicator from "./MonitorStatusIndicator";
import monitorTypeToSearchLink from "./MonitorReplay";
import MonitorType from "./MonitorType";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";

export default function MonitorsTable({monitors, page, setPage, perPage, onApplyMonitor = undefined,
                                       showType = false, orderColumn = undefined, setOrderColumn = undefined,
                                       orderDirection = undefined, setOrderDirection = undefined,
                                       filterFields = undefined, setFilters = undefined}) {

  if (!monitors) {
    return <LoadingSpinner />
  }

  const sortable = setOrderColumn && setOrderDirection;
  const filterable = filterFields && setFilters;

  const columnSorting = (columnName) => {
    if (!sortable) {
      return null;
    }

    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    if (!filterable) {
      return null;
    }

    return <FilterValueIcon setFilters={setFilters} fields={filterFields} field={field} value={value} />
  }

  if (monitors.monitors.length === 0) {
    return <div className="alert alert-info mb-0">
      {filterable ? "No monitors match the current filters." : "No monitors configured."}
    </div>
  }

  return (
    <>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th style={{width: 25}}>{columnSorting("alerted")}</th>
          <th>Name {columnSorting("name")}</th>
          {showType ? <th>Type {columnSorting("type")}</th> : null}
          <th>Taps {columnSorting("taps")}</th>
          <th>Interval {columnSorting("interval")}</th>
          <th>Created At {columnSorting("created_at")}</th>
          {onApplyMonitor ? <th style={{width: 250}}></th> : <th></th> }
        </tr>
        </thead>
        <tbody>
        {monitors.monitors.map(function(m, i) {
          return (
            <tr key={i}>
              <td><MonitorStatusIndicator monitor={m} /></td>
              <td>
                {onApplyMonitor ?
                  <span>{m.name}</span> : <Link to={ApiRoutes.ALERTS.MONITORS.DETAILS(m.uuid)}>{m.name}</Link>}
                {" "}{filterIcon("name", m.name)}
              </td>
              {showType ? <td><MonitorType type={m.type} /> {filterIcon("type", m.type)}</td> : null}
              <td>{m.taps === null ? "All" : m.taps.length}</td>
              <td>{numeral(m.interval).format("0,0")} {m.interval === 1 ? "Minute" : "Minutes"}</td>
              <td title={moment(m.created_at).format()}>{moment(m.created_at).fromNow()}</td>
              {onApplyMonitor ?
                <td><a href="#" onClick={(e) => {e.preventDefault(); onApplyMonitor(m); }}>Apply</a></td> :
                <td><Link to={monitorTypeToSearchLink(m)}>Replay</Link></td> }
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={monitors.total} perPage={perPage} setPage={setPage} page={page} />
    </>
  )

}
