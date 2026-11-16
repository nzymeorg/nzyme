import React from "react";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import PivotLink from "../../../shared/PivotLink";
import {filtersApplied} from "../../../../util/Tools";
import {FOLDED_PORT, OUTCOMES, flowServiceLabel} from "../SegmentationFormat";
import {targetSessionsLink} from "../SegmentationPivots";
import {FindingSegment, FindingTargetAddress} from "./SegmentationFindingsTable";

export default function SegmentationFindingTargetsTable({finding, targets, page, setPage, perPage, orderColumn,
                                                          setOrderColumn, orderDirection, setOrderDirection,
                                                          filterFields, filters, setFilters, onAllow = undefined}) {

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
    return <FilterValueIcon setFilters={setFilters} filters={filters} fields={filterFields} field={field} value={value} />
  }

  if (targets.targets.length === 0) {
    return (
      <div className="alert alert-info mb-0">
        {filtersApplied(filters) ? "No targets match the current filters." : "No targets recorded."}
      </div>
    )
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Address {columnSorting("address")}</th>
          <th>Segment</th>
          <th>Service {columnSorting("port")}</th>
          <th>Outcome {columnSorting("outcome")}</th>
          <th>Sessions {columnSorting("sessions")}</th>
          <th>First Seen {columnSorting("first_seen")}</th>
          <th>Last Seen {columnSorting("last_seen")}</th>
          <th>&nbsp;</th>
          {onAllow ? <th>&nbsp;</th> : null}
        </tr>
        </thead>
        <tbody>
        {targets.targets.map((t, i) => {
          const outcome = OUTCOMES[t.outcome];
          const sessionsLink = targetSessionsLink(finding.source_address ? finding.source_address.address : null, t);
          // A target that stands for many ports names none to filter by.
          const port = t.port === null || t.port === undefined || t.port === FOLDED_PORT ? null : t.port;

          return (
            <tr key={i}>
              <td><FindingTargetAddress target={t} filterElement={filterIcon("address", t.address_text)} /></td>
              <td><FindingSegment name={t.segment_name} internal={t.internal} outcome={t.outcome} /></td>
              <td>{flowServiceLabel(t)} {filterIcon("port", port)}</td>
              <td title={outcome ? outcome.description : undefined}>
                {outcome ? outcome.title : t.outcome} {filterIcon("outcome", t.outcome)}
              </td>
              <td>{numeral(t.sessions).format("0,0")}</td>
              <td title={moment(t.first_seen).format()}>{moment(t.first_seen).fromNow()}</td>
              <td title={moment(t.last_seen).format()}>{moment(t.last_seen).fromNow()}</td>
              <td className="text-end">
                {sessionsLink
                    ? <PivotLink to={sessionsLink} title="Sessions between the source and this target" icon={true} />
                    : null}
              </td>
              {onAllow ?
                <td className="text-end">
                  <a href="#" onClick={(e) => { e.preventDefault(); onAllow(t); }}>Allow</a>
                </td> : null}
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={targets.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
