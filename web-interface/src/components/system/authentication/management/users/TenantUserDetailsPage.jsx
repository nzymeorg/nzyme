import React, {useEffect, useState} from "react";
import {Navigate, useParams} from "react-router-dom";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import Routes from "../../../../../util/ApiRoutes";
import ApiRoutes from "../../../../../util/ApiRoutes";
import moment from "moment";
import {toast} from "react-toastify";
import LastUserActivity from "./shared/LastUserActivity";
import TenantUserPermissions from "./TenantUserPermissions";
import TenantUserTaps from "./TenantUserTaps";
import LoginThrottleWarning from "./shared/LoginThrottleWarning";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

function TenantUserDetailsPage() {

  usePageTitle("Details of Tenant User");

  const { organizationId } = useParams();
  const { tenantId } = useParams();
  const { userId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);
  const [user, setUser] = useState(null);
  const [taps, setTaps] = useState(null);
  const [isDeletable, setIsDeletable] = useState(null);
  const [apiKeys, setApiKeys] = useState(null);

  const [localRevision, setLocalRevision] = useState(0);

  const [allPermissions, setAllPermissions] = useState(null);

  const [redirect, setRedirect] = useState(false);

  const deleteUser = function() {
    if (!confirm("Really delete user?")) {
      return;
    }

    authenticationManagementService.deleteUserOfTenant(organizationId, tenantId, userId, function() {
      setRedirect(true);
      toast.success('User deleted.');
    });
  }

  const resetMfa = function() {
    if (!confirm("Really reset MFA credentials for this user?")) {
      return;
    }

    authenticationManagementService.resetMFAOfUserOfTenant(organizationId, tenantId, userId, function() {
      toast.success('MFA successfully reset.');
    });
  }

  const apiKeysTable = () => {
    if (!apiKeys || apiKeys.length === 0) {
      return <div className="alert alert-info mb-0">No API keys.</div>
    }

    return (
      <table className="table table-sm table-hover table-striped mb-0">
        <thead>
        <tr>
          <th>Name</th>
          <th>Created</th>
          <th>Expires</th>
          <th>Last Activity</th>
        </tr>
        </thead>
        <tbody>
        {apiKeys.map((key, i) => {
          return (
            <tr key={"apikey-" + i}>
              <td>{key.name}</td>
              <td title={moment(key.created_at).format()}>
                {moment(key.created_at).fromNow()}
              </td>
              <td>
                {key.expires_at ? moment(key.expires_at).format() : "Never"}
              </td>
              <td>
                {key.last_activity ? moment(key.last_activity).format() : "Never used"}
              </td>
            </tr>
          )
        })}
        </tbody>
      </table>
    )
  }

  const onTapPermissionsUpdated = function () {
    setLocalRevision(localRevision + 1);
  }

  useEffect(() => {
    setUser(null);
    setTaps(null);

    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
    authenticationManagementService.findUserOfTenant(organizationId, tenantId, userId, setUser, setIsDeletable, setApiKeys);

    authenticationManagementService.findAllTapPermissions(organizationId, tenantId, setTaps, 250, 0);

    authenticationManagementService.findAllExistingPermissions(setAllPermissions);
  }, [organizationId, tenantId, localRevision])

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.USERS_PAGE(organization.id, tenant.id)} />
  }

  if (!organization || !tenant || !user || !allPermissions || !taps) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-9">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">
                  <a href={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</a>
                </li>
                <li className="breadcrumb-item">Organizations</li>
                <li className="breadcrumb-item">
                  <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}>
                    {organization.name}
                  </a>
                </li>
                <li className="breadcrumb-item">
                  <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.TENANTS_PAGE(organization.id)}>
                    Tenants
                  </a>
                </li>
                <li className="breadcrumb-item">
                  <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(organization.id, tenant.id)}>
                  {tenant.name}
                  </a>
                </li>
                <li className="breadcrumb-item">
                  <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.USERS_PAGE(organization.id, tenant.id)}>
                    Users
                  </a>
                </li>
                <li className="breadcrumb-item active" aria-current="page">{user.email}</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-3">
            <span className="float-end">
              <a className="btn btn-secondary"
                 href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.USERS_PAGE(organization.id, tenant.id)}>
                Back
              </a>{' '}
              <a className="btn btn-primary" href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.USERS.EDIT(organization.id, tenant.id, user.id)}>
                Edit User
              </a>
            </span>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12">
            <h1>User &quot;{user.email}&quot;</h1>
          </div>
        </div>

        <LoginThrottleWarning show={user.is_login_throttled} />

        <div className="row mt-3">
          <div className="col-md-8">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>User Details</h3>

                    <dl className="mb-0">
                      <dt>Email Address / Username</dt>
                      <dd>{user.email}</dd>

                      <dt>Full Name</dt>
                      <dd>{user.name}</dd>

                      <dt>MFA</dt>
                      <dd>
                        {user.mfa_disabled ?
                            <span className="text-warning">Disabled</span>
                            : <span className="text-success">Enabled</span>
                        }
                      </dd>

                      <dt>Created At</dt>
                      <dd title={moment(user.created_at).format()}>
                        {moment(user.created_at).fromNow()}
                      </dd>

                      <dt>Updated At</dt>
                      <dd title={moment(user.updated_at).format()}>
                        {moment(user.updated_at).fromNow()}
                      </dd>
                    </dl>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Permissions</h3>

                    <h4>Tap Data</h4>

                    <p>
                      You can decide to restrict access to data of only specific taps but make sure to understand the
                      features &amp; functionality description below, because not all features restrict tap data access.
                    </p>

                    <TenantUserTaps taps={taps} user={user} onTapPermissionsUpdated={onTapPermissionsUpdated} />

                    <h4 className="mt-4">Features &amp; Functionality</h4>

                    <p>
                      On top of tap data access, a user can be restricted to certain features and functionality in nzyme.
                      While the user will always be limited to seeing data from taps that belong to their tenant, some
                      functionality will not be limited to the taps selected above. For example, if a user is allowed to
                      view alerts, they might see alerts referencing data of taps that are not selected above. Any such
                      permission is specifically marked below.
                    </p>

                    <TenantUserPermissions user={user} allPermissions={allPermissions} />
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>API Keys</h3>

                    {apiKeysTable()}
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Last Activity</h3>

                    <LastUserActivity
                        timestamp={user.last_activity}
                        remoteAddress={user.last_remote_ip}
                        remoteCountry={user.last_geo_country}
                        remoteCity={user.last_geo_city}
                        remoteAsn={user.last_geo_asn} />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Delete User</h3>

                    <p>
                      Note that you cannot delete yourself.
                    </p>

                    <button className="btn btn-sm btn-danger" onClick={deleteUser} disabled={!isDeletable}>
                      Delete User
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Reset MFA</h3>

                    <p>
                      The user will be logged out and prompted to set up new MFA credentials after logging back in.
                    </p>

                    <button className="btn btn-sm btn-warning" onClick={resetMfa}>
                      Reset MFA Credentials
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default TenantUserDetailsPage;