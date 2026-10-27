import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../../util/ApiRoutes";
import Paginator from "../../../../misc/Paginator";

const authenticationMgmtService = new AuthenticationManagementService();

function TenantsTable(props) {

  const organizationId = props.organizationId;

  const [tenants, setTenants] = useState(null);

  const perPage = 20;
  const [page, setPage] = useState(1);

  useEffect(() => {
    authenticationMgmtService.findAllTenantsOfOrganization(organizationId, setTenants, perPage, (page-1)*perPage);
  }, [page]);

  if (!tenants) {
    return <LoadingSpinner />
  }

  if (tenants.tenants.length === 0) {
    return (
        <div className="alert alert-info mb-2">
          This organization does not have any tenants.
        </div>
    )
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Name</th>
            <th>Users</th>
            <th>Taps</th>
          </tr>
          </thead>
          <tbody>
          {tenants.tenants.map(function (tenant, i) {
            return (
                <tr key={"tenant-" + i}>
                  <td>
                    <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.TENANTS.DETAILS(
                        tenant.organization_id,
                        tenant.id)}>
                      {tenant.name}
                    </Link>
                  </td>
                  <td>{tenant.users_count}</td>
                  <td>{tenant.taps_count}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={tenants.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default TenantsTable;