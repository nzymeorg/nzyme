import React, {useEffect, useState} from 'react';
import {Link, Navigate, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import Routes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import UavTypeForm from "./UavTypeForm";
import UavService from "../../../services/UavService";
import LoadingSpinner from "../../misc/LoadingSpinner";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import usePageTitle from "../../../util/UsePageTitle";

const uavService = new UavService();

export default function EditCustomTypePage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [type, setType] = useState(null);

  const [redirect, setRedirect] = React.useState(false);

  usePageTitle(type ? `Edit Custom UAV Type: ${type.name}` : "Edit Custom UAV Type");

  useEffect(() => {
    uavService.findCustomType(setType, uuid, organizationId, tenantId);
  }, [uuid, organizationId, tenantId]);

  const onFormSubmitted = (matchType, matchValue, defaultClassification, type, model, name, onFailure) => {
    uavService.editCustomType(uuid, organizationId, tenantId, matchType, matchValue, defaultClassification, type, model, name,
        () => {
          toast.success("Custom UAV type updated.");
          setRedirect(true);
        }, onFailure)
  }

  if (redirect) {
    return <Navigate to={Routes.UAV.TYPES.INDEX} />
  }

  if (!type) {
    return <LoadingSpinner />
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
                <li className="breadcrumb-item">{type.name}</li>
                <li className="breadcrumb-item active">Edit</li>
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
            <h1>Edit Custom Unmanned Aerial Vehicle (UAV) Type &quot;{type.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Create Type" />

                <UavTypeForm onSubmit={onFormSubmitted}
                             matchType={type.match_type}
                             matchValue={type.match_value}
                             type={type.type}
                             model={type.model}
                             name={type.name}
                             defaultClassification={type.default_classification}
                             submitText="Edit Type" />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}