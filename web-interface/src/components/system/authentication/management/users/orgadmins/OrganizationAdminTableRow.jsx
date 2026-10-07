import React from "react";
import moment from "moment";
import ApiRoutes from "../../../../../../util/ApiRoutes";
import numeral from "numeral";

function OrganizationAdminTableRow(props) {

  const organization = props.organization;
  const user = props.user;

  return (
      <tr>
        <td>
          <a href={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.ADMINS.DETAILS(organization.id, user.id)}>
            {user.name}
          </a>
        </td>
        <td>{user.email}</td>
        <td>{user.mfa_disabled ? <span className="text-warning">Disabled</span>
            : <span className="text-success">Enabled</span> }</td>
        <td>{numeral(user.api_keys).format("0,0")}</td>
        <td title={user.last_activity ? moment(user.last_activity).format() : "None"}>
          {user.last_activity ? moment(user.last_activity).fromNow() : "None"}
        </td>
      </tr>
  )

}

export default OrganizationAdminTableRow;
