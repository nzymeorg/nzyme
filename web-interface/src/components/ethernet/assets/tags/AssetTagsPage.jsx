import React, {useContext, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import {toast} from "react-toastify";
import numeral from "numeral";
import SectionMenuBar from "../../../shared/SectionMenuBar";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import useUrlFilters from "../../../shared/filtering/useUrlFilters";
import AssetTagsService from "../../../../services/ethernet/AssetTagsService";
import {UserContext} from "../../../../App";
import {apiErrorMessage, userHasPermission} from "../../../../util/Tools";
import {isBehindLastPage} from "../../segmentation/SegmentationBulkActions";
import {ASSETS_MENU_ITEMS} from "../AssetsMenuItems";
import {ASSET_TAGS_FILTER_FIELDS} from "./AssetTagsFilterFields";
import AssetTagsTable from "./AssetTagsTable";
import AssetTagSuggestionsTable from "./AssetTagSuggestionsTable";

const assetTagsService = new AssetTagsService();

const SHOWN_SKIPPED = 10;

export default function AssetTagsPage() {

  usePageTitle("Ethernet Asset Tags");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_assets_manage");

  const [filters, setFilters] = useUrlFilters(ASSET_TAGS_FILTER_FIELDS);
  const [orderColumn, setOrderColumn] = useState("name");
  const [orderDirection, setOrderDirection] = useState("ASC");
  const [page, setPage] = useState(1);

  const perPage = 25;

  const [tags, setTags] = useState(null);
  const [suggestions, setSuggestions] = useState(null);
  const [deleteError, setDeleteError] = useState(null);
  const [skipped, setSkipped] = useState(null);
  const [applying, setApplying] = useState(false);
  const [revision, setRevision] = useState(new Date());

  const applyFilters = (newFilters) => {
    setPage(1);
    setFilters(newFilters);
  }

  const applyOrderColumn = (column) => {
    setPage(1);
    setOrderColumn(column);
  }

  const applyOrderDirection = (direction) => {
    setPage(1);
    setOrderDirection(direction);
  }

  // Not reset while loading again: the table keeps showing the assets of a tag while one of them is removed.
  useEffect(() => {
    assetTagsService.findTags(
      organizationId, tenantId, filters, orderColumn, orderDirection, perPage, (page-1)*perPage, (response) => {
        if (isBehindLastPage(response.tags, response.total, page)) {
          setPage(Math.ceil(response.total / perPage));
        } else {
          setTags(response);
        }
      }
    );
  }, [organizationId, tenantId, filters, orderColumn, orderDirection, page, revision]);

  useEffect(() => {
    assetTagsService.findSuggestions(organizationId, tenantId, setSuggestions);
  }, [organizationId, tenantId, revision]);

  const reload = () => setRevision(new Date());

  const refresh = () => {
    setApplying(false);
    reload();
  }

  const onDelete = (e, tag) => {
    e.preventDefault();

    if (!confirm("Really delete the tag \"" + tag.name + "\"? It is taken off " + tag.asset_count +
        (tag.asset_count === 1 ? " asset." : " assets."))) {
      return;
    }

    setDeleteError(null);
    setSkipped(null);
    assetTagsService.deleteTag(tag.uuid, organizationId, tenantId, () => {
      toast.success("Tag deleted.");
      reload();
    }, (error) => {
      toast.error("Could not delete the tag.");
      setDeleteError({name: tag.name, message: apiErrorMessage(error, "Could not delete the tag.")});
    });
  }

  const bulk = canEdit ? {
    organizationId: organizationId,
    tenantId: tenantId,
    filters: filters,
    countAll: (onCount, onError) => {
      assetTagsService.findTags(organizationId, tenantId, null, orderColumn, orderDirection, 1, 0,
        (response) => onCount(response.total), onError);
    },
    onDone: (result) => {
      setDeleteError(null);
      setSkipped(result.skipped && result.skipped.length > 0 ? result.skipped : null);
      reload();
    }
  } : null;

  const onApply = (e, suggestion) => {
    e.preventDefault();

    const assign = (tagUuid) => {
      assetTagsService.assignTag(tagUuid, suggestion.asset.uuid, organizationId, tenantId, () => {
        toast.success("Tag " + suggestion.tag + " put on the asset.");
        setApplying(false);
        reload();
      }, () => {
        toast.error("Could not put tag " + suggestion.tag + " on the asset.");
        setApplying(false);
        reload();
      });
    }

    setApplying(true);

    if (suggestion.tag_uuid) {
      assign(suggestion.tag_uuid);
      return;
    }

    assetTagsService.createTag(organizationId, tenantId, suggestion.tag, null, (tag) => assign(tag.uuid), (error) => {
      toast.error(apiErrorMessage(error, "Could not create the tag."));
      setApplying(false);
      reload();
    });
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={ASSETS_MENU_ITEMS}
                          activeRoute={ApiRoutes.ETHERNET.ASSETS.TAGS.INDEX} />
        </div>
      </div>

      {deleteError ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-danger mb-0">
              <strong>&quot;{deleteError.name}&quot; was not deleted.</strong> {deleteError.message}
            </div>
          </div>
        </div> : null}

      {skipped ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-warning alert-dismissible mb-0">
              <strong>
                {skipped.length === 1 ? "1 tag was" : numeral(skipped.length).format("0,0") + " tags were"} skipped
                and not deleted.
              </strong>
              <ul className="mb-0">
                {skipped.slice(0, SHOWN_SKIPPED).map((s) => <li key={s.uuid}>{s.name}: {s.reason}</li>)}
                {skipped.length > SHOWN_SKIPPED ?
                  <li>and {numeral(skipped.length - SHOWN_SKIPPED).format("0,0")} more</li> : null}
              </ul>
              <button type="button" className="btn-close" title="Dismiss" onClick={() => setSkipped(null)}></button>
            </div>
          </div>
        </div> : null}

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Filters" />

              <Filters filters={filters}
                       setFilters={applyFilters}
                       fields={ASSET_TAGS_FILTER_FIELDS}
                       onMonitorsReady={() => {}} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Tags" refreshAction={refresh} />

              <p className="text-muted">
                Labels on assets. Usable as source or destination in segmentation policies.
              </p>

              {canEdit ?
                <p>
                  <Link to={ApiRoutes.ETHERNET.ASSETS.TAGS.CREATE} className="btn btn-sm btn-primary">
                    Create Tag
                  </Link>
                </p> : null}

              <AssetTagsTable tags={tags}
                              organizationId={organizationId}
                              tenantId={tenantId}
                              canEdit={canEdit}
                              onDelete={onDelete}
                              onChange={reload}
                              page={page}
                              setPage={setPage}
                              perPage={perPage}
                              orderColumn={orderColumn}
                              setOrderColumn={applyOrderColumn}
                              orderDirection={orderDirection}
                              setOrderDirection={applyOrderDirection}
                              filterFields={ASSET_TAGS_FILTER_FIELDS}
                              setFilters={applyFilters}
                              bulk={bulk} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Suggested Tags" refreshAction={refresh} />

              <p className="text-muted">
                Assets that accepted connections on well-known ports and do not have the matching tag.
              </p>

              <AssetTagSuggestionsTable suggestions={suggestions}
                                        canEdit={canEdit}
                                        applying={applying}
                                        onApply={onApply} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
