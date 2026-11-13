import React, {useContext, useEffect, useState} from "react";
import {Link} from "react-router-dom";
import SectionMenuBar from "../shared/SectionMenuBar";
import CardTitleWithControls from "../shared/CardTitleWithControls";
import ApiRoutes from "../../util/ApiRoutes";
import usePageTitle from "../../util/UsePageTitle";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import {UserContext} from "../../App";
import {userHasPermission} from "../../util/Tools";
import DashboardsService from "../../services/DashboardsService";
import {DASHBOARDS_MENU_ITEMS} from "./DashboardsMenuItems";
import DashboardsTable from "./DashboardsTable";

const dashboardsService = new DashboardsService();

export default function DashboardsPage() {

  usePageTitle("Dashboards");

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "dashboards_manage");

  const [dashboards, setDashboards] = useState(null);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(new Date());

  const perPage = 25;

  useEffect(() => {
    setDashboards(null);
    dashboardsService.findAllDashboards(organizationId, tenantId, perPage, (page-1)*perPage, setDashboards);
  }, [organizationId, tenantId, page, revision]);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <SectionMenuBar items={DASHBOARDS_MENU_ITEMS} activeRoute={ApiRoutes.DASHBOARDS.INDEX} />
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Dashboards" refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Dashboards are pages made to run permanently on a screen. A dashboard is shown on a screen by
                sharing it on a{' '}
                {canManage ? <Link to={ApiRoutes.DASHBOARDS.DISPLAYS.INDEX}>display</Link> : "display"}.
              </p>

              {canManage ?
                <p>
                  <Link to={ApiRoutes.DASHBOARDS.CREATE} className="btn btn-sm btn-primary">Create Dashboard</Link>
                </p> : null}

              <DashboardsTable dashboards={dashboards} page={page} setPage={setPage} perPage={perPage} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
