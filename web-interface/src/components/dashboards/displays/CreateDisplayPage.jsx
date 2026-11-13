import React, {useState} from "react";
import {Link} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import DashboardsService from "../../../services/DashboardsService";
import DisplayForm from "./DisplayForm";
import DisplayLink from "./DisplayLink";

const dashboardsService = new DashboardsService();

export default function CreateDisplayPage() {

  usePageTitle("Create Display");

  const [organizationId, tenantId] = useSelectedTenant();

  const [created, setCreated] = useState(null);

  const create = (payload, onError) => {
    dashboardsService.createDisplay(organizationId, tenantId, payload, (display) => {
      toast.success("Display created.");
      setCreated(display);
    }, onError);
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.DASHBOARDS.INDEX}>Dashboards</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.DASHBOARDS.DISPLAYS.INDEX}>Displays</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Create</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Create Display</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.DASHBOARDS.DISPLAYS.INDEX} className="btn btn-primary">Back to Displays</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              {created ?
                <React.Fragment>
                  <CardTitleWithControls title="Display Link"
                                         helpLink="https://go.nzyme.org/dashboards-displays-link" />

                  <DisplayLink token={created.token} />

                  <Link to={ApiRoutes.DASHBOARDS.DISPLAYS.DETAILS(created.uuid)} className="btn btn-primary">
                    Continue to Display
                  </Link>
                </React.Fragment>
                :
                <React.Fragment>
                  <CardTitleWithControls title="Display Settings"
                                         helpLink="https://go.nzyme.org/dashboards-displays-settings" />

                  <DisplayForm organizationId={organizationId}
                               tenantId={tenantId}
                               initialValues={{}}
                               submitText="Create Display"
                               onSubmit={create} />
                </React.Fragment>}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
