import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import AuthenticationManagementService from "../../../../../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../../../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../../../../util/ApiRoutes";
import {toast} from "react-toastify";
import FloorForm from "./FloorForm";
import usePageTitle from "../../../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

function CreateFloorPage() {

  usePageTitle("Create Floor");

  const { organizationId } = useParams();
  const { tenantId } = useParams();
  const { locationId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);
  const [location, setLocation] = useState(null);

  const [errorMessage, setErrorMessage] = useState(null);
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
    authenticationManagementService.findTenantLocation(locationId, organizationId, tenantId, setLocation)
  }, [organizationId, tenantId, locationId])

  const create = (number, name, pathLossExponent) => {
    authenticationManagementService.createFloorOfTenantLocation(organization.id, tenant.id, location.id, number, name, pathLossExponent, () => {
      toast.success('Floor created.');
      setRedirect(true);
    }, (error) => {
      setErrorMessage(error.response.data.message)
    })
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.DETAILS(organization.id, tenant.id, location.id)} />
  }

  if (!organization || !tenant || !location) {
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
              <li className="breadcrumb-item">
                <Link
                  to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.DETAILS(organization.id, tenant.id, location.id)}>
                  {location.name}
                </Link>
              </li>
              <li className="breadcrumb-item">Floors</li>
              <li className="breadcrumb-item active" aria-current="page">Create Floor</li>
            </ol>
          </nav>
        </div>

        <div className="col-md-3">
          <span className="float-end">
            <Link className="btn btn-secondary"
               to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.LOCATIONS.DETAILS(organization.id, tenant.id, location.id)}>
              Back
            </Link>
          </span>
        </div>

        <div className="col-md-12">
          <h1>Create Floor at Location &quot;{location.name}&quot;</h1>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Create Floor</h3>

                <FloorForm submitText="Create Floor" onSubmit={create} errorMessage={errorMessage} />
              </div>
            </div>
          </div>
        </div>
      </div>
  )
}

export default CreateFloorPage;