import React, {useContext} from "react";
import ApiRoutes from "../../../../util/ApiRoutes";
import OrganizationsTable from "./organizations/OrganizationsTable";
import GlobalSessions from "./sessions/GlobalSessions";
import SuperAdminTable from "./users/superadmins/SuperAdminTable";
import {Link, Navigate} from "react-router-dom";
import Routes from "../../../../util/ApiRoutes";
import {UserContext} from "../../../../App";
import usePageTitle from "../../../../util/UsePageTitle";

function AuthenticationPage() {

  usePageTitle("Authentication & Authorization");

  const user = useContext(UserContext);

  // Send an Org Admin directly to their org page because they won't be able to access anything here.
  if (user.is_orgadmin) {
    return <Navigate to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(user.organization_id)} />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-10">
            <h1>Authentication &amp; Authorization</h1>
          </div>

          <div className="col-2">
            <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.SETTINGS} className="btn btn-primary float-end">
              Settings
            </Link>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="row">
              <div className="col-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Organizations</h3>

                    <OrganizationsTable />

                    <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.CREATE}
                       className="btn btn-sm btn-secondary">
                      Create Organization
                    </Link>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Super Administrators</h3>

                    <SuperAdminTable />

                    <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.SUPERADMINS.CREATE}
                       className="btn btn-sm btn-secondary">
                      Create Super Administrator
                    </Link>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <h3>All Active Sessions</h3>

                <GlobalSessions />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default AuthenticationPage;