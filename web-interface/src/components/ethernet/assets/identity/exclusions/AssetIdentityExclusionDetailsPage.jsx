import React, {useContext, useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../../util/ApiRoutes";
import usePageTitle from "../../../../../util/UsePageTitle";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import AssetsService from "../../../../../services/ethernet/AssetsService";
import {UserContext} from "../../../../../App";
import {userHasPermission} from "../../../../../util/Tools";
import AssetIdentityExclusionImpact from "./AssetIdentityExclusionImpact";
import {typeTitle} from "./AssetIdentityExclusionForm";

const assetsService = new AssetsService();

export default function AssetIdentityExclusionDetailsPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_assets_manage");

  const [exclusion, setExclusion] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(exclusion ? `Asset Identity Exclusion: ${exclusion.name}` : "Asset Identity Exclusion Details");

  useEffect(() => {
    setExclusion(null);
    assetsService.findAssetIdentityExclusion(uuid, organizationId, tenantId, setExclusion, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  const onToggleEnabled = (e) => {
    e.preventDefault();

    assetsService.setAssetIdentityExclusionEnabled(uuid, organizationId, tenantId, !exclusion.enabled, () => {
      toast.success(exclusion.enabled ? "Exclusion disabled." : "Exclusion enabled.");
      setRevision(new Date());
    });
  }

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this exclusion? Findings for the assets it covers are detected again.")) {
      return;
    }

    assetsService.deleteAssetIdentityExclusion(uuid, organizationId, tenantId, () => {
      toast.success("Exclusion deleted.");
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Exclusion not found. <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.INDEX}>Back to Exclusions</Link>
      </div>
    )
  }

  if (!exclusion) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.INDEX}>Assets</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.INDEX}>Identity Exclusions</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{exclusion.name}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <h1>Exclusion &quot;{exclusion.name}&quot;</h1>
        </div>

        <div className="col-6 text-end">
          <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.INDEX} className="btn btn-secondary me-1">Back</Link>
          {canEdit ?
            <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.EDIT(exclusion.uuid)} className="btn btn-primary">Edit</Link>
            : null}
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Name</dt>
                <dd>{exclusion.name}</dd>
                <dt>Description</dt>
                <dd>
                  {exclusion.description ?
                    <span style={{whiteSpace: "pre-wrap"}}>{exclusion.description}</span>
                    : <span className="text-muted">No description.</span>}
                </dd>
                <dt>Status</dt>
                <dd>
                  {exclusion.enabled ? "Enabled" : <span className="text-warning">Disabled</span>}
                  {exclusion.enabled ? null :
                    <span className="text-muted"> (kept, but not applied until enabled again)</span>}
                </dd>
                <dt>Last Matched</dt>
                <dd>
                  {exclusion.last_matched_at ?
                    <span title={moment(exclusion.last_matched_at).format()}>
                      {moment(exclusion.last_matched_at).fromNow()}
                    </span>
                    : <span className="text-muted">Never</span>}
                </dd>
                <dt>Suppressed Alerts</dt>
                <dd title="Findings that were not raised because of this exclusion">
                  {numeral(exclusion.match_count).format("0,0")}
                </dd>
                <dt>Created</dt>
                <dd title={moment(exclusion.created_at).format()}>
                  {moment(exclusion.created_at).fromNow()}
                  {exclusion.created_by_name ? <span className="text-muted"> by {exclusion.created_by_name}</span> : null}
                </dd>
                <dt>Last Updated</dt>
                <dd className="mb-0" title={moment(exclusion.updated_at).format()}>
                  {moment(exclusion.updated_at).fromNow()}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Criteria" />

              <p className="text-muted">
                A finding is not raised for an asset when all of these match.
              </p>

              <dl className="mb-0">
                <dt>{exclusion.mac_is_prefix ? "Vendor Prefix" : "MAC Address"}</dt>
                <dd className="machine-data">{exclusion.mac}</dd>
                <dt>Detection Types</dt>
                <dd className="mb-0">
                  {exclusion.types && exclusion.types.length > 0 ?
                    exclusion.types.map((t) => typeTitle(t)).join(", ")
                    : <span className="text-muted">All</span>}
                </dd>
              </dl>
            </div>
          </div>

          {canEdit ?
            <div className="row mt-3">
              <div className="col-6">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title={exclusion.enabled ? "Disable Exclusion" : "Enable Exclusion"} slim={true} />

                    <p>
                      {exclusion.enabled ?
                        "A disabled exclusion is kept but no longer applied. Findings for the assets it covers " +
                        "are detected again."
                        : "Enable the exclusion to apply it again with the next detection run."}
                    </p>

                    <button className={"btn btn-sm " + (exclusion.enabled ? "btn-warning" : "btn-success")}
                            onClick={onToggleEnabled}>
                      {exclusion.enabled ? "Disable Exclusion" : "Enable Exclusion"}
                    </button>
                  </div>
                </div>
              </div>

              <div className="col-6">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Delete Exclusion" slim={true} />

                    <p>
                      Findings for the assets this exclusion covers are detected again after it is deleted.
                    </p>

                    <button className="btn btn-sm btn-danger" onClick={onDelete}>Delete Exclusion</button>
                  </div>
                </div>
              </div>
            </div> : null}
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Covered Assets"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Known assets this exclusion currently covers. Assets discovered later that match the criteria are
                covered as well.
              </p>

              <AssetIdentityExclusionImpact impact={exclusion.impact} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
