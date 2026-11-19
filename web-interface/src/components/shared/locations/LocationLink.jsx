import React from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../util/ApiRoutes";

export default function LocationLink({id, name, plain}) {

  if (!id || !name) {
    return <span className="text-muted">No Location</span>
  }

  if (plain) {
    return <span>{name}</span>
  }

  return <Link to={ApiRoutes.LOCATIONS.DETAILS(id)}>{name}</Link>

}
