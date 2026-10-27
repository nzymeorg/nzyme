import {Link} from "react-router-dom";
import React, {useContext} from "react";
import Routes from "../../../../../util/ApiRoutes";
import {UserContext} from "../../../../../App";

export default function OrganizationHeader(props) {

  const organization = props.organization;

  const user = useContext(UserContext);

  return (
      <div className="row">
        <div className="col-md-9">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item">
                <Link to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</Link>
              </li>
              <li className="breadcrumb-item">Organizations</li>
              <li className="breadcrumb-item active" aria-current="page">{organization.name}</li>
            </ol>
          </nav>
        </div>
        <div className="col-md-3">
            <span className="float-end">
              {user.is_superadmin ?
                  <Link className="btn btn-primary"
                     to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EDIT(organization.id)}>
                    Edit Organization
                  </Link> : null}
            </span>
        </div>
      </div>
  )

}