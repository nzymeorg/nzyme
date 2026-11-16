import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import Filters from "../../../shared/filtering/Filters";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import PivotLink from "../../../shared/PivotLink";
import {Relative} from "../../../shared/timerange/TimeRange";
import ApiRoutes from "../../../../util/ApiRoutes";
import {networkSessionsLink} from "../SegmentationPivots";
import useSegmentationClientTable from "../useSegmentationClientTable";
import {
  SEGMENT_SUGGESTIONS_FILTER_FIELDS,
  SEGMENT_SUGGESTIONS_SORT_COLUMNS,
  SUGGESTION_SOURCES
} from "./SegmentSuggestionsFilterFields";

const createLink = (suggestion) => {
  const params = new URLSearchParams();
  params.set("network", suggestion.network);

  return ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.CREATE + "?" + params.toString();
}

export default function SegmentSuggestionsTable({suggestions, canEdit, limitPerSource = null, lookbackDays = null}) {

  const table = useSegmentationClientTable(
    suggestions ? suggestions : [], SEGMENT_SUGGESTIONS_FILTER_FIELDS, SEGMENT_SUGGESTIONS_SORT_COLUMNS, null
  );

  if (!suggestions) {
    return <LoadingSpinner />
  }

  if (suggestions.length === 0) {
    return <div className="alert alert-info mb-0">No suggestions.</div>
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={table.orderColumn}
                          setOrderColumn={table.setOrderColumn}
                          orderDirection={table.orderDirection}
                          setOrderDirection={table.setOrderDirection} />
  }

  const sessionsRange = lookbackDays ? Relative(lookbackDays * 24 * 60, "Last " + lookbackDays + " Days") : null;

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={table.setFilters}
                            filters={table.filters}
                            fields={SEGMENT_SUGGESTIONS_FILTER_FIELDS}
                            field={field}
                            value={value} />
  }

  return (
    <React.Fragment>
      {limitPerSource !== null ?
        <div className="alert alert-warning">Limited to {limitPerSource} suggestions per source.</div> : null}

      <div className="mb-4">
        <Filters filters={table.filters}
                 setFilters={table.setFilters}
                 fields={SEGMENT_SUGGESTIONS_FILTER_FIELDS}
                 embedded={true}
                 onMonitorsReady={() => {}} />
      </div>

      {table.total === 0 ?
        <div className="alert alert-info mb-0">No suggestions match the current filters.</div>
        : <table className={"table table-sm table-hover table-striped" + (table.total > table.perPage ? "" : " mb-0")}>
          <thead>
          <tr>
            <th>Source {columnSorting("source")}</th>
            <th>Network {columnSorting("network")}</th>
            <th title="Number of addresses of the network that were seen">Hosts Seen {columnSorting("hosts")}</th>
            <th>&nbsp;</th>
          </tr>
          </thead>
          <tbody>
          {table.rows.map((s) => {
            const source = SUGGESTION_SOURCES[s.source];

            return (
              <tr key={s.source + "-" + s.network}>
                <td title={source ? source.description : undefined}>
                  {source ? source.title : s.source}{' '}
                  {s.dhcp_server ? <span className="machine-data">{s.dhcp_server}</span> : null}{' '}
                  {filterIcon("source", source ? source.title : s.source)}
                </td>
                <td><span className="machine-data">{s.network}</span></td>
                <td>
                  {s.hosts === null || s.hosts === undefined ?
                    <span className="text-muted">n/a</span> : numeral(s.hosts).format("0,0")}
                </td>
                <td className="text-end text-nowrap">
                  {canEdit ? <Link to={createLink(s)} className="me-3">Create Segment</Link> : null}
                  {sessionsRange ?
                    <PivotLink to={networkSessionsLink(s.network, sessionsRange)} title="Sessions of this network"
                               icon={true} /> : null}
                </td>
              </tr>
            )
          })}
          </tbody>
        </table>}

      {table.total > table.perPage ?
        <Paginator itemCount={table.total} perPage={table.perPage} setPage={table.setPage} page={table.page} />
        : null}
    </React.Fragment>
  )

}
