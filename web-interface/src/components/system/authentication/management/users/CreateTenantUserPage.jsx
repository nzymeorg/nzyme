import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import Routes from "../../../../../util/ApiRoutes";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import CreateUserForm from "./shared/CreateUserForm";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../../util/ApiRoutes";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationMgmtService = new AuthenticationManagementService();

function CreateTenantUserPage() {

  usePageTitle("Create User of Tenant");

  const { organizationId } = useParams();
  const { tenantId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    authenticationMgmtService.findOrganization(organizationId, setOrganization);
    authenticationMgmtService.findTenantOfOrganization(organizationId, tenantId, setTenant);
  }, [organizationId, tenantId])

  const onFormSubmitted = function (email, password, name, disableMfa, callback) {
    authenticationMgmtService.createUserOfTenant(organizationId, tenantId, email, password, name, disableMfa, function() {
      // Success.
      toast.success('User created.');
      setRedirect(true);
      callback();
    }, function (error) {
      // Error.
      if (error.response.status === 422) {
        setErrorMessage("Quota exceeded. Please contact your administrator.");
      } else {
        setErrorMessage(error.response.data.message);
      }

      callback();
    })
  }

  if (redirect) {
    return <Navigate to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.USERS_PAGE(organizationId, tenantId)} />
  }

  if (!organization || !tenant) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">
                  <Link to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</Link>
                </li>
                <li className="breadcrumb-item">Organizations</li>
                <li className="breadcrumb-item">
                  <Link to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}>
                    {organization.name}
                  </Link>
                </li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.TENANTS_PAGE(organization.id)}>
                    Tenants
                  </Link>
                </li>
                <li className="breadcrumb-item">
                  <Link to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(organization.id, tenant.id)}>
                    {tenant.name}
                  </Link>
                </li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.USERS_PAGE(organization.id, tenant.id)}>
                    Users
                  </Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Create User</li>
              </ol>
            </nav>
          </div>

          <div className="col-2">
            <Link className="btn btn-secondary float-end"
               to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.USERS_PAGE(organization.id, tenant.id)}>
              Back
            </Link>
          </div>

          <div className="col-12">
            <h1>Create User</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CreateUserForm onClick={onFormSubmitted}
                                errorMessage={errorMessage}
                                submitText="Create User" />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default CreateTenantUserPage;