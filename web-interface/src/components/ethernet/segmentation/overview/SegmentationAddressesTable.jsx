import React from "react";
import moment from "moment";
import GenericWidgetLoadingSpinner from "../../../widgets/GenericWidgetLoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import {FindingTargetAddress} from "../findings/SegmentationFindingsTable";
import PivotLink from "../../../shared/PivotLink";
import {filtersApplied} from "../../../../util/Tools";
import {formatCount} from "../../assets/details/AssetProfileHelpers";
import {addressSessionsLink} from "../SegmentationPivots";

export default function SegmentationAddressesTable({addresses, tapsOf, page, setPage, perPage, orderColumn,
                                                     setOrderColumn, orderDirection, setOrderDirection, filterFields,
                                                     filters, setFilters, timeRange}) {

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters} filters={filters} fields={filterFields} field={field} value={value} />
  }

  const sessions = (count, address, side, title) => (
      <React.Fragment>
        <span className={count === 0 ? "text-muted" : undefined}>{formatCount(count)}</span>
        {count > 0
            ? <PivotLink to={addressSessionsLink(address, side, timeRange)} title={title} icon={true} className="ms-2" />
            : null}
      </React.Fragment>
  )

  if (!addresses) {
    return <GenericWidgetLoadingSpinner height={120} />
  }

  const folded = addresses.folded_sessions > 0 ?
      <div className="alert alert-warning">
        The destinations of {formatCount(addresses.folded_sessions)} unanswered{' '}
        {addresses.folded_sessions === 1 ? "session" : "sessions"} are not listed. More than{' '}
        {addresses.destinations_folded_above} unanswered destinations of one source per evaluation are stored as one
        flow.
      </div> : null;

  if (addresses.addresses.length === 0) {
    return (
        <React.Fragment>
          {folded}
          <div className="alert alert-info mb-0">
            {filtersApplied(filters)
                ? "No addresses match the current filters."
                : "No addresses are stored for the selected time range."}
          </div>
        </React.Fragment>
    )
  }

  return (
      <React.Fragment>
        {folded}

        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Address {columnSorting("address")}</th>
            <th title="Sessions the address opened">Sessions From {columnSorting("sessions_from")}</th>
            <th title="Sessions that went to the address">Sessions To {columnSorting("sessions_to")}</th>
            <th title="Sessions to the address that it answered">Answered {columnSorting("answered")}</th>
            <th>Recorded By</th>
            <th>First Seen {columnSorting("first_seen")}</th>
            <th>Last Seen {columnSorting("last_seen")}</th>
          </tr>
          </thead>
          <tbody>
          {addresses.addresses.map((a) => (
              <tr key={a.address_text}>
                <td>
                  <FindingTargetAddress target={a} filterElement={filterIcon("address", a.address_text)} />
                </td>
                <td>{sessions(a.sessions_from, a.address_text, "source", "Sessions from this address")}</td>
                <td>{sessions(a.sessions_to, a.address_text, "destination", "Sessions to this address")}</td>
                <td className={a.answered === 0 ? "text-muted" : undefined}>{formatCount(a.answered)}</td>
                <td>{tapsOf(a.tap_uuids).map((tap) => tap.name).join(", ")}</td>
                <td title={moment(a.first_seen).format()}>{moment(a.first_seen).fromNow()}</td>
                <td title={moment(a.last_seen).format()}>{moment(a.last_seen).fromNow()}</td>
              </tr>
          ))}
          </tbody>
        </table>

        <Paginator itemCount={addresses.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}
