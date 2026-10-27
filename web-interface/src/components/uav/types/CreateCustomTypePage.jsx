import React from 'react';
import {Link, Navigate} from "react-router-dom";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import UavService from "../../../services/UavService";
import Routes from "../../../util/ApiRoutes";
import {toast} from "react-toastify";
import UavTypeForm from "./UavTypeForm";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";

const uavService = new UavService();

export default function CreateCustomTypePage() {

  usePageTitle("Create custom UAV type");

  const [organizationId, tenantId] = useSelectedTenant();

  const [redirect, setRedirect] = React.useState(false);

  const onFormSubmitted = (matchType, matchValue, defaultClassification, type, model, name, onFailure) => {
    uavService.createCustomType(organizationId, tenantId, matchType, matchValue, defaultClassification, type, model, name,
        () => {
          toast.success("Custom UAV type created.");
          setRedirect(true);
        }, onFailure)
  }

  if (redirect) {
    return <Navigate to={Routes.UAV.TYPES.INDEX} />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">UAVs</li>
                <li className="breadcrumb-item"><Link to={Routes.UAV.TYPES.INDEX}>Types</Link></li>
                <li className="breadcrumb-item">Custom Types</li>
                <li className="breadcrumb-item active" aria-current="page">Create</li>
              </ol>
            </nav>
          </div>

          <div className="col-2">
            <Link className="btn btn-secondary float-end" to={Routes.UAV.TYPES.INDEX}>
              Back
            </Link>
          </div>
        </div>

        <div className="row">
          <div className="col-12">
            <h1>Create Custom Unmanned Aerial Vehicle (UAV) Type</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Create Type" />

                <UavTypeForm onSubmit={onFormSubmitted} matchType="EXACT" type="GENERIC" submitText="Create Type" />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}