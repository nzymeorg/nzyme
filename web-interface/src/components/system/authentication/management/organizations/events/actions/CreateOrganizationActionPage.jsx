import React, {useEffect, useState} from "react";
import Routes from "../../../../../../../util/ApiRoutes";
import ApiRoutes from "../../../../../../../util/ApiRoutes";
import {Link, Navigate, useParams} from "react-router-dom";
import AuthenticationManagementService from "../../../../../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../../../../../misc/LoadingSpinner";
import CreateActionProxy from "../../../../../events/shared/forms/CreateActionProxy";
import CreateActionSelect from "../../../../../events/shared/forms/CreateActionSelect";
import usePageTitle from "../../../../../../../util/UsePageTitle";

const authenticationMgmtService = new AuthenticationManagementService();

function CreateOrganizationActionPage() {

  usePageTitle("Create Organization Action");

  const { organizationId } = useParams();

  const [complete, setComplete] = useState(false);

  const [organization, setOrganization] = useState(null);
  const [type, setType] = useState("");

  useEffect(() => {
    authenticationMgmtService.findOrganization(organizationId, setOrganization);
  }, [organizationId])

  if (!organization) {
    return <LoadingSpinner />
  }

  if (complete) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS_PAGE(organization.id)} />
  }

  return (
      <React.Fragment>
        <div className="row">

          <div className="col-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</Link>
                </li>
                <li className="breadcrumb-item">Organizations</li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}>
                    {organization.name}
                  </Link>
                </li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS_PAGE(organization.id)}>
                    Events &amp; Actions
                  </Link>
                </li>
                <li className="breadcrumb-item">Actions</li>
                <li className="breadcrumb-item active" aria-current="page">Create</li>
              </ol>
            </nav>
          </div>

          <div className="col-2">
            <Link className="btn btn-primary float-end"
               to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EVENTS_PAGE(organization.id)}>
              Back
            </Link>
          </div>
        </div>

        <div className="row">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Create Action</h3>

                <CreateActionSelect type={type} setType={setType} />
                <CreateActionProxy type={type} setComplete={setComplete} organizationId={organization.id} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default CreateOrganizationActionPage;