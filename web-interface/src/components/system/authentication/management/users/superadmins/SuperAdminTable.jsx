import React, {useEffect, useState} from "react";
import LoadingSpinner from "../../../../../misc/LoadingSpinner";
import AuthenticationManagementService from "../../../../../../services/AuthenticationManagementService";
import SuperAdminTableRow from "./SuperAdminTableRow";
import Paginator from "../../../../../misc/Paginator";

const authenticationMgmtService = new AuthenticationManagementService();

function SuperAdminTable() {

  const [users, setUsers] = useState(null);

  const perPage = 20;
  const [page, setPage] = useState(1);

  useEffect(() => {
    authenticationMgmtService.findAllSuperAdmins(setUsers, perPage, (page-1)*perPage);
  }, [page]);

  if (!users) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Name</th>
            <th>Email</th>
            <th>API Keys</th>
            <th>MFA</th>
            <th>Last Activity</th>
          </tr>
          </thead>
          <tbody>
          {users.users.map(function (key, i) {
            return <SuperAdminTableRow key={"superadmins-" + i} user={users.users[i]} />
          })}
          </tbody>
        </table>

        <Paginator itemCount={users.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default SuperAdminTable;