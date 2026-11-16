import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import Filters from "../../../shared/filtering/Filters";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import ApiRoutes from "../../../../util/ApiRoutes";
import {SEGMENT_KINDS} from "../SegmentationFormat";
import useSegmentationClientTable from "../useSegmentationClientTable";
import {
  SEGMENT_SUGGESTIONS_FILTER_FIELDS,
  SEGMENT_SUGGESTIONS_SORT_COLUMNS,
  SUGGESTION_SOURCES
} from "./SegmentSuggestionsFilterFields";

const createLink = (suggestion) => {
  const params = new URLSearchParams();
  params.set("kind", suggestion.kind);
  params.set("network", suggestion.network);

  if (suggestion.name) {
    params.set("name", suggestion.name);
  }

  if (suggestion.description) {
    params.set("description", suggestion.description);
  }

  return ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.CREATE + "?" + params.toString();
}

export default function SegmentSuggestionsTable({suggestions, canEdit, limitPerSource = null}) {

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
            <th>Type {columnSorting("kind")}</th>
            <th>Suggested Name {columnSorting("name")}</th>
            <th>Suggested Description {columnSorting("description")}</th>
            <th title="Number of addresses of the network that were seen">Hosts Seen {columnSorting("hosts")}</th>
            {canEdit ? <th>&nbsp;</th> : null}
          </tr>
          </thead>
          <tbody>
          {table.rows.map((s) => {
            const source = SUGGESTION_SOURCES[s.source];
            const kind = SEGMENT_KINDS[s.kind];
            const external = s.kind === "EXTERNAL";

            return (
              <tr key={s.source + "-" + s.network}>
                <td title={source ? source.description : undefined}>
                  {source ? source.title : s.source}{' '}
                  {s.dhcp_server ? <span className="machine-data">{s.dhcp_server}</span> : null}{' '}
                  {filterIcon("source", source ? source.title : s.source)}
                </td>
                <td><span className="machine-data">{s.network}</span></td>
                <td>{kind ? kind.title : s.kind} {filterIcon("kind", s.kind)}</td>
                <td>{s.name ? s.name : <span className="text-muted">None</span>}</td>
                <td>{s.description ? s.description : <span className="text-muted">None</span>}</td>
                <td>
                  {s.hosts === null || s.hosts === undefined ?
                    <span className="text-muted">n/a</span> : numeral(s.hosts).format("0,0")}
                </td>
                {canEdit ?
                  <td className="text-end text-nowrap">
                    <Link to={createLink(s)}>Create {external ? "External Network" : "Segment"}</Link>
                  </td> : null}
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
