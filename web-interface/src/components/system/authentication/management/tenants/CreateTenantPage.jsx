import React, {useEffect, useState} from "react";
import Routes from "../../../../../util/ApiRoutes";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import {Link, Navigate, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import TenantForm from "./TenantForm";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationMgmtService = new AuthenticationManagementService();

function CreateTenantPage() {

  usePageTitle("Create Tenant");

  const { organizationId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    authenticationMgmtService.findOrganization(organizationId, setOrganization);
  }, [organizationId])

  const onFormSubmitted = function (name,
                                    description,
                                    sessionTimeoutMinutes,
                                    sessionInactivityTimeoutMinutes,
                                    mfaTimeoutMinutes) {
    authenticationMgmtService.createTenantOfOrganization(organizationId, name, description,
        sessionTimeoutMinutes, sessionInactivityTimeoutMinutes, mfaTimeoutMinutes, function() {
      toast.success('Tenant created.');
      setRedirect(true);
    })
  }

  if (redirect) {
    return <Navigate to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.TENANTS_PAGE(organizationId)} />
  }

  if (!organization) {
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
                <li className="breadcrumb-item">
                  <Link to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}>{organization.name}</Link>
                </li>
                <li className="breadcrumb-item">
                  <Link to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.TENANTS_PAGE(organization.id)}>
                    Tenants
                  </Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Create</li>
              </ol>
            </nav>
          </div>

          <div className="col-2">
            <Link className="btn btn-secondary float-end"
               to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.TENANTS_PAGE(organization.id)}>
              Back
            </Link>
          </div>

          <div className="col-12">
            <h1>Create Tenant</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <TenantForm onClick={onFormSubmitted}
                            sessionTimeoutMinutes="12"
                            sessionTimeoutMinutesUnit="hours"
                            sessionInactivityTimeoutMinutes="15"
                            mfaTimeoutMinutes="5"
                            submitText="Create Tenant" />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default CreateTenantPage;