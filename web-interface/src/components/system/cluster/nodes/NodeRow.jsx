import {Link} from "react-router-dom";
import React from "react";

import numeral from "numeral";
import moment from "moment/moment";
import ApiRoutes from "../../../../util/ApiRoutes";

function NodeRow(props) {

  const node = props.node
  const showOfflineNodes = props.showOfflineNodes;

  if (node.active && !node.deleted) {
    return (
        <tr>
          <td>
            <Link to={ApiRoutes.SYSTEM.CLUSTER.NODES.DETAILS(node.uuid)}>
              {node.name}
            </Link>
            {' '}
            {node.is_ephemeral ? <i className="fa-regular fa-clock" title="Ephemeral Node" /> : null}
          </td>
          <td>{numeral(node.cpu_system_load).format("0.0")}%</td>
          <td>
            {numeral(node.memory_bytes_used).format("0.0b")} of {numeral(node.memory_bytes_total).format("0.0b")}{' '}
            ({numeral((node.memory_bytes_used * 100.0) / node.memory_bytes_total).format("0.0")}%)
          </td>
          <td>
            {numeral(node.heap_bytes_used).format("0.0b")} of {numeral(node.heap_bytes_total).format("0.0b")}{' '}
            ({numeral((node.heap_bytes_used * 100.0) / node.heap_bytes_total).format("0.0")}%)
          </td>
          <td>{node.clock_drift_ms < -5000 || node.clock_drift_ms > 5000
            ? <i className="fa-solid fa-warning text-danger" title="Clock drift detected"/>
            : <i className="fa-regular fa-circle-check" title="No clock drift detected" />}</td>
          <td>{node.version}</td>
        </tr>
    )
  } else {
    if (showOfflineNodes) {
      if (node.deleted) {
        return (
            <tr>
              <td><Link to={ApiRoutes.SYSTEM.CLUSTER.NODES.DETAILS(node.uuid)}>{node.name}</Link></td>
              <td colSpan={6} style={{textAlign: "center"}} title={moment(node.last_seen).format()}>
                <span><i className="fa-solid fa-triangle-exclamation text-danger" title="Node is offline."/></span>{' '}
                Node has been manually deleted and will expire automatically if not brought back online.
              </td>
            </tr>
        )
      } else {
        return (
            <tr>
              <td><Link to={ApiRoutes.SYSTEM.CLUSTER.NODES.DETAILS(node.uuid)}>{node.name}</Link></td>
              <td colSpan={6} style={{textAlign: "center"}} title={moment(node.last_seen).format()}>
                <span><i className="fa-solid fa-triangle-exclamation text-danger" title="Node is offline."/></span>{' '}
                Last seen {moment(node.last_seen).fromNow()} and will expire automatically if not brought back online.
              </td>
            </tr>
        )
      }
    } else {
      return null;
    }
  }

}

export default NodeRow;
