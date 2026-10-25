import React from "react";
import {Link} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import ApiRoutes from "../../../../util/ApiRoutes";
import IcmpExclusionCriteria from "./IcmpExclusionCriteria";
import {ICMP_EXCLUSIONS_FILTER_FIELDS} from "./IcmpExclusionsFilterFields";

export default function IcmpExclusionsTable({exclusions, page, setPage, perPage, orderColumn, setOrderColumn,
                                             orderDirection, setOrderDirection, setFilters}) {

  if (!exclusions) {
    return <LoadingSpinner />
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName} orderColumn={orderColumn} setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection} setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters} fields={ICMP_EXCLUSIONS_FILTER_FIELDS} field={field} value={value} />
  }

  if (exclusions.exclusions.length === 0) {
    return (
      <div className="alert alert-info mb-0">
        No exclusions match the current filters. Exclusions keep known good sources, like a legitimate second
        router or a monitoring host, out of redirect, tunnel and traceroute detection.
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
          <th>Suppressed {columnSorting("match_count")}</th>
          <th>Last Matched {columnSorting("last_matched_at")}</th>
        </tr>
        </thead>
        <tbody>
        {exclusions.exclusions.map((ex) => {
          return (
            <tr key={ex.uuid}>
              <td>
                <Link to={ApiRoutes.ETHERNET.ICMP.EXCLUSIONS.DETAILS(ex.uuid)}>{ex.name}</Link>{' '}
                {filterIcon("name", ex.name)}
              </td>
              <td><IcmpExclusionCriteria exclusion={ex} /></td>
              <td>
                {ex.enabled ? "Enabled" : <span className="text-warning">Disabled</span>}{' '}
                {filterIcon("enabled", ex.enabled ? "true" : "false")}
              </td>
              <td>{numeral(ex.match_count).format("0,0")}</td>
              <td>
                {ex.last_matched_at ?
                  <span title={moment(ex.last_matched_at).format()}>{moment(ex.last_matched_at).fromNow()}</span>
                  : <span className="text-muted">Never</span>}
              </td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={exclusions.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
