import React, {useEffect, useState} from "react";
import moment from "moment";
import {Link, Navigate, useParams} from "react-router-dom";
import {toast} from "react-toastify";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../misc/LoadingSpinner";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import DashboardsService from "../../../services/DashboardsService";
import DisplayForm from "./DisplayForm";

const dashboardsService = new DashboardsService();

export default function EditDisplayPage() {

  usePageTitle("Edit Display");

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();

  const [display, setDisplay] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    setDisplay(null);
    dashboardsService.findDisplay(uuid, organizationId, tenantId, setDisplay, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  const save = (payload, onError) => {
    dashboardsService.updateDisplay(uuid, organizationId, tenantId, payload, () => {
      toast.success("Display updated.");
      setRedirect(true);
    }, onError);
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.DASHBOARDS.DISPLAYS.DETAILS(uuid)} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Display not found. <Link to={ApiRoutes.DASHBOARDS.DISPLAYS.INDEX}>Back to Displays</Link>
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
              <li className="breadcrumb-item"><Link to={ApiRoutes.DASHBOARDS.DISPLAYS.INDEX}>Displays</Link></li>
              {display ?
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.DASHBOARDS.DISPLAYS.DETAILS(display.uuid)}>{display.name}</Link>
                </li> : null}
              <li className="breadcrumb-item active" aria-current="page">Edit</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Edit Display {display ? <span>&quot;{display.name}&quot;</span> : null}</h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.DASHBOARDS.DISPLAYS.DETAILS(uuid)} className="btn btn-primary">Back</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-12 col-xxl-8">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Display Settings"
                                     helpLink="https://go.nzyme.org/dashboards-displays-settings" />

              {display ?
                <DisplayForm organizationId={organizationId}
                             tenantId={tenantId}
                             initialValues={{
                               name: display.name,
                               location_id: display.location_id ? display.location_id : "",
                               dashboards: display.dashboards,
                               theme: display.theme,
                               ...(display.theme === "SCHEDULED" ?
                                 {light_from: display.light_from, light_until: display.light_until} : {}),
                               time_zone: display.time_zone,
                               clock_format: display.clock_format,
                               scale_percent: String(display.scale_percent),
                               rotation_seconds: String(display.rotation_seconds),
                               refresh_seconds: String(display.refresh_seconds),
                               allowed_networks: display.allowed_networks.join("\n"),
                               redacted: display.redacted ? "true" : "false",
                               hide_cursor: display.hide_cursor ? "true" : "false",
                               expires_at: display.expires_at ?
                                 moment(display.expires_at).format("YYYY-MM-DDTHH:mm") : ""
                             }}
                             submitText="Save Display"
                             onSubmit={save} />
                : <LoadingSpinner />}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
