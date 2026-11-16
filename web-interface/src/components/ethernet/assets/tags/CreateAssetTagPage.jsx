import React, {useState} from "react";
import {Link, Navigate} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import AssetTagsService from "../../../../services/ethernet/AssetTagsService";
import AssetTagForm from "./AssetTagForm";

const assetTagsService = new AssetTagsService();

export default function CreateAssetTagPage() {

  usePageTitle("Create Asset Tag");

  const [organizationId, tenantId] = useSelectedTenant();

  const [redirect, setRedirect] = useState(false);

  const create = (name, description, onError) => {
    assetTagsService.createTag(organizationId, tenantId, name, description, () => {
      toast.success("Tag created.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.ASSETS.TAGS.INDEX} />
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
              <li className="breadcrumb-item active" aria-current="page">Create</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Create Tag</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.ASSETS.TAGS.INDEX} className="btn btn-primary">Back to Tags</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <AssetTagForm submitText="Create Tag" onSubmit={create} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
