import React, {useEffect, useState} from "react";
import LoadingSpinner from "../../../../../misc/LoadingSpinner";
import AuthenticationManagementService from "../../../../../../services/AuthenticationManagementService";
import Paginator from "../../../../../misc/Paginator";
import OrganizationAdminTableRow from "./OrganizationAdminTableRow";

const authenticationMgmtService = new AuthenticationManagementService();

function OrganizationAdminTable(props) {

  const organization = props.organization;

  const [users, setUsers] = useState(null);

  const perPage = 20;
  const [page, setPage] = useState(1);

  useEffect(() => {
    authenticationMgmtService.findAllOrganizationAdmins(organization.id, setUsers, perPage, (page-1)*perPage);
  }, [page]);

  if (!users) {
    return <LoadingSpinner />
  }

  if (users.users.length === 0) {
    return (
        <div className="alert alert-info mb-2">
          This organization does not have any administrators.
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
            <th>Last Activity</th>
          </tr>
          </thead>
          <tbody>
          {users.users.map(function (key, i) {
            return <OrganizationAdminTableRow key={"orgadmins-" + i} user={users.users[i]} organization={organization} />
          })}
          </tbody>
        </table>

        <Paginator itemCount={users.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default OrganizationAdminTable;