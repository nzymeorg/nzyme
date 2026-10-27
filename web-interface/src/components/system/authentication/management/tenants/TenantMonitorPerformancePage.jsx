import React, {useEffect, useState} from 'react';
import {Link, useParams} from "react-router-dom";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import Routes from "../../../../../util/ApiRoutes";
import ApiRoutes from "../../../../../util/ApiRoutes";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import {TENANT_MENU_ITEMS} from "./TenantMenuItems";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import TenantMonitorPerformanceTable from "./TenantMonitorPerformanceTable";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

export default function TenantMonitorPerformancePage() {

  usePageTitle("Monitor Performance of Tenant");

  const { organizationId } = useParams();
  const { tenantId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [tenant, setTenant] = useState(null);

  const [monitors, setMonitors] = useState(null);
  const [page, setPage] = useState(1);
  const [orderColumn, setOrderColumn] = useState("name");
  const [orderDirection, setOrderDirection] = useState("ASC");
  const [revision, setRevision] = useState(new Date());

  const applyOrderColumn = (column) => {
    setPage(1);
    setOrderColumn(column);
  }

  const applyOrderDirection = (direction) => {
    setPage(1);
    setOrderDirection(direction);
  }

  const perPage = 50;

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
    authenticationManagementService.findTenantOfOrganization(organizationId, tenantId, setTenant);
  }, [organizationId, tenantId])

  useEffect(() => {
    setMonitors(null);
    authenticationManagementService.getMonitorPerformanceOfTenantOfOrganization(
      organizationId, tenantId, orderColumn, orderDirection, perPage, (page-1)*perPage, setMonitors
    )
  }, [organizationId, tenantId, orderColumn, orderDirection, page, revision])

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
                            activeRoute={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.MONITOR_PERFORMANCE_PAGE(organization.id, tenant.id)}/>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <h1>Monitor Performance of Tenant &quot;{tenant.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Monitors"
                                       slim={true}
                                       refreshAction={() => setRevision(new Date())} />

                <p className="text-muted">
                  All monitors of this tenant, across all subsystems, with the time each execution takes. Every
                  node times the executions it performs. The timings combine the latest report of all nodes that
                  reported within the last two minutes, and the execution count is cumulative since those nodes
                  started. A monitor shows no timings until a running node has executed it.
                </p>

                <TenantMonitorPerformanceTable monitors={monitors}
                                               page={page}
                                               setPage={setPage}
                                               perPage={perPage}
                                               orderColumn={orderColumn}
                                               setOrderColumn={applyOrderColumn}
                                               orderDirection={orderDirection}
                                               setOrderDirection={applyOrderDirection} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}
