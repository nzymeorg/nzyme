import React from "react";
import ApiRoutes from "../../../../../util/ApiRoutes";
import moment from "moment";
import numeral from "numeral";

function TenantUsersTableRow(props) {

  const user = props.user;

  return (
      <tr>
        <td>
          <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.USERS.DETAILS(
              user.organization_id,
              user.tenant_id,
              user.id)}>
            {user.name}
          </a>
        </td>
        <td>{user.email}</td>
        <td>{user.mfa_disabled ? <span className="text-warning">Disabled</span>
            : <span className="text-success">Enabled</span> }</td>
        <td>{numeral(user.api_keys).format("0,0")}</td>
        <td>
          {user.tap_permissions && user.tap_permissions.length > 0 ? user.tap_permissions.length : "All"}
        </td>
        <td>
          {user.permissions ? user.permissions.length : "None"}
        </td>
        <td title={user.last_activity ? moment(user.last_activity).format() : "None"}>
          {user.last_activity ? moment(user.last_activity).fromNow() : "None"}
        </td>
      </tr>
  )

}

export default TenantUsersTableRow;
