import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import ApiRoutes from "../../../../../util/ApiRoutes";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import Routes from "../../../../../util/ApiRoutes";
import TapPermissionForm from "./TapPermissionForm";
import {toast} from "react-toastify";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

function EditTapPermissionsPage() {

  usePageTitle("Edit Tap");

  const { organizationId } = useParams();
  const { tenantId } = useParams();
  const { tapUuid } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);
  const [tap, setTap] = useState(null);

  const [redirect, setRedirect] = useState(false);

  const onFormSubmitted = function (name, description, location, floor, latitude, longitude) {
    authenticationManagementService.editTapPermission(organization.id, tenant.id, tap.uuid, name, description, location, floor, latitude, longitude, () => {
      setRedirect(true);
      toast.success('Tap details updated.');
    })
  }

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
    authenticationManagementService.findTapPermission(organizationId, tenantId, tapUuid, setTap);
  }, [organizationId, tenantId])

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TAPS.DETAILS(organization.id, tenant.id, tap.uuid)} />
  }

  if (!organization || !tenant || !tap) {
    return <LoadingSpinner />
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
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.TAPS_PAGE(organization.id, tenant.id)}>
                    Taps
                  </Link>
                </li>
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TAPS.DETAILS(organization.id, tenant.id, tap.uuid)}>
                    {tap.name}
                  </Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Edit</li>
              </ol>
            </nav>
          </div>

          <div className="col-3">
            <span className="float-end">
              <Link className="btn btn-secondary"
                 to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TAPS.DETAILS(organization.id, tenant.id, tap.uuid)}>
                Back
              </Link>
            </span>
          </div>

          <div className="col-12">
            <h1>Edit Tap &quot;{tap.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Edit Tap Details</h3>

                <TapPermissionForm onClick={onFormSubmitted}
                                   name={tap.name}
                                   description={tap.description}
                                   floor={tap.floor_id}
                                   location={tap.location_id}
                                   latitude={tap.latitude}
                                   longitude={tap.longitude}
                                   defaultMapZoomLevel={(tap.latitude && tap.longitude) ? 18 : 1}
                                   submitText="Update Tap Settings" />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default EditTapPermissionsPage;