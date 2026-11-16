import React from "react";
import numeral from "numeral";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import Filters from "../../../shared/filtering/Filters";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import {portServiceLabel} from "../../segmentation/SegmentationFormat";
import useSegmentationClientTable from "../../segmentation/useSegmentationClientTable";
import AssetIpAddresses from "../AssetIpAddresses";
import {
  ASSET_TAG_SUGGESTIONS_FILTER_FIELDS,
  ASSET_TAG_SUGGESTIONS_SORT_COLUMNS
} from "./AssetTagSuggestionsFilterFields";
import TaggedAsset from "./TaggedAsset";

export default function AssetTagSuggestionsTable({suggestions, canEdit, applying, onApply}) {

  const table = useSegmentationClientTable(
    suggestions ? suggestions.suggestions : [], ASSET_TAG_SUGGESTIONS_FILTER_FIELDS,
    ASSET_TAG_SUGGESTIONS_SORT_COLUMNS, null
  );

  if (!suggestions) {
    return <LoadingSpinner />
  }

  const limited = suggestions.truncated ?
    <p className="text-muted">
      Limited to the {numeral(suggestions.limit).format("0,0")} served ports with the most sessions.
    </p> : null;

  if (suggestions.suggestions.length === 0) {
    return (
      <React.Fragment>
        {limited}

        <div className="alert alert-info mb-0">No suggestions.</div>
      </React.Fragment>
    )
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={table.orderColumn}
                          setOrderColumn={table.setOrderColumn}
                          orderDirection={table.orderDirection}
                          setOrderDirection={table.setOrderDirection} />
  }

  return (
    <React.Fragment>
      <div className="mb-4">
        <Filters filters={table.filters}
                 setFilters={table.setFilters}
                 fields={ASSET_TAG_SUGGESTIONS_FILTER_FIELDS}
                 embedded={true}
                 onMonitorsReady={() => {}} />
      </div>

      {limited}

      {table.total === 0 ?
        <div className="alert alert-info mb-0">No suggestions match the current filters.</div>
        : <table className={"table table-sm table-hover table-striped" + (table.total > table.perPage ? "" : " mb-0")}>
          <thead>
          <tr>
            <th>Suggested Tag {columnSorting("tag")}</th>
            <th>Asset {columnSorting("mac")}</th>
            <th>IP Address</th>
            <th title="The ports the asset serves that led to the suggestion">Serves</th>
            <th title="Sessions to these ports">Sessions {columnSorting("sessions")}</th>
            {canEdit ? <th>&nbsp;</th> : null}
          </tr>
          </thead>
          <tbody>
          {table.rows.map((s) => {
            return (
              <tr key={s.tag + "-" + s.asset.uuid}>
                <td>
                  <i className="fa-solid fa-tag text-muted"></i> {s.tag}{' '}
                  {s.tag_uuid ? null :
                    <span className="text-muted"
                          title="The tag does not exist yet. Applying the suggestion creates it.">(new)</span>}{' '}
                  <FilterValueIcon setFilters={table.setFilters}
                                   filters={table.filters}
                                   fields={ASSET_TAG_SUGGESTIONS_FILTER_FIELDS}
                                   field="tag"
                                   value={s.tag} />
                </td>
                <td><TaggedAsset asset={s.asset} /></td>
                <td><AssetIpAddresses addresses={s.asset.addresses} /></td>
                <td><span className="machine-data">{s.ports.map(portServiceLabel).join(", ")}</span></td>
                <td>{numeral(s.sessions).format("0,0")}</td>
                {canEdit ?
                  <td className="text-end">
                    {applying ? <span className="text-muted">Apply</span>
                      : <a href="#" onClick={(e) => onApply(e, s)}>Apply</a>}
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
