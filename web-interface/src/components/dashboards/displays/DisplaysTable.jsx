import React, {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Paginator from "../../misc/Paginator";
import ApiRoutes from "../../../util/ApiRoutes";
import DashboardsService from "../../../services/DashboardsService";
import DisplayStatus from "./DisplayStatus";
import DisplayVersion from "./DisplayVersion";

const dashboardsService = new DashboardsService();

export default function DisplaysTable({organizationId, tenantId, dashboardId = undefined, locationId = undefined, revision}) {

  const [displays, setDisplays] = useState(null);
  const [page, setPage] = useState(1);

  const perPage = 25;

  useEffect(() => {
    setDisplays(null);
    dashboardsService.findAllDisplays(organizationId, tenantId, dashboardId, locationId, perPage, (page-1)*perPage,
      setDisplays);
  }, [organizationId, tenantId, dashboardId, locationId, page, revision]);

  const emptyMessage = () => {
    if (dashboardId) {
      return "No display shows this dashboard.";
    }

    return locationId ? "No displays at this location." : "No displays found.";
  }

  if (!displays) {
    return <LoadingSpinner />
  }

  if (displays.displays.length === 0) {
    return (
      <div className="alert alert-info mb-0">{emptyMessage()}</div>
    )
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Name</th>
          {locationId ? null : <th>Location</th>}
          <th>Dashboards</th>
          <th>Status</th>
          <th>Loaded Version</th>
          <th>Last Seen</th>
        </tr>
        </thead>
        <tbody>
        {displays.displays.map((display) => {
          return (
            <tr key={display.uuid}>
              <td><Link to={ApiRoutes.DASHBOARDS.DISPLAYS.DETAILS(display.uuid)}>{display.name}</Link></td>
              {locationId ? null :
                <td>
                  {display.location_id ?
                    <Link to={ApiRoutes.LOCATIONS.DETAILS(display.location_id)}>{display.location_name}</Link>
                    : <span className="text-muted">n/a</span>}
                </td>}
              <td>
                {display.dashboards.length === 0 ? <span className="text-muted">None</span>
                  : numeral(display.dashboards.length).format("0,0")}
              </td>
              <td><DisplayStatus display={display} /></td>
              <td><DisplayVersion display={display} /></td>
              <td>
                {display.last_seen_at ?
                  <span title={moment(display.last_seen_at).format()}>{moment(display.last_seen_at).fromNow()}</span>
                  : <span className="text-muted">Never</span>}
              </td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={displays.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
