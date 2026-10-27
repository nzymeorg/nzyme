import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../../util/ApiRoutes";

function MatchingNodes(props) {

  const nodes = props.nodes;

  if (!nodes || nodes.length === 0) {
    return (
        <ul>
          <li>None</li>
        </ul>
    )
  }

  return (
      <ul className="mb-0">
        {nodes.sort((a, b) => a.node_name.localeCompare(b.node_name)).map(function (key, i) {
          return (
            <li key={"node" + i}>
              <Link to={ApiRoutes.SYSTEM.CLUSTER.NODES.DETAILS(nodes[i].node_id)}>
                {nodes[i].node_name}
              </Link>
            </li>
          )
        })}
      </ul>
  )

}

export default MatchingNodes;