import React, {useContext, useEffect, useState} from "react";
import {Link, useParams} from "react-router-dom";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import {ORGANIZATION_MENU_ITEMS} from "./OrganizationMenuItems";
import ApiRoutes from "../../../../../util/ApiRoutes";
import TenantsTable from "../tenants/TenantsTable";
import OrganizationHeader from "./OrganizationHeader";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

export default function OrganizationTenantsPage() {

  usePageTitle("Tenants of Organization");

  const { organizationId } = useParams();

  const [organization, setOrganization] = useState(null);

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
  }, [organizationId])

  if (!organization) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <OrganizationHeader organization={organization} />

        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={ORGANIZATION_MENU_ITEMS(organization.id)}
                            activeRoute={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.TENANTS_PAGE(organizationId)}/>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <h1>Tenants of Organization &quot;{organization.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Tenants" slim={true} />

                <p className="mb-2">
                  The following tenants are part of this organization.
                </p>

                <TenantsTable organizationId={organization.id}/>

                <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.CREATE(organization.id)}
                   className="btn btn-sm btn-secondary">
                  Create Tenant
                </Link>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}