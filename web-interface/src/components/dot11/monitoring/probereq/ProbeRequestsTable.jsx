import {Link} from "react-router-dom";
import React from "react";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import {truncate} from "../../../../util/Tools";
import ApiRoutes from "../../../../util/ApiRoutes";

export default function ProbeRequestsTable(props) {

  const probeRequests = props.probeRequests;
  const page = props.page;
  const setPage = props.setPage;
  const perPage = props.perPage;
  const onDelete = props.onDelete;

  if (!probeRequests) {
    return <LoadingSpinner />
  }

  if (probeRequests.ssids.length === 0) {
    return <div className="alert alert-info mt-3">No monitored probe requests defined yet.</div>
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>SSID</th>
            <th>Notes</th>
            <th>&nbsp;</th>
            <th>&nbsp;</th>
          </tr>
          </thead>
          <tbody>
          {probeRequests.ssids.map((ssid, i) => {
            return (
                <tr key={i}>
                  <td>{ssid.ssid}</td>
                  <td>{ssid.notes ? <span title={ssid.notes} className="cursor-help">{truncate(ssid.notes, 125, true)}</span>
                      : <span className="text-muted">None</span>}</td>
                  <td>
                    <Link to={ApiRoutes.DOT11.MONITORING.PROBE_REQUESTS.EDIT(ssid.id)}>
                      Edit
                    </Link>
                  </td>
                  <td><a href="#" onClick={(e) => onDelete(e, ssid.id)}>Delete</a></td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator page={page} setPage={setPage} perPage={perPage} itemCount={probeRequests.total} />
      </React.Fragment>
  )

}