import React, {useContext, useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import moment from "moment";
import {toast} from "react-toastify";
import ApiRoutes from "../../util/ApiRoutes";
import usePageTitle from "../../util/UsePageTitle";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../misc/LoadingSpinner";
import CardTitleWithControls from "../shared/CardTitleWithControls";
import {UserContext} from "../../App";
import {userHasPermission} from "../../util/Tools";
import DashboardsService from "../../services/DashboardsService";
import DisplaysTable from "./displays/DisplaysTable";
import {WINDOWS} from "./screen/widgets/WidgetCatalog";

const dashboardsService = new DashboardsService();

export default function DashboardDetailsPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "dashboards_manage");

  const [dashboard, setDashboard] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(dashboard ? `Dashboard: ${dashboard.name}` : "Dashboard Details");

  useEffect(() => {
    setDashboard(null);
    dashboardsService.findDashboard(uuid, organizationId, tenantId, setDashboard, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this dashboard? It is removed from all displays that show it.")) {
      return;
    }

    dashboardsService.deleteDashboard(uuid, organizationId, tenantId, () => {
      toast.success("Dashboard deleted.");
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.DASHBOARDS.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Dashboard not found. <Link to={ApiRoutes.DASHBOARDS.INDEX}>Back to Dashboards</Link>
      </div>
    )
  }

  if (!dashboard) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.DASHBOARDS.INDEX}>Dashboards</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{dashboard.name}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <h1>Dashboard &quot;{dashboard.name}&quot;</h1>
        </div>

        <div className="col-6 text-end">
          <Link to={ApiRoutes.DASHBOARDS.INDEX} className="btn btn-secondary me-1">Back</Link>
          {canManage ?
            <Link to={ApiRoutes.DASHBOARDS.EDIT(dashboard.uuid)} className="btn btn-secondary me-1">Edit</Link>
            : null}
          <Link to={ApiRoutes.DASHBOARDS.VIEW(dashboard.uuid)} className="btn btn-primary">Open</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className={canManage ? "col-8" : "col-12"}>
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Description</dt>
                <dd>
                  {dashboard.description ?
                    <span style={{whiteSpace: "pre-wrap"}}>{dashboard.description}</span>
                    : <span className="text-muted">No description.</span>}
                </dd>
                <dt>Taps</dt>
                <dd>
                  {dashboard.scope_type === "ALL" ? "All taps"
                    : (dashboard.scope_type === "LOCATIONS" ? "All taps at " : "")
                      + (dashboard.scope.length === 0 ? "none" : dashboard.scope.map((item) => item.name).join(", "))}
                </dd>
                <dt>Time Window</dt>
                <dd>{WINDOWS.find((w) => w.minutes === dashboard.window_minutes).title}</dd>
                <dt>Widgets</dt>
                <dd>{dashboard.widgets.length}</dd>
                <dt>Created</dt>
                <dd title={moment(dashboard.created_at).format()}>{moment(dashboard.created_at).fromNow()}</dd>
                <dt>Last Updated</dt>
                <dd className="mb-0" title={moment(dashboard.updated_at).format()}>
                  {moment(dashboard.updated_at).fromNow()}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        {canManage ?
          <div className="col-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Delete Dashboard" slim={true} />

                <p>The dashboard is removed from all displays that show it. The displays are kept.</p>

                <button className="btn btn-sm btn-danger" onClick={onDelete}>Delete Dashboard</button>
              </div>
            </div>
          </div> : null}
      </div>

      {canManage ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Displays" refreshAction={() => setRevision(new Date())} />

                <DisplaysTable organizationId={organizationId}
                               tenantId={tenantId}
                               dashboardId={dashboard.uuid}
                               revision={revision} />
              </div>
            </div>
          </div>
        </div> : null}
    </React.Fragment>
  )

}
