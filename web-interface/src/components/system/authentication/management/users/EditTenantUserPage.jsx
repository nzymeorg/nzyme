import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import Routes from "../../../../../util/ApiRoutes";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../../util/ApiRoutes";
import EditUserForm from "./shared/EditUserForm";
import EditPasswordForm from "./shared/EditPasswordForm";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

function EditTenantUserPage() {

  usePageTitle("Edit Tenant User");

  const { organizationId } = useParams();
  const { tenantId } = useParams();
  const { userId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);
  const [user, setUser] = useState(null);

  const [redirect, setRedirect] = useState(false);

  const onEditDetailsFormSubmitted = function (email, name, disableMfa, callback) {
    authenticationManagementService.editUserOfTenant(organization.id, tenant.id, user.id, name, email, disableMfa, function() {
      // Success.
      toast.success('User updated.');
      setRedirect(true);
      callback();
    }, function (error) {
      // Error.
      setErrorMessage(error.response.data.message);
      callback();
    })
  }

  const onEditPasswordFormSubmitted = function (password, callback) {
    authenticationManagementService.editUserOfTenantPassword(organization.id, tenant.id, user.id, password, function() {
      // Success.
      toast.success('Password updated.');
      setRedirect(true);
      callback();
    })
  }

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
    authenticationManagementService.findUserOfTenant(organizationId, tenantId, userId, setUser);
  }, [organizationId, tenantId])

  if (redirect) {
    return <Navigate to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.USERS.DETAILS(organization.id, tenant.id, user.id)} />
  }

  if (!organization || !tenant || !user) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-9">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item">
                <Link to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</Link>
              </li>
              <li className="breadcrumb-item">Organizations</li>
              <li className="breadcrumb-item">
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}>
                  {organization.name}
                </Link>
              </li>
              <li className="breadcrumb-item">
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.TENANTS_PAGE(organization.id)}>
                  Tenants
                </Link>
              </li>
              <li className="breadcrumb-item">
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(organization.id, tenant.id)}>
                {tenant.name}
                </Link>
              </li>
              <li className="breadcrumb-item">
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.USERS_PAGE(organization.id, tenant.id)}>
                  Users
                </Link>
              </li>
              <li className="breadcrumb-item">
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.USERS.DETAILS(organization.id, tenant.id, user.id)}>
                  {user.email}
                </Link>
              </li>
              <li className="breadcrumb-item active"aria-current="page">Edit</li>
            </ol>
          </nav>
        </div>

        <div className="col-md-3">
              <span className="float-end">
                <Link className="btn btn-secondary"
                   to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.USERS.DETAILS(organization.id, tenant.id, user.id)}>
                  Back
                </Link>
              </span>
        </div>
      </div>

      <div className="row">
        <div className="col-md-12">
          <h1>User &quot;{user.email}&quot;</h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-8">
          <div className="row">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <h3>Edit User Details</h3>

                  <EditUserForm
                      email={user.email}
                      name={user.name}
                      disableMfa={user.mfa_disabled}
                      submitText="Edit User"
                      errorMessage={errorMessage}
                      onClick={onEditDetailsFormSubmitted} />
                </div>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-12">
              <div className="card">
                <div className="card-body">
                  <h3>Change Password</h3>

                  <p>
                    Changing the password will log out the user and prompt them to log in again with the
                    new password.
                  </p>

                  <EditPasswordForm
                      submitText="Change Password"
                      onClick={onEditPasswordFormSubmitted} />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>


    </React.Fragment>
  )

}

export default EditTenantUserPage;