import {Link} from "react-router-dom";
import React from "react";
import moment from "moment";
import numeral from "numeral";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import Paginator from "../../../../misc/Paginator";
import FormattedTiming from "../../../../misc/metrics/FormattedTiming";
import MonitorStatusIndicator from "../../../../monitors/shared/MonitorStatusIndicator";
import MonitorType from "../../../../monitors/shared/MonitorType";
import ApiRoutes from "../../../../../util/ApiRoutes";
import ColumnSorting from "../../../../shared/ColumnSorting";

export default function TenantMonitorPerformanceTable({monitors, page, setPage, perPage, orderColumn, setOrderColumn,
                                                       orderDirection, setOrderDirection}) {

  if (!monitors) {
    return <LoadingSpinner />
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  if (monitors.monitors.length === 0) {
    return <div className="alert alert-info mb-0">This tenant has no monitors.</div>
  }

  const timing = (monitor, field) => {
    if (!monitor.execution_timer) {
      return <span className="text-muted" title="No node has executed this monitor since it started.">n/a</span>
    }

    return <FormattedTiming timing={monitor.execution_timer[field]} format="0,0" />
  }

  const executions = (monitor) => {
    if (!monitor.execution_timer) {
      return <span className="text-muted">n/a</span>
    }

    return numeral(monitor.execution_timer.count).format("0,0");
  }

  const time = (t) => {
    if (!t) {
      return <span className="text-muted">Never</span>
    }

    return <span title={moment(t).format()}>{moment(t).fromNow()}</span>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th style={{width: 25}}>{columnSorting("alerted")}</th>
          <th>Name {columnSorting("name")}</th>
          <th>Type {columnSorting("type")}</th>
          <th>Interval {columnSorting("interval")}</th>
          <th>Last Run {columnSorting("last_run")}</th>
          <th>Max {columnSorting("timer_max")}</th>
          <th>Min {columnSorting("timer_min")}</th>
          <th>Mean {columnSorting("timer_mean")}</th>
          <th>P99 {columnSorting("timer_p99")}</th>
          <th>StdDev {columnSorting("timer_stddev")}</th>
          <th>Executions {columnSorting("executions")}</th>
        </tr>
        </thead>
        <tbody>
        {monitors.monitors.map((m, i) => {
          return (
            <tr key={i}>
              <td><MonitorStatusIndicator monitor={m} /></td>
              <td><Link to={ApiRoutes.ALERTS.MONITORS.DETAILS(m.uuid)}>{m.name}</Link></td>
              <td><MonitorType type={m.type} /></td>
              <td>{numeral(m.interval).format("0,0")} {m.interval === 1 ? "Minute" : "Minutes"}</td>
              <td>{time(m.last_run)}</td>
              <td>{timing(m, "max")}</td>
              <td>{timing(m, "min")}</td>
              <td>{timing(m, "mean")}</td>
              <td>{timing(m, "percentile_99")}</td>
              <td>{timing(m, "stddev")}</td>
              <td>{executions(m)}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={monitors.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
