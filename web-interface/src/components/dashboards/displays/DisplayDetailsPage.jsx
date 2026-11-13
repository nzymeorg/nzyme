import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import moment from "moment";
import {toast} from "react-toastify";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../misc/LoadingSpinner";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import DashboardsService from "../../../services/DashboardsService";
import DisplayStatus from "./DisplayStatus";
import DisplayVersion from "./DisplayVersion";
import DisplayLink from "./DisplayLink";

const dashboardsService = new DashboardsService();

const THEMES = {
  DARK: "Dark",
  LIGHT: "Light",
  SCHEDULED: "Light during the day, dark at night"
};

export default function DisplayDetailsPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();

  const [display, setDisplay] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [newToken, setNewToken] = useState(null);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(display ? `Display: ${display.name}` : "Display Details");

  useEffect(() => {
    setDisplay(null);
    dashboardsService.findDisplay(uuid, organizationId, tenantId, setDisplay, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  const onReplaceLink = (e) => {
    e.preventDefault();

    if (!confirm("Really replace the link of this display? The screen stops working until the new link is opened on it.")) {
      return;
    }

    dashboardsService.replaceDisplayToken(uuid, organizationId, tenantId, (replaced) => {
      toast.success("Display link replaced.");
      setNewToken(replaced.token);
    });
  }

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this display? Its link stops working immediately.")) {
      return;
    }

    dashboardsService.deleteDisplay(uuid, organizationId, tenantId, () => {
      toast.success("Display deleted.");
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.DASHBOARDS.DISPLAYS.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Display not found. <Link to={ApiRoutes.DASHBOARDS.DISPLAYS.INDEX}>Back to Displays</Link>
      </div>
    )
  }

  if (!display) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.DASHBOARDS.INDEX}>Dashboards</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.DASHBOARDS.DISPLAYS.INDEX}>Displays</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{display.name}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <h1>Display &quot;{display.name}&quot;</h1>
        </div>

        <div className="col-6 text-end">
          <Link to={ApiRoutes.DASHBOARDS.DISPLAYS.INDEX} className="btn btn-secondary me-1">Back</Link>
          <Link to={ApiRoutes.DASHBOARDS.DISPLAYS.EDIT(display.uuid)} className="btn btn-primary">Edit</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Location</dt>
                <dd>
                  {display.location_id ?
                    <Link to={ApiRoutes.LOCATIONS.DETAILS(display.location_id)}>{display.location_name}</Link>
                    : <span className="text-muted">n/a</span>}
                </dd>
                <dt>Rotation Interval</dt>
                <dd>{display.rotation_seconds} seconds</dd>
                <dt>Refresh Interval</dt>
                <dd className="mb-0">{display.refresh_seconds} seconds</dd>
              </dl>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Presentation" />

                  <dl className="mb-0">
                    <dt>Theme</dt>
                    <dd>
                      {THEMES[display.theme]}
                      {display.theme === "SCHEDULED" ?
                        <span> (light from {display.light_from} until {display.light_until})</span> : null}
                    </dd>
                    <dt>Time Zone</dt>
                    <dd>{display.time_zone}</dd>
                    <dt>Clock Format</dt>
                    <dd>{display.clock_format === "H12" ? "12-hour" : "24-hour"}</dd>
                    <dt>Scale</dt>
                    <dd>{display.scale_percent}%</dd>
                    <dt>Mouse Pointer</dt>
                    <dd className="mb-0">{display.hide_cursor ? "Hidden" : "Shown"}</dd>
                  </dl>
                </div>
              </div>
            </div>

            <div className="col-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Access" />

                  <dl className="mb-0">
                    <dt>Addresses and Names</dt>
                    <dd>{display.redacted ? "Hidden" : "Shown"}</dd>
                    <dt>Allowed Networks</dt>
                    <dd>
                      {display.allowed_networks.length === 0 ? "Every address"
                        : <span className="machine-data">{display.allowed_networks.join(", ")}</span>}
                    </dd>
                    <dt>Expires</dt>
                    <dd className="mb-0">
                      {display.expires_at ?
                        <span title={moment(display.expires_at).format()}>
                          {moment(display.expires_at).format("YYYY-MM-DD HH:mm")} ({moment(display.expires_at).fromNow()})
                        </span>
                        : "Never"}
                    </dd>
                  </dl>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Status" refreshAction={() => setRevision(new Date())} />

              <dl className="mb-0">
                <dt>Status</dt>
                <dd><DisplayStatus display={display} /></dd>
                <dt>Last Seen</dt>
                <dd>
                  {display.last_seen_at ?
                    <span title={moment(display.last_seen_at).format()}>{moment(display.last_seen_at).fromNow()}</span>
                    : <span className="text-muted">Never</span>}
                </dd>
                <dt>Last Address</dt>
                <dd>
                  {display.last_remote_address ?
                    <span className="machine-data">{display.last_remote_address}</span>
                    : <span className="text-muted">n/a</span>}
                </dd>
                <dt>Loaded Version</dt>
                <dd className="mb-0"><DisplayVersion display={display} /></dd>
              </dl>
            </div>
          </div>

          <div className="card mt-3">
            <div className="card-body">
              <CardTitleWithControls title="Display Link" slim={true} />

              {newToken ?
                <DisplayLink token={newToken} />
                :
                <React.Fragment>
                  <p>
                    The link of a display is only shown when it is created. Replace it if it was lost or if it
                    should stop working on the current screen.
                  </p>

                  <button className="btn btn-sm btn-warning" onClick={onReplaceLink}>Replace Link</button>
                </React.Fragment>}
            </div>
          </div>

          <div className="card mt-3">
            <div className="card-body">
              <CardTitleWithControls title="Delete Display" slim={true} />

              <p>The link of the display stops working immediately. Its dashboards are kept.</p>

              <button className="btn btn-sm btn-danger" onClick={onDelete}>Delete Display</button>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Dashboards" />

              {display.dashboards.length === 0 ?
                <div className="alert alert-info mb-0">No dashboard is assigned to this display.</div>
                :
                <table className="table table-sm table-hover table-striped mb-0">
                  <thead>
                  <tr>
                    <th>Order</th>
                    <th>Name</th>
                    <th>&nbsp;</th>
                  </tr>
                  </thead>
                  <tbody>
                  {display.dashboards.map((dashboard, i) => {
                    return (
                      <tr key={dashboard.uuid}>
                        <td>{i + 1}</td>
                        <td><Link to={ApiRoutes.DASHBOARDS.DETAILS(dashboard.uuid)}>{dashboard.name}</Link></td>
                        <td><Link to={ApiRoutes.DASHBOARDS.VIEW(dashboard.uuid)}>Open</Link></td>
                      </tr>
                    )
                  })}
                  </tbody>
                </table>}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
