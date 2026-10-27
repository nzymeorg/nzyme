import {Link} from "react-router-dom";
import React, {useEffect, useState} from "react";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../../util/ApiRoutes";
import Paginator from "../../../../misc/Paginator";

const authenticationMgmtService = new AuthenticationManagementService();

function OrganizationsTable() {

  const [organizations, setOrganizations] = useState(null);

  const perPage = 20;
  const [page, setPage] = useState(1);

  useEffect(() => {
    setOrganizations(null);
    authenticationMgmtService.findAllOrganizations(setOrganizations, perPage, (page-1)*perPage);
  }, [page]);

  if (!organizations) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <table className="table table-sm table-hover table-striped">
          <thead>
          <tr>
            <th>Name</th>
            <th>Tenants</th>
            <th>Users</th>
            <th>Taps</th>
          </tr>
          </thead>
          <tbody>
          {organizations.organizations.map(function (key, i) {
            return (
                <tr key={"org-" + i}>
                  <td>
                    <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organizations.organizations[i].id)}>
                      {organizations.organizations[i].name}
                    </Link>
                  </td>
                  <td>{organizations.organizations[i].tenants_count}</td>
                  <td>{organizations.organizations[i].users_count}</td>
                  <td>{organizations.organizations[i].taps_count}</td>
                </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={organizations.count} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
  )

}

export default OrganizationsTable;