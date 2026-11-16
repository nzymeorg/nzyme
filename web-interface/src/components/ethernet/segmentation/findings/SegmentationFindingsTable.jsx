import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import ApiRoutes from "../../../../util/ApiRoutes";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import L4Address from "../../shared/L4Address";
import FullCopyShortenedId from "../../../shared/FullCopyShortenedId";
import useRowSelection from "../../assets/identity/useRowSelection";
import {OUTCOMES, flowServiceLabel, targetCountNote, targetCountText} from "../SegmentationFormat";
import SegmentationBulkActions from "../SegmentationBulkActions";
import FindingClass from "./FindingClass";

const segmentationService = new SegmentationService();

const NOUNS = {one: "finding", many: "findings"};

export const DISMISS_NOTE = "Dismissed findings reappear if the source connects again.";

const bulkActions = (bulk) => {
  return [{
    key: "dismiss",
    label: "Dismiss",
    title: "Dismiss Findings",
    description: "Deletes findings. This cannot be undone. " + DISMISS_NOTE,
    confirm: (what) => "Dismiss " + what,
    running: "Dismissing ...",
    destructive: true,
    done: (what) => what + " dismissed.",
    onChecked: (uuids, onSuccess, onError) => {
      segmentationService.deleteFindings(uuids, bulk.organizationId, bulk.tenantId, onSuccess, onError);
    },
    onFiltered: (filters, onSuccess, onError) => {
      segmentationService.deleteFilteredFindings(bulk.organizationId, bulk.tenantId, filters, bulk.timeRange,
        bulk.policy, onSuccess, onError);
    }
  }];
}

export function FindingSource({finding, filterElement = undefined}) {
  if (finding.source_address) {
    return <L4Address address={finding.source_address} hidePort={true} withAssetName hideSegment filterElement={filterElement} />
  }

  const addresses = finding.source_addresses ? finding.source_addresses.length : 0;

  return (
    <span title={addresses > 0 ? finding.source_addresses.join(", ") : undefined}>
      {finding.source_label}
      {addresses > 0 ?
        <span className="text-muted"> ({numeral(addresses).format("0,0")} {addresses === 1 ? "address" : "addresses"})</span>
        : null}
      {filterElement ? <React.Fragment>{' '}{filterElement}</React.Fragment> : null}
    </span>
  )
}

export function FindingSegment({name, internal, outcome = null}) {
  if (outcome === "SENT") {
    return <span className="text-muted" title="A broadcast or multicast address">Broadcast</span>
  }

  if (name) {
    return name;
  }

  return <span className="text-muted">{internal ? "Unassigned" : "Internet"}</span>
}

export function FindingTargetAddress({target, filterElement = undefined}) {
  if (target.address) {
    return <L4Address address={target.address} hidePort={true} withAssetName hideSegment filterElement={filterElement} />
  }

  return (
    <React.Fragment>
      <span className="machine-data">{target.address_text}</span>
      {filterElement ? <React.Fragment>{' '}{filterElement}</React.Fragment> : null}
    </React.Fragment>
  )
}

export default function SegmentationFindingsTable(props) {

  if (!props.findings) {
    return <LoadingSpinner />
  }

  return <LoadedFindingsTable {...props} />

}

/*
 * A component of its own, so that it is mounted anew with every list that was loaded: what was checked does not
 * survive another page, other filters, another sorting or another time range.
 */
