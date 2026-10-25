import React from "react";
import {Link} from "react-router-dom";
import moment from "moment";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import ApiRoutes from "../../../../util/ApiRoutes";
import BeaconExclusionCriteria from "./BeaconExclusionCriteria";

export default function BeaconExclusionsTable({exclusions, page, setPage, perPage, orderColumn, setOrderColumn,
                                                orderDirection, setOrderDirection, filterFields, setFilters}) {

  if (!exclusions) {
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

  if (exclusions.exclusions.length === 0) {
    return (
      <div className="alert alert-info mb-0">
        No exclusions match the current filters. Exclusions keep traffic you never want to see as a beacon out of
        detection.
      </div>
    )
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Name {columnSorting("name")}</th>
          <th>Criteria</th>
          <th>Status {columnSorting("enabled")}</th>
          <th>Last Matched {columnSorting("last_matched_at")}</th>
          <th>Created {columnSorting("created_at")}</th>
        </tr>
        </thead>
        <tbody>
        {exclusions.exclusions.map((exclusion) => {
          return (
            <tr key={exclusion.uuid}>
              <td>
                <Link to={ApiRoutes.ETHERNET.BEACONS.EXCLUSIONS.DETAILS(exclusion.uuid)}>{exclusion.name}</Link>{' '}
                {filterIcon("name", exclusion.name)}
              </td>
              <td><BeaconExclusionCriteria exclusion={exclusion} /></td>
              <td>
                {exclusion.enabled ? "Enabled" : <span className="text-warning">Disabled</span>}{' '}
                {filterIcon("enabled", exclusion.enabled ? "true" : "false")}
              </td>
              <td>
                {exclusion.last_matched_at ?
                  <span title={moment(exclusion.last_matched_at).format()}>{moment(exclusion.last_matched_at).fromNow()}</span>
                  : <span className="text-muted">Never</span>}
              </td>
              <td title={moment(exclusion.created_at).format()}>{moment(exclusion.created_at).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={exclusions.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
