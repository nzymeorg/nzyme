import React, {useEffect, useState} from "react";
import {toast} from "react-toastify";
import AssetTagsService from "../../../../services/ethernet/AssetTagsService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import SegmentationBulkActions, {isBehindLastPage} from "../../segmentation/SegmentationBulkActions";
import AssetIpAddresses from "../AssetIpAddresses";
import useRowSelection from "../identity/useRowSelection";
import {removeTagQuestion} from "./AssetTagHelpers";
import TaggedAsset from "./TaggedAsset";

const assetTagsService = new AssetTagsService();

const PER_PAGE = 25;

const NOUNS = {one: "asset", many: "assets"};

const bulkActions = (tag, organizationId, tenantId) => {
  return [{
    key: "remove",
    label: "Remove Tag",
    title: "Remove Tag from Assets",
    description: "Takes the tag \"" + tag.name + "\" off assets. The tag is not deleted.",
    confirm: (what) => "Remove tag from " + what,
    running: "Removing ...",
    destructive: true,
    done: (what) => "Tag removed from " + what + ".",
    onChecked: (uuids, onSuccess, onError) => {
      assetTagsService.unassignTagFromAssets(tag.uuid, uuids, organizationId, tenantId, onSuccess, onError);
    },
    // The list has no filters, so this is only ever asked for every asset of the tag.
    onFiltered: (filters, onSuccess, onError) => {
      assetTagsService.unassignTagFromAllAssets(tag.uuid, organizationId, tenantId, onSuccess, onError);
    }
  }];
}

export default function AssetTagAssets({tag, organizationId, tenantId, canEdit, onChange}) {

  const [assets, setAssets] = useState(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(new Date());

  const selection = useRowSelection(assets ? assets.assets : []);

  useEffect(() => {
    assetTagsService.findAssetsOfTag(tag.uuid, organizationId, tenantId, PER_PAGE, (page-1)*PER_PAGE, (response) => {
      if (isBehindLastPage(response.assets, response.total, page)) {
        setPage(Math.ceil(response.total / PER_PAGE));
      } else {
        setAssets(response);
      }
    });
  }, [tag.uuid, organizationId, tenantId, page, revision]);

  const reload = () => {
    setRevision(new Date());
    onChange();
  }

  const onRemove = (e, asset) => {
    e.preventDefault();

    if (!confirm(removeTagQuestion(tag))) {
      return;
    }

    assetTagsService.unassignTag(tag.uuid, asset.uuid, organizationId, tenantId, () => {
      toast.success("Tag removed from asset.");
      reload();
    });
  }

  if (!assets) {
    return <LoadingSpinner />
  }

  if (assets.assets.length === 0) {
    return <span className="text-muted">No assets.</span>
  }

  return (
    <React.Fragment>
      {canEdit ?
        <SegmentationBulkActions nouns={NOUNS}
                                 actions={bulkActions(tag, organizationId, tenantId)}
                                 selection={selection}
                                 total={assets.total}
                                 partOf="with this tag"
                                 onDone={reload} /> : null}

      <table className="table table-sm mb-0">
        <thead>
        <tr>
          {canEdit ?
            <th>
              <input className="form-check-input" type="checkbox" title="Check all rows of this page"
                     checked={selection.allSelected} onChange={selection.toggleAll} />
            </th> : null}
          <th>Asset</th>
          <th>IP Address</th>
          {canEdit ? <th>&nbsp;</th> : null}
        </tr>
        </thead>
        <tbody>
        {assets.assets.map((asset) => {
          return (
            <tr key={asset.uuid}>
              {canEdit ?
                <td>
                  <input className="form-check-input" type="checkbox" checked={selection.selected.includes(asset.uuid)}
                         onChange={() => selection.toggle(asset.uuid)} />
                </td> : null}
              <td><TaggedAsset asset={asset} /></td>
              <td><AssetIpAddresses addresses={asset.addresses} /></td>
              {canEdit ?
                <td className="text-end"><a href="#" onClick={(e) => onRemove(e, asset)}>Remove</a></td> : null}
            </tr>
          )
        })}
        </tbody>
      </table>

      {assets.total > PER_PAGE ?
        <div className="mt-3">
          <Paginator itemCount={assets.total} perPage={PER_PAGE} setPage={setPage} page={page} />
        </div> : null}
    </React.Fragment>
  )

}
