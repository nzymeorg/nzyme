import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../util/ApiRoutes";

function ShortNodeId(props) {

  const id = props.id;

  return <Link to={ApiRoutes.SYSTEM.CLUSTER.NODES.DETAILS(id)}>{id.substr(0, 8)}</Link>;

}

export default ShortNodeId;