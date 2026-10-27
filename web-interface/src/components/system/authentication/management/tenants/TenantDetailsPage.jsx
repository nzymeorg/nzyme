import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import Routes from "../../../../../util/ApiRoutes";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../../util/ApiRoutes";
import {toast} from "react-toastify";
import TenantUsersTable from "../users/TenantUsersTable";
import TapPermissionsTable from "../taps/TapPermissionsTable";
import TenantSessions from "../sessions/TenantSessions";
import LocationsTable from "./locations/LocationsTable";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import {ORGANIZATION_MENU_ITEMS} from "../organizations/OrganizationMenuItems";
import {TENANT_MENU_ITEMS} from "./TenantMenuItems";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

function TenantDetailsPage() {

  usePageTitle("Tenant Details");

  const { organizationId } = useParams();
  const { tenantId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);
  const [redirect, setRedirect] = useState(false);

  const deleteTenant = function() {
    if (!confirm("Really delete tenant?")) {
      return;
    }

    authenticationManagementService.deleteTenantOfOrganization(organizationId, tenantId, function() {
      setRedirect(true);
      toast.success('Tenant deleted.');
    });
  }

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
  }, [organizationId, tenantId])

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)} />
  }

  if (!organization || !tenant) {
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
                <li className="breadcrumb-item active" aria-current="page">{tenant.name}</li>
              </ol>
            </nav>
          </div>

          <div className="col-md-3">
            <span className="float-end">
              <Link className="btn btn-secondary"
                 to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}>
                Back
              </Link>{' '}
              <Link className="btn btn-primary"
                 to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.EDIT(organization.id, tenant.id)}>
                Edit Tenant
              </Link>
            </span>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={TENANT_MENU_ITEMS(organization.id, tenant.id)}
                            activeRoute={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(organization.id, tenant.id)}/>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <h1>Tenant &quot;{tenant.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-8">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Description" slim={true} />

                    <p className="mb-0">
                      {tenant.description}
                    </p>
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
                    <CardTitleWithControls title="Delete Tenant" slim={true} />

                    <p>
                      You can only delete a tenant if it has no users and no taps.
                    </p>

                    <button className="btn btn-sm btn-danger" disabled={!tenant.is_deletable} onClick={deleteTenant}>
                      Delete Tenant
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All Active Sessions of Tenant" slim={true} />

                <TenantSessions organizationId={organization.id} tenantId={tenant.id}/>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default TenantDetailsPage;