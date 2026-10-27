import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import Routes from "../../../../../util/ApiRoutes";
import {toast} from "react-toastify";
import TenantForm from "./TenantForm";
import ApiRoutes from "../../../../../util/ApiRoutes";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationMgmtService = new AuthenticationManagementService();

function EditTenantPage() {

  usePageTitle("Edit Tenant");

  const { organizationId } = useParams();
  const { tenantId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);
  const [redirect, setRedirect] = useState(false);

  const onFormSubmitted = function (name,
                                    description,
                                    sessionTimeoutMinutes,
                                    sessionInactivityTimeoutMinutes,
                                    mfaTimeoutMinutes) {
    authenticationMgmtService.editTenantOfOrganization(
        organization.id,
        tenant.id,
        name,
        description,
        sessionTimeoutMinutes,
        sessionInactivityTimeoutMinutes,
        mfaTimeoutMinutes,
        function() {
      setRedirect(true);
      toast.success('Tenant details updated.');
    })
  }

  useEffect(() => {
    authenticationMgmtService.findOrganization(organizationId, setOrganization);
    authenticationMgmtService.findTenantOfOrganization(organizationId, tenantId, setTenant);
  }, [organizationId])

  if (!organization || !tenant) {
    return <LoadingSpinner />
  }

  if (redirect) {
    return <Navigate to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(organization.id, tenant.id)} />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-9">
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
                <li className="breadcrumb-item active" aria-current="page">Edit</li>
              </ol>
            </nav>
          </div>

          <div className="col-3">
            <span className="float-end">
              <Link className="btn btn-secondary"
                 to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(organization.id, tenant.id)}>
                Back
              </Link>{' '}
            </span>
          </div>

          <div className="col-12">
            <h1>Tenant &quot;{tenant.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Edit Tenant Details</h3>

                <TenantForm onClick={onFormSubmitted}
                            name={tenant.name}
                            description={tenant.description}
                            sessionTimeoutMinutes={tenant.session_timeout_minutes.toString()}
                            sessionInactivityTimeoutMinutes={tenant.session_inactivity_timeout_minutes.toString()}
                            mfaTimeoutMinutes={tenant.mfa_timeout_minutes.toString()}
                            submitText="Edit Tenant"/>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default EditTenantPage;