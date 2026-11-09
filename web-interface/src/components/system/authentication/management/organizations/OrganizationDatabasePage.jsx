import React, {useEffect, useState} from "react";
import {useParams} from "react-router-dom";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import OrganizationHeader from "./OrganizationHeader";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import {ORGANIZATION_MENU_ITEMS} from "./OrganizationMenuItems";
import ApiRoutes from "../../../../../util/ApiRoutes";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import OrganizationDatabaseTable from "./OrganizationDatabaseTable";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../../util/UsePageTitle";
import SystemService from "../../../../../services/SystemService";
import SubsystemDatabaseUsageTable from "../../../database/SubsystemDatabaseUsageTable";

const authenticationManagementService = new AuthenticationManagementService();
const systemService = new SystemService();

export default function OrganizationDatabasePage() {

  usePageTitle("Database of Organization");

  const { organizationId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [sizes, setSizes] = useState(null);
  const [revision, setRevision] = useState(new Date());

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
  }, [organizationId])

  useEffect(() => {
    setSizes(null);
    systemService.getDatabaseOrganizationSizes(setSizes, organizationId);
  }, [organizationId, revision])

  if (!organization) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <OrganizationHeader organization={organization}/>

      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={ORGANIZATION_MENU_ITEMS(organization.id)}
                          activeRoute={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DATABASE_PAGE(organizationId)}/>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-md-12">
          <h1>Database of Organization &quot;{organization.name}&quot;</h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Organization Database Usage per Subsystem" slim={true} />

              <SubsystemDatabaseUsageTable sizes={sizes ? sizes.subsystem_sizes : null} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Organization Database Usage" slim={true} />

              <OrganizationDatabaseTable organization={organization} sizes={sizes} onRefresh={() => setRevision(new Date())} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}