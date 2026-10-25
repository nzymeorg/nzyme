import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../misc/LoadingSpinner";
import ApiRoutes from "../../../util/ApiRoutes";

export default function BeaconDestinationConnectionsTable({connections}) {

  if (!connections) {
    return <LoadingSpinner />
  }

  if (!connections.destination_address) {
    return <div className="alert alert-info mb-0">
      This beacon has no known destination address. Connections cannot be looked up.
    </div>
  }

  const sessionsLink = (l4Type, port) => {
    const filters = {
      destination_address: [{field: "destination_address", operator: "equals", value: connections.destination_address}]
    }

    if (l4Type) {
      filters.l4_type = [{field: "l4_type", operator: "equals", value: l4Type}];
    }

    if (port !== undefined) {
      filters.destination_port = [{field: "destination_port", operator: "equals_numeric", value: String(port)}];
    }

    return ApiRoutes.ETHERNET.L4.SESSIONS + "?filters=" + encodeURIComponent(JSON.stringify(filters));
  }

  if (connections.connections.length === 0) {
    return <div className="alert alert-info mb-0">
      No connections to <span className="machine-data">{connections.destination_address}</span> recorded in the
      selected time range.
    </div>
  }

  return (
    <React.Fragment>
      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          <th>Traffic</th>
          <th>Type</th>
          <th>Port</th>
          <th>Sessions</th>
          <th>Devices</th>
          <th>Bytes RX</th>
          <th>Bytes TX</th>
          <th>First Seen</th>
          <th>Last Seen</th>
          <th>&nbsp;</th>
        </tr>
        </thead>
        <tbody>
        {connections.connections.map((c, i) => {
          return (
            <tr key={i} className={c.is_beacon_port ? "table-active" : ""}>
              <td>{c.is_beacon_port ? <strong>This beacon</strong> : <span className="text-muted">Other</span>}</td>
              <td>{c.l4_type}</td>
              <td><span className="machine-data">{c.destination_port}</span></td>
              <td>{numeral(c.session_count).format("0,0")}</td>
              <td>{numeral(c.source_count).format("0,0")}</td>
              <td>{numeral(c.bytes_rx).format("0.0b")}</td>
              <td>{numeral(c.bytes_tx).format("0.0b")}</td>
              <td title={moment(c.first_seen).format()}>{moment(c.first_seen).fromNow()}</td>
              <td title={moment(c.last_seen).format()}>{moment(c.last_seen).fromNow()}</td>
              <td><Link to={sessionsLink(c.l4_type, c.destination_port)}>Sessions</Link></td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Link to={sessionsLink()} className="btn btn-sm btn-outline-secondary">
        All sessions to {connections.destination_address}
      </Link>
    </React.Fragment>
  )

}
