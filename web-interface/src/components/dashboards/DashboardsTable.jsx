import React from "react";
import {Link} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import LoadingSpinner from "../misc/LoadingSpinner";
import Paginator from "../misc/Paginator";
import ApiRoutes from "../../util/ApiRoutes";

export default function DashboardsTable({dashboards, page, setPage, perPage}) {

  if (!dashboards) {
    return <LoadingSpinner />
  }

  if (dashboards.dashboards.length === 0) {
    return <div className="alert alert-info mb-0">No dashboards found.</div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Name</th>
          <th>Description</th>
          <th>Displays</th>
          <th>Updated</th>
          <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
        {dashboards.dashboards.map((dashboard) => {
          return (
            <tr key={dashboard.uuid}>
              <td><Link to={ApiRoutes.DASHBOARDS.DETAILS(dashboard.uuid)}>{dashboard.name}</Link></td>
              <td>
                {dashboard.description ? dashboard.description : <span className="text-muted">No description.</span>}
              </td>
              <td>{numeral(dashboard.display_count).format("0,0")}</td>
              <td title={moment(dashboard.updated_at).format()}>{moment(dashboard.updated_at).fromNow()}</td>
              <td><Link to={ApiRoutes.DASHBOARDS.VIEW(dashboard.uuid)}>Open</Link></td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={dashboards.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
