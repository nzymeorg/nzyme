import React from "react";
import {Link} from "react-router-dom";
import moment from "moment";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import ApiRoutes from "../../../../util/ApiRoutes";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import useRowSelection from "../../assets/identity/useRowSelection";
import {SEGMENT_KINDS, segmentTapNames} from "../SegmentationFormat";
import SegmentationBulkActions from "../SegmentationBulkActions";

const segmentationService = new SegmentationService();

const SHOWN_NETWORKS = 4;

const NOUNS = {one: "segment or external network", many: "segments and external networks"};

const memberLabel = (member) => member.type === "ASN" ? "AS" + member.asn : member.network;

const bulkActions = (bulk) => {
  return [{
    key: "delete",
    label: "Delete",
    title: "Delete Segments and External Networks",
    description: "Deletes segments and external networks. This cannot be undone. Those a policy refers to are " +
      "skipped.",
    confirm: (what) => "Delete " + what,
    running: "Deleting ...",
    destructive: true,
    done: (what) => what + " deleted.",
    onChecked: (uuids, onSuccess, onError) => {
      segmentationService.deleteSegments(uuids, bulk.organizationId, bulk.tenantId, onSuccess, onError);
    },
    onFiltered: (filters, onSuccess, onError) => {
      segmentationService.deleteFilteredSegments(bulk.organizationId, bulk.tenantId, filters, onSuccess, onError);
    }
  }];
}

export default function SegmentsTable(props) {

  if (!props.segments || !props.taps) {
    return <LoadingSpinner />
  }

  return <LoadedSegmentsTable {...props} />

}

/*
 * A component of its own, so that it is mounted anew with every list that was loaded: what was checked does not
 * survive another page, other filters or another sorting.
 */
function LoadedSegmentsTable({segments, taps, canEdit, onDelete, page, setPage, perPage, orderColumn, setOrderColumn,
                              orderDirection, setOrderDirection, filterFields, setFilters, bulk}) {

  const selection = useRowSelection(segments.segments);

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

  if (segments.segments.length === 0) {
    return <div className="alert alert-info mb-0">No segments or external networks found.</div>
  }

  const networks = (segment) => {
    const labels = segment.members.map(memberLabel);
    const hidden = labels.length - SHOWN_NETWORKS;

    return (
      <span title={hidden > 0 ? labels.join(", ") : undefined}>
        {labels.slice(0, SHOWN_NETWORKS).map((label, i) => {
          return (
            <React.Fragment key={label}>
              {i > 0 ? ", " : null}
              <span className="machine-data">{label}</span>
            </React.Fragment>
          )
        })}
        {hidden > 0 ? <span className="text-muted"> and {hidden} more</span> : null}
      </span>
    )
  }

  const tapNames = (segment) => {
    const names = segmentTapNames(segment, taps);
    return names ? names.join(", ") : <span className="text-muted">All taps</span>;
  }

  return (
    <React.Fragment>
      {bulk ?
        <SegmentationBulkActions nouns={NOUNS}
                                 actions={bulkActions(bulk)}
                                 selection={selection}
                                 total={segments.total}
                                 filters={bulk.filters}
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
          <th>Name {columnSorting("name")}</th>
          <th>Type {columnSorting("kind")}</th>
          <th>Description {columnSorting("description")}</th>
          <th>Networks</th>
          <th title="The taps the definition is limited to">Taps</th>
          <th title="The policies that refer to it">Used by Policies</th>
          <th>Created {columnSorting("created_at")}</th>
          <th>Updated {columnSorting("updated_at")}</th>
          {canEdit ? <th>&nbsp;</th> : null}
        </tr>
        </thead>
        <tbody>
        {segments.segments.map((segment) => {
          const kind = SEGMENT_KINDS[segment.kind];

          return (
            <tr key={segment.uuid}>
              {bulk ?
                <td>
                  <input className="form-check-input" type="checkbox" checked={selection.selected.includes(segment.uuid)}
                         onChange={() => selection.toggle(segment.uuid)} />
                </td> : null}
              <td>
                <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.DETAILS(segment.uuid)}>{segment.name}</Link>{' '}
                {filterIcon("name", segment.name)}
              </td>
              <td title={kind ? kind.description : undefined}>
                {kind ? kind.title : segment.kind} {filterIcon("kind", segment.kind)}
              </td>
              <td>{segment.description ? segment.description : <span className="text-muted">n/a</span>}</td>
              <td>{networks(segment)}</td>
              <td>{tapNames(segment)}</td>
              <td>
                {segment.policies && segment.policies.length > 0 ?
                  segment.policies.map((policy, i) => {
                    return (
                      <React.Fragment key={policy.uuid}>
                        {i > 0 ? ", " : null}
                        <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(policy.uuid)}>{policy.name}</Link>
                      </React.Fragment>
                    )
                  })
                  : <span className="text-muted">None</span>}
              </td>
              <td title={moment(segment.created_at).format()}>{moment(segment.created_at).fromNow()}</td>
              <td title={moment(segment.updated_at).format()}>{moment(segment.updated_at).fromNow()}</td>
              {canEdit ?
                <td className="text-end text-nowrap">
                  <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.EDIT(segment.uuid)}>Edit</Link>{' '}
                  <a href="#" className="ms-2" onClick={(e) => onDelete(e, segment)}>Delete</a>
                </td> : null}
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={segments.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
