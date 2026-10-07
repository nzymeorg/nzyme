import React, {useEffect, useState} from "react";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import TenantUsersTableRow from "./TenantUsersTableRow";
import Paginator from "../../../../misc/Paginator";

const authenticationMgmtService = new AuthenticationManagementService();

function TenantUsersTable(props) {

  const organizationId = props.organizationId;
  const tenantId = props.tenantId;

  const [users, setUsers] = useState(null);

  const perPage = 20;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setUsers(null);
    authenticationMgmtService.findAllUsersOfTenant(organizationId, tenantId, setUsers, perPage, (page-1)*perPage);
  }, [page]);

  if (!users) {
    return <LoadingSpinner />
  }

  if (users.users.length === 0) {
    return (
        <div className="alert alert-info mb-2">
          This tenant does not have any users.
        </div>
    )
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Name</th>
            <th>Email</th>
            <th>MFA</th>
            <th>API Keys</th>
            <th>Tap Permissions</th>
            <th>Addtl. Functionality</th>
            <th>Last Activity</th>
          </tr>
          </thead>
          <tbody>
          {users.users.map(function (key, i) {
            return <TenantUsersTableRow key={"tenantusers-" + i} user={users.users[i]} />
          })}
          </tbody>
        </table>

        <Paginator itemCount={users.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default TenantUsersTable;