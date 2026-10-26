import React from "react";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import ColumnSorting from "../../shared/ColumnSorting";
import BeaconInterval from "./BeaconInterval";
import L4Address from "../shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";

export default function BeaconSourcesTable({sources, page, setPage, perPage, orderColumn, setOrderColumn,
                                            orderDirection, setOrderDirection, filterFields, setFilters}) {

  if (!sources) {
    return <LoadingSpinner />
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  // Filter icons apply to the check-in history of the beacon, not to this table.
  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters} fields={filterFields} field={field} value={value} />
  }

  if (sources.sources.length === 0) {
    return <div className="alert alert-info mb-0">No devices called this beacon in the selected time range.</div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Address {columnSorting("source_address")}</th>
          <th>MAC {columnSorting("source_mac")}</th>
          <th>Interval {columnSorting("interval_seconds")}</th>
          <th>Regularity {columnSorting("regularity")}</th>
          <th>Check-Ins {columnSorting("checkin_count")}</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {sources.sources.map((s, i) => {
          return (
            <tr key={i}>
              <td>
                {s.address ? <L4Address address={s.address} hidePort={true}
                                        filterElement={filterIcon("source_address", s.source_address)} />
                  : <React.Fragment>
                    <span className="machine-data">{s.source_address}</span>{' '}
                    {filterIcon("source_address", s.source_address)}
                  </React.Fragment>}
              </td>
              <td>
                {s.address && s.address.mac ?
                  <EthernetMacAddress addressWithContext={s.address.mac} withAssetLink withAssetName
                                      filterElement={filterIcon("source_mac", s.source_mac)} />
                  : (s.source_mac ?
                    <React.Fragment>
                      <span className="machine-data" title={s.source_mac_oui ? s.source_mac_oui : "Unknown vendor"}>{s.source_mac}</span>{' '}
                      {filterIcon("source_mac", s.source_mac)}
                    </React.Fragment>
                    : <span className="text-muted">n/a</span>)}
              </td>
              <td><BeaconInterval seconds={s.interval_seconds} jitter={s.interval_jitter} /></td>
              <td>{numeral(s.regularity * 100).format("0")}%</td>
              <td>{numeral(s.checkin_count).format("0,0")}</td>
              <td title={moment(s.first_seen).format()}>{moment(s.first_seen).fromNow()}</td>
              <td title={moment(s.last_seen).format()}>{moment(s.last_seen).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={sources.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
