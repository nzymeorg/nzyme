import React from "react";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import L4Address from "../shared/L4Address";
import ReconScanOutcome from "./ReconScanOutcome";

export default function ReconScanTargetsTable({scan, targets, page, setPage, perPage, orderColumn, setOrderColumn,
                                               orderDirection, setOrderDirection, filterFields, setFilters}) {

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

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters} fields={filterFields} field={field} value={value} />
  }

  if (targets.targets.length === 0) {
    return <div className="alert alert-info mb-0">No targets match the current filters.</div>
  }

  const showPort = scan.type !== "ARP_SWEEP" && scan.type !== "ICMP_SWEEP" && scan.type !== "DNS_REVERSE_SWEEP"
    && scan.type !== "OS_FINGERPRINTING";

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Target {columnSorting("target_address")}</th>
          {showPort ? <th>Port {columnSorting("target_port")}</th> : null}
          <th>Outcome {columnSorting("outcome")}</th>
          <th>Probes {columnSorting("probe_count")}</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {targets.targets.map((t, i) => {
          return (
            <tr key={i}>
              <td>
                {t.target_address_data ?
                  <L4Address address={t.target_address_data} hidePort={true} withAssetName
                             filterElement={filterIcon("target_address", t.target_address)} />
                  : <React.Fragment>
                    <span className="machine-data">{t.target_address}</span>{' '}
                    {filterIcon("target_address", t.target_address)}
                  </React.Fragment>}
              </td>
              {showPort ?
                <td>
                  {t.target_port !== null && t.target_port !== undefined ?
                    <React.Fragment>
                      <span className="machine-data">{t.target_port}</span>{' '}
                      {filterIcon("target_port", t.target_port)}
                    </React.Fragment>
                    : <span className="text-muted">n/a</span>}
                </td> : null}
              <td><ReconScanOutcome outcome={t.outcome} /> {filterIcon("outcome", t.outcome)}</td>
              <td>{numeral(t.probe_count).format("0,0")}</td>
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
