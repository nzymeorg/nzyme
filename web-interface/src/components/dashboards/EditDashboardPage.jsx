import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../util/ApiRoutes";
import usePageTitle from "../../util/UsePageTitle";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../misc/LoadingSpinner";
import CardTitleWithControls from "../shared/CardTitleWithControls";
import DashboardsService from "../../services/DashboardsService";
import DashboardForm from "./DashboardForm";

const dashboardsService = new DashboardsService();

export default function EditDashboardPage() {

  usePageTitle("Edit Dashboard");

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();

  const [dashboard, setDashboard] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    setDashboard(null);
    dashboardsService.findDashboard(uuid, organizationId, tenantId, setDashboard, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  const save = (payload, onError) => {
    dashboardsService.updateDashboard(uuid, organizationId, tenantId, payload, () => {
      toast.success("Dashboard updated.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.DASHBOARDS.DETAILS(uuid)} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Dashboard not found. <Link to={ApiRoutes.DASHBOARDS.INDEX}>Back to Dashboards</Link>
      </div>
    )
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.DASHBOARDS.INDEX}>Dashboards</Link></li>
              {dashboard ?
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.DASHBOARDS.DETAILS(dashboard.uuid)}>{dashboard.name}</Link>
                </li> : null}
              <li className="breadcrumb-item active" aria-current="page">Edit</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Edit Dashboard {dashboard ? <span>&quot;{dashboard.name}&quot;</span> : null}</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.DASHBOARDS.DETAILS(uuid)} className="btn btn-primary">Back</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-10">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Dashboard Settings" helpLink="https://go.nzyme.org/dashboards-create" />

              {dashboard ?
                <DashboardForm initialValues={dashboard}
                               submitText="Save Dashboard"
                               onSubmit={save} />
                : <LoadingSpinner />}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