function LoadedFindingsTable({findings, page, setPage, perPage, orderColumn, setOrderColumn, orderDirection,
                              setOrderDirection, filterFields, setFilters, filters = undefined, policyFixed = false,
                              emptyMessage = undefined, bulk = undefined}) {

  const selection = useRowSelection(findings.findings);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value, operator = undefined) => {
    return <FilterValueIcon setFilters={setFilters}
                            filters={filters}
                            fields={filterFields}
                            field={field}
                            value={value}
                            operator={operator} />
  }

  const sourceFilterIcon = (f) => {
    if (f.source_address) {
      return filterIcon("source_address", f.source_address.address);
    }

    if (f.source_asn !== null && f.source_asn !== undefined) {
      return filterIcon("source_asn", f.source_asn);
    }

    return f.source_country_code ? filterIcon("source_country_code", f.source_country_code) : null;
  }

  if (findings.findings.length === 0) {
    return (
      <div className="alert alert-info mb-0">
        {emptyMessage ? emptyMessage : "No findings match the current filters and time range."}
      </div>
    )
  }

  const reached = (f) => {
    if (!f.targets || f.targets.length === 0) {
      return <span className="text-muted">n/a</span>
    }

    const first = f.targets[0];
    const outcome = OUTCOMES[first.outcome];

    return (
      <React.Fragment>
        <FindingTargetAddress target={first} filterElement={filterIcon("target_address", first.address_text)} />{' '}
        <span className="text-muted">on</span> {flowServiceLabel(first)}{' '}
        <span className="text-muted" title={outcome ? outcome.description : undefined}>
          ({outcome ? outcome.title : first.outcome})
        </span>
      </React.Fragment>
    )
  }

  return (
    <React.Fragment>
      {bulk ?
        <SegmentationBulkActions nouns={NOUNS}
                                 actions={bulkActions(bulk)}
                                 selection={selection}
                                 total={findings.total}
                                 filters={bulk.filters}
                                 partOf={bulk.policy ? "of this policy" : null}
                                 matchedBy="the filters and time range"
                                 allWhat="in the selected time range"
                                 countAll={bulk.countAll}
                                 onDone={bulk.onDone} /> : null}

      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          {bulk ?
            <th>
              <input className="form-check-input" type="checkbox" title="Check all rows of this page"
                     checked={selection.allSelected} onChange={selection.toggleAll} />
            </th> : null}
          <th>ID</th>
          <th className="text-nowrap">Last Seen {columnSorting("last_seen")}</th>
          <th className="text-nowrap">First Seen {columnSorting("first_seen")}</th>
          {policyFixed ? null : <th>Policy {columnSorting("policy_name")}</th>}
          <th>Class {columnSorting("finding_class")}</th>
          <th>Source {columnSorting("source")}</th>
          <th>Source Segment</th>
          <th title="The most recent target">Reached</th>
          <th className="text-nowrap">Targets {columnSorting("target_count")}</th>
          <th className="text-nowrap">Sessions {columnSorting("session_count")}</th>
        </tr>
        </thead>
        <tbody>
        {findings.findings.map((f) => {
          return (
            <tr key={f.uuid}>
              {bulk ?
                <td>
                  <input className="form-check-input" type="checkbox" checked={selection.selected.includes(f.uuid)}
                         onChange={() => selection.toggle(f.uuid)} />
                </td> : null}
              <td>
                <Link to={ApiRoutes.ETHERNET.SEGMENTATION.FINDINGS.DETAILS(f.uuid)}>
                  <FullCopyShortenedId value={f.uuid} />
                </Link>
              </td>
              <td title={moment(f.last_seen).format()}>{moment(f.last_seen).fromNow()}</td>
              <td title={moment(f.first_seen).format()}>{moment(f.first_seen).fromNow()}</td>
              {policyFixed ? null :
                <td className="text-nowrap">
                  <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(f.policy_uuid)}>{f.policy_name}</Link>{' '}
                  {filterIcon("policy_name", f.policy_name)}
                </td>}
              <td className="text-nowrap">
                <FindingClass findingClass={f.finding_class} audit={f.audit} />{' '}
                {filterIcon("finding_class", f.finding_class)}
              </td>
              <td><FindingSource finding={f} filterElement={sourceFilterIcon(f)} /></td>
              <td><FindingSegment name={f.source_segment_name} internal={f.source_internal} /></td>
              <td>{reached(f)}</td>
              <td title={targetCountNote(f)}>{targetCountText(f)}</td>
              <td>{numeral(f.session_count).format("0,0")}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={findings.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
