import React from "react";
import {Link} from "react-router-dom";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import numeral from "numeral";
import moment from "moment";
import ApiRoutes from "../../../util/ApiRoutes";
import ColumnSorting from "../../shared/ColumnSorting";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import BeaconClassification from "./BeaconClassification";
import BeaconType from "./BeaconType";
import BeaconInterval from "./BeaconInterval";
import L4Address from "../shared/L4Address";

export default function BeaconsTable({beacons, page, setPage, perPage, orderColumn, setOrderColumn,
                                      orderDirection, setOrderDirection, filterFields, setFilters,
                                      organizationId, tenantId, canEdit = false, onBeaconChanged = undefined}) {

  if (!beacons) {
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

  if (beacons.beacons.length === 0) {
    return <div className="alert alert-info mb-0">
      No beacons match the current filters and time range. Beacons appear here once devices have called out to
      the same destination in regular intervals for a while.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Classification {columnSorting("classification")}</th>
          <th>Name {columnSorting("name")}</th>
          <th>Type {columnSorting("type")}</th>
          <th>Destination {columnSorting("destination")}</th>
          <th>Devices {columnSorting("source_count")}</th>
          <th>Interval {columnSorting("interval_seconds")}</th>
          <th>Check-Ins {columnSorting("checkin_count")}</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
        </tr>
        </thead>
        <tbody>
        {beacons.beacons.map((b, i) => {
          return (
            <tr key={i}>
              <td>
                <BeaconClassification classification={b.classification}
                                      beaconUuid={b.uuid}
                                      organizationId={organizationId}
                                      tenantId={tenantId}
                                      enableEditMode={canEdit}
                                      compact={true}
                                      onChange={onBeaconChanged} />{' '}
                {filterIcon("classification", b.classification)}
              </td>
              <td>
                <Link to={ApiRoutes.ETHERNET.BEACONS.DETAILS(b.uuid)}>{b.name}</Link>{' '}
                {filterIcon("name", b.name)}
                {b.matching_exclusions && b.matching_exclusions.length > 0 ?
                  <span className="text-muted ms-1"
                        title={"Matches exclusion " + b.matching_exclusions.map((ex) => "\"" + ex.name + "\"").join(", ")
                          + ". No longer receives check-ins."}>
                    (excluded)
                  </span> : null}
              </td>
              <td><BeaconType type={b.type} /> {filterIcon("type", b.type)}</td>
              <td>
                {!b.destination_host && b.destination_address_data ?
                  <L4Address address={b.destination_address_data}
                             filterElement={filterIcon("destination_address", b.destination_address)} />
                  : <React.Fragment>
                    <span className="machine-data">{b.destination}</span>{' '}
                    {b.destination_host ? filterIcon("destination_host", b.destination_host) : null}
                  </React.Fragment>}
              </td>
              <td>{numeral(b.source_count).format("0,0")} {filterIcon("source_count", b.source_count)}</td>
              <td><BeaconInterval seconds={b.interval_seconds} jitter={b.interval_jitter} /></td>
              <td>{numeral(b.checkin_count).format("0,0")}</td>
              <td title={moment(b.first_seen).format()}>{moment(b.first_seen).fromNow()}</td>
              <td title={moment(b.last_seen).format()}>{moment(b.last_seen).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={beacons.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
