import React, {useEffect, useState} from "react";
import AuthenticationManagementService from "../../../../../../services/AuthenticationManagementService";
import {Link, Navigate, useParams} from "react-router-dom";
import LoadingSpinner from "../../../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../../../util/ApiRoutes";
import LocationForm from "./LocationForm";
import {toast} from "react-toastify";
import usePageTitle from "../../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

function CreateLocationPage() {

  usePageTitle("Create Tenant Location");

  const { organizationId } = useParams();
  const { tenantId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);

  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
  }, [organizationId, tenantId])

  const create = (name, description, latitude, longitude) => {
    authenticationManagementService.createTenantLocation(organization.id, tenant.id, name, description, latitude, longitude, () => {
      toast.success('Location created.');
      setRedirect(true);
    })
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS_PAGE(organization.id, tenant.id)} />
  }

  if (!organization || !tenant) {
    return <LoadingSpinner />
  }

  return (
      <div className="row">
        <div className="col-md-9">
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
                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS_PAGE(organization.id, tenant.id)}>
                  Locations
                </Link>
              </li>
              <li className="breadcrumb-item active" aria-current="page">Create</li>
            </ol>
          </nav>
        </div>

        <div className="col-md-3">
          <span className="float-end">
            <Link className="btn btn-secondary"
               to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS_PAGE(organization.id, tenant.id)}>
              Back
            </Link>
          </span>
        </div>

        <div className="col-md-12">
          <h1>Create Location</h1>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Create Location</h3>

                <LocationForm submitText="Create Location" onSubmit={create} />
              </div>
            </div>
          </div>
        </div>
      </div>
  )

}

export default CreateLocationPage;