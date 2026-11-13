import React, {useEffect, useState} from 'react';
import {Link, useParams} from "react-router-dom";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import Routes from "../../../../../util/ApiRoutes";
import ApiRoutes from "../../../../../util/ApiRoutes";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import {TENANT_MENU_ITEMS} from "./TenantMenuItems";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import usePageTitle from "../../../../../util/UsePageTitle";
import ConfigurationValue from "../../../../configuration/ConfigurationValue";
import ConfigurationModal from "../../../../configuration/modal/ConfigurationModal";

const authenticationManagementService = new AuthenticationManagementService();

export default function TenantDashboardsPage() {

  usePageTitle("Dashboards of Tenant");

  const { organizationId } = useParams();
  const { tenantId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);

  const [configuration, setConfiguration] = useState(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
  }, [organizationId, tenantId])

  useEffect(() => {
    setConfiguration(null);
    authenticationManagementService.getDashboardsConfigurationOfTenantOfOrganization(organizationId, tenantId,
      setConfiguration);
  }, [organizationId, tenantId, revision])

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
                          activeRoute={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DASHBOARDS_PAGE(organization.id, tenant.id)}/>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <h1>Dashboards of Tenant &quot;{tenant.name}&quot;</h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Dashboards" slim={true} />

              <p>
                This setting governs dashboards availability for this tenant. Displays of the tenant stop showing
                dashboards while dashboards are disabled.
              </p>

              {configuration ?
              <table className="table table-sm table-hover table-striped">
                <thead>
                <tr>
                  <th>Configuration</th>
                  <th>Value</th>
                  <th>Actions</th>
                </tr>
                </thead>
                <tbody>
                <tr>
                  <td>Are Dashboards enabled</td>
                  <td>
                    <ConfigurationValue value={configuration.dashboards_enabled.value}
                                        configKey={configuration.dashboards_enabled.key}
                                        boolean={true}/>
                  </td>
                  <td>
                    <ConfigurationModal config={configuration.dashboards_enabled}
                                        setGlobalConfig={setConfiguration}
                                        setLocalRevision={setRevision}
                                        organizationId={organization.id}
                                        tenantId={tenant.id}
                                        dbUpdateCallback={authenticationManagementService.updateDashboardsConfigurationOfTenantOfOrganization} />
                  </td>
                </tr>
                </tbody>
              </table> : <LoadingSpinner />}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}