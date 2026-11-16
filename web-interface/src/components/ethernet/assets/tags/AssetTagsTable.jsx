import React, {useState} from "react";
import {Link} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import ApiRoutes from "../../../../util/ApiRoutes";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import AssetTagsService from "../../../../services/ethernet/AssetTagsService";
import SegmentationBulkActions from "../../segmentation/SegmentationBulkActions";
import useRowSelection from "../identity/useRowSelection";
import AssetTagAssets from "./AssetTagAssets";

const assetTagsService = new AssetTagsService();

const NOUNS = {one: "tag", many: "tags"};

const bulkActions = (bulk) => {
  return [{
    key: "delete",
    label: "Delete",
    title: "Delete Tags",
    description: "Deletes tags and takes them off their assets. This cannot be undone. Tags that a segmentation " +
      "policy refers to are skipped and not deleted.",
    confirm: (what) => "Delete " + what,
    running: "Deleting ...",
    destructive: true,
    done: (what) => what + " deleted.",
    onChecked: (uuids, onSuccess, onError) => {
      assetTagsService.deleteTags(uuids, bulk.organizationId, bulk.tenantId, onSuccess, onError);
    },
    onFiltered: (filters, onSuccess, onError) => {
      assetTagsService.deleteFilteredTags(bulk.organizationId, bulk.tenantId, filters, onSuccess, onError);
    }
  }];
}

export default function AssetTagsTable({tags, organizationId, tenantId, canEdit, onDelete, onChange, page, setPage,
                                         perPage, orderColumn, setOrderColumn, orderDirection, setOrderDirection,
                                         filterFields, setFilters, bulk}) {

  const [expanded, setExpanded] = useState(null);

  const selection = useRowSelection(tags ? tags.tags : []);

  if (!tags) {
    return <LoadingSpinner />
  }

  if (tags.tags.length === 0) {
    return <div className="alert alert-info mb-0">No tags found.</div>
  }

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const toggle = (e, uuid) => {
    e.preventDefault();
    setExpanded(expanded === uuid ? null : uuid);
  }

  const columns = 6 + (canEdit ? 1 : 0) + (bulk ? 1 : 0);

  return (
    <React.Fragment>
      {bulk ?
        <SegmentationBulkActions nouns={NOUNS}
                                 actions={bulkActions(bulk)}
                                 selection={selection}
                                 total={tags.total}
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
          <th>Description {columnSorting("description")}</th>
          <th>Assets {columnSorting("asset_count")}</th>
          <th title="The segmentation policies that refer to the tag">Used by Policies</th>
          <th>Created {columnSorting("created_at")}</th>
          <th>Updated {columnSorting("updated_at")}</th>
          {canEdit ? <th>&nbsp;</th> : null}
        </tr>
        </thead>
        <tbody>
        {tags.tags.map((tag) => {
          return (
            <React.Fragment key={tag.uuid}>
              <tr>
                {bulk ?
                  <td>
                    <input className="form-check-input" type="checkbox" checked={selection.selected.includes(tag.uuid)}
                           onChange={() => selection.toggle(tag.uuid)} />
                  </td> : null}
                <td>
                  <i className="fa-solid fa-tag text-muted"></i> {tag.name}{' '}
                  <FilterValueIcon setFilters={setFilters} fields={filterFields} field="name" value={tag.name} />
                </td>
                <td>{tag.description ? tag.description : <span className="text-muted">n/a</span>}</td>
                <td>
                  <a href="#" onClick={(e) => toggle(e, tag.uuid)}
                     title={expanded === tag.uuid ? "Hide the assets" : "Show the assets that carry this tag"}>
                    {numeral(tag.asset_count).format("0,0")}{' '}
                    <i className={"fa-solid " + (expanded === tag.uuid ? "fa-chevron-up" : "fa-chevron-down")}></i>
                  </a>
                </td>
                <td>
                  {tag.policies && tag.policies.length > 0 ?
                    tag.policies.join(", ") : <span className="text-muted">None</span>}
                </td>
                <td title={moment(tag.created_at).format()}>{moment(tag.created_at).fromNow()}</td>
                <td title={moment(tag.updated_at).format()}>{moment(tag.updated_at).fromNow()}</td>
                {canEdit ?
                  <td className="text-end text-nowrap">
                    <Link to={ApiRoutes.ETHERNET.ASSETS.TAGS.EDIT(tag.uuid)}>Edit</Link>{' '}
                    <a href="#" className="ms-2" onClick={(e) => onDelete(e, tag)}>Delete</a>
                  </td> : null}
              </tr>
              {expanded === tag.uuid ?
                <tr>
                  <td colSpan={columns}>
                    <AssetTagAssets tag={tag}
                                    organizationId={organizationId}
                                    tenantId={tenantId}
                                    canEdit={canEdit}
                                    onChange={onChange} />
                  </td>
                </tr> : null}
            </React.Fragment>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={tags.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
