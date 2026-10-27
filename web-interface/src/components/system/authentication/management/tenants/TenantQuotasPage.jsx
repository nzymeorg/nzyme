import React, {useEffect, useState} from 'react';
import {Link, useParams} from "react-router-dom";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import Routes from "../../../../../util/ApiRoutes";
import ApiRoutes from "../../../../../util/ApiRoutes";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import {TENANT_MENU_ITEMS} from "./TenantMenuItems";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import TenantQuotasTable from "./TenantQuotasTable";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

export default function TenantQuotasPage() {

  usePageTitle("Quotas of Tenant");

  const { organizationId } = useParams();
  const { tenantId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);

  const [quotas, setQuotas] = useState(null);

  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
  }, [organizationId, tenantId])

  useEffect(() => {
    setQuotas(null);
    authenticationManagementService.getQuotasOfTenantOfOrganization(organizationId, tenantId, setQuotas)
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
                            activeRoute={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.QUOTAS_PAGE(organization.id, tenant.id)}/>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <h1>Quotas of Tenant &quot;{tenant.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Quotas" slim={true} />

                <p className="text-muted">
                  Organization administrators can set quotas for certain resource types. Once a quota is reached, no
                  new resources can be created until existing ones are deleted.
                </p>

                <p className="text-muted">
                  It is possible to configure a quota that is lower than the current usage. In this scenario, no new
                  resources can be created, but nzyme will not automatically delete any existing resources.
                  Instead, you will see a warning that you are above the quota and should decide which resources
                  to manually delete.
                </p>

                <p className="text-muted">
                  You can <a href="https://go.nzyme.org/quotas">learn more about quotas in the documentation</a>.
                </p>

                <TenantQuotasTable organization={organization}
                                   tenant={tenant}
                                   quotas={quotas}
                                   onUpdate={() => setRevision(new Date())} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}