import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import AssetTagsService from "../../../../services/ethernet/AssetTagsService";
import AssetTagForm from "./AssetTagForm";

const assetTagsService = new AssetTagsService();

export default function EditAssetTagPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();

  const [tag, setTag] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);

  usePageTitle(tag ? `Edit Asset Tag: ${tag.name}` : "Edit Asset Tag");

  useEffect(() => {
    setTag(null);
    assetTagsService.findTag(uuid, organizationId, tenantId, setTag, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  const save = (name, description, onError) => {
    assetTagsService.updateTag(uuid, organizationId, tenantId, name, description, () => {
      toast.success("Tag updated.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.ASSETS.TAGS.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Tag not found. <Link to={ApiRoutes.ETHERNET.ASSETS.TAGS.INDEX}>Back to Tags</Link>
      </div>
    )
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.INDEX}>Assets</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.TAGS.INDEX}>Tags</Link></li>
              {tag ? <li className="breadcrumb-item">{tag.name}</li> : null}
              <li className="breadcrumb-item active" aria-current="page">Edit</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Edit Tag {tag ? <span>&quot;{tag.name}&quot;</span> : null}</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.ASSETS.TAGS.INDEX} className="btn btn-primary">Back to Tags</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              {tag ?
                <React.Fragment>
                  {tag.policies && tag.policies.length > 0 ?
                    <p className="text-muted">Used by segmentation policies: {tag.policies.join(", ")}.</p> : null}

                  <AssetTagForm name={tag.name}
                                description={tag.description ? tag.description : ""}
                                submitText="Save Tag"
                                onSubmit={save} />
                </React.Fragment>
                : <LoadingSpinner />}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
