import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../util/ApiRoutes";

function UserDetailsAdminLink(props) {

  const userId = props.id;
  const email = props.email;
  const organizationId = props.organizationId;
  const tenantId = props.tenantId;

  if (!organizationId && !tenantId) {
    // Superadmin.
    return <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.SUPERADMINS.DETAILS(userId)}>
      {email}
    </Link>
  }

  if (organizationId && !tenantId) {
    // Org admin.
    return <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.ADMINS.DETAILS(organizationId, userId)}>
      {email}
    </Link>  }

  // Tenant User.
  return <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.USERS.DETAILS(organizationId, tenantId, userId)}>
    {email}
  </Link>

}

export default UserDetailsAdminLink;