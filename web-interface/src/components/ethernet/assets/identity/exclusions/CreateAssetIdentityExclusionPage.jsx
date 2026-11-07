import React, {useContext, useState} from "react";
import {Link, Navigate, useLocation} from "react-router-dom";
import {UserContext} from "../../../../../App";
import {userHasPermission} from "../../../../../util/Tools";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../../util/ApiRoutes";
import usePageTitle from "../../../../../util/UsePageTitle";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import AssetsService from "../../../../../services/ethernet/AssetsService";
import AssetIdentityExclusionForm from "./AssetIdentityExclusionForm";

const assetsService = new AssetsService();

const prefillFromMac = (mac, type) => {
  return {
    name: "Exclude " + mac,
    description: "Created from an asset identity finding.",
    mac: mac,
    types: type ? [type] : []
  };
}

export default function CreateAssetIdentityExclusionPage() {

  usePageTitle("Create Asset Identity Exclusion");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "ethernet_assets_manage");
  const query = new URLSearchParams(useLocation().search);
  const mac = query.get("mac");
  const type = query.get("type");

  const [redirect, setRedirect] = useState(false);

  const create = (payload, onError) => {
    assetsService.createAssetIdentityExclusion(organizationId, tenantId, payload, () => {
      toast.success("Exclusion created.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.INDEX} />
  }

  if (!canManage) {
    return (
      <div className="alert alert-warning">
        You do not have permission to create asset identity exclusions.{' '}
        <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.INDEX}>Back to Exclusions</Link>
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
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.INDEX}>Identity Exclusions</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Create</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Create Exclusion</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.INDEX} className="btn btn-primary">Back to Exclusions</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <AssetIdentityExclusionForm organizationId={organizationId}
                                            tenantId={tenantId}
                                            initialValues={mac ? prefillFromMac(mac, type) : {}}
                                            submitText="Create Exclusion"
                                            onSubmit={create} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
