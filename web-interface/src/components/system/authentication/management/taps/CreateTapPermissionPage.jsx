import React, {useEffect, useState} from "react";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import {Link, Navigate, useParams} from "react-router-dom";
import Routes from "../../../../../util/ApiRoutes";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import {toast} from "react-toastify";
import TapPermissionForm from "./TapPermissionForm";
import ApiRoutes from "../../../../../util/ApiRoutes";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationMgmtService = new AuthenticationManagementService();

function CreateTapPermissionPage() {

  usePageTitle("Create Tap");

  const { organizationId } = useParams();
  const { tenantId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    authenticationMgmtService.findOrganization(organizationId, setOrganization);
    authenticationMgmtService.findTenantOfOrganization(organizationId, tenantId, setTenant);
  }, [organizationId, tenantId])

  const onFormSubmitted = function (name, description, location, floor, latitude, longitude) {
    authenticationMgmtService.createTapPermission(organizationId, tenantId, name, description, location, floor, latitude, longitude,
        () => {
      toast.success('Tap created.');
      setRedirect(true);
    })
  }

  if (redirect) {
    return <Navigate to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.TAPS_PAGE(organizationId, tenantId)} />
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
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.TAPS_PAGE(organization.id, tenant.id)}>
                    Taps
                  </Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Create Tap</li>
              </ol>
            </nav>
          </div>

          <div className="col-2">
            <Link className="btn btn-secondary float-end"
               to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.TAPS_PAGE(organization.id, tenant.id)}>
              Back
            </Link>
          </div>

          <div className="col-12">
            <h1>Create Tap</h1>
          </div>

          <div className="row mt-3">
            <div className="col-xl-12 col-xxl-6">
              <div className="card">
                <div className="card-body">
                  <TapPermissionForm onClick={onFormSubmitted} submitText="Create Tap" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default CreateTapPermissionPage;