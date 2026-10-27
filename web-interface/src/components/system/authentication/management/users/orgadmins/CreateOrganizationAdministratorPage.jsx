import React, {useEffect, useState} from "react";

import {toast} from "react-toastify";
import AuthenticationManagementService from "../../../../../../services/AuthenticationManagementService";
import ApiRoutes from "../../../../../../util/ApiRoutes";
import {Link, Navigate, useParams} from "react-router-dom";
import CreateUserForm from "../shared/CreateUserForm";
import LoadingSpinner from "../../../../../misc/LoadingSpinner";
import usePageTitle from "../../../../../../util/UsePageTitle";

const authenticationMgmtService = new AuthenticationManagementService();

function CreateOrganizationAdministratorPage() {

  usePageTitle("Create Organization Administrator");

  const { organizationId } = useParams();

  const [organization, setOrganization] = useState(null);

  const [errorMessage, setErrorMessage] = useState(null);
  const [redirect, setRedirect] = useState(false);

  const onFormSubmitted = function (email, password, name, disableMfa, callback) {
    authenticationMgmtService.createOrganizationAdministrator(organizationId, email, password, name, disableMfa, () => {
      // Success.
      toast.success('Organization administrator created.');
      setRedirect(true);
      callback();
    }, function (error) {
      // Error.
      setErrorMessage(error.response.data.message);
      callback();
    })
  }

  useEffect(() => {
    authenticationMgmtService.findOrganization(organizationId, setOrganization);
  }, [organizationId])

  if (!organization) {
    return <LoadingSpinner />
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.ADMINS_PAGE(organization.id)} />
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
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.ADMINS_PAGE(organization.id)}>
                    Organization Administrators
                  </Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Create</li>
              </ol>
            </nav>
          </div>

          <div className="col-2">
            <Link className="btn btn-secondary float-end"
               to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.ADMINS_PAGE(organization.id)}>
              Back
            </Link>
          </div>

          <div className="col-12">
            <h1>Create Organization Administrator</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CreateUserForm onClick={onFormSubmitted} errorMessage={errorMessage} submitText="Create Organization Administrator" />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default CreateOrganizationAdministratorPage;