import React, {useEffect, useState} from "react";
import {useParams} from "react-router-dom";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import OrganizationHeader from "./OrganizationHeader";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import {ORGANIZATION_MENU_ITEMS} from "./OrganizationMenuItems";
import ApiRoutes from "../../../../../util/ApiRoutes";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import OrganizationAdminTable from "../users/orgadmins/OrganizationAdminTable";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

export default function OrganizationAdministratorsPage() {

  usePageTitle("Administrators of Organization");

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
        <OrganizationHeader organization={organization}/>

        <div className="row">
          <div className="col-12">
            <SectionMenuBar items={ORGANIZATION_MENU_ITEMS(organization.id)}
                            activeRoute={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.ADMINS_PAGE(organizationId)}/>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <h1>Administrators of Organization &quot;{organization.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Organization Administrators" slim={true} />

                <OrganizationAdminTable organization={organization}/>

                <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.ADMINS.CREATE(organization.id)}
                   className="btn btn-sm btn-secondary">
                  Create Organization Administrator
                </a>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}