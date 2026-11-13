import React, {useState} from "react";
import {Link, Navigate} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../util/ApiRoutes";
import CardTitleWithControls from "../shared/CardTitleWithControls";
import usePageTitle from "../../util/UsePageTitle";
import useSelectedTenant from "../system/tenantselector/useSelectedTenant";
import DashboardsService from "../../services/DashboardsService";
import DashboardForm from "./DashboardForm";

const dashboardsService = new DashboardsService();

export default function CreateDashboardPage() {

  usePageTitle("Create Dashboard");

  const [organizationId, tenantId] = useSelectedTenant();

  const [createdUuid, setCreatedUuid] = useState(null);

  const create = (payload, onError) => {
    dashboardsService.createDashboard(organizationId, tenantId, payload, (dashboard) => {
      toast.success("Dashboard created.");
      setCreatedUuid(dashboard.uuid);
    }, onError);
  }

  if (createdUuid) {
    return <Navigate to={ApiRoutes.DASHBOARDS.DETAILS(createdUuid)} />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.DASHBOARDS.INDEX}>Dashboards</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Create</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Create Dashboard</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.DASHBOARDS.INDEX} className="btn btn-primary">Back to Dashboards</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-10">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Dashboard Settings" helpLink="https://go.nzyme.org/dashboards-create" />

              <DashboardForm submitText="Create Dashboard" onSubmit={create} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
