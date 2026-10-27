import React, {useContext, useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import Routes from "../../../../../util/ApiRoutes";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../../util/ApiRoutes";
import OrganizationSessions from "../sessions/OrganizationSessions";
import {UserContext} from "../../../../../App";
import WithExactRole from "../../../../misc/WithExactRole";
import SectionMenuBar from "../../../../shared/SectionMenuBar";
import {ORGANIZATION_MENU_ITEMS} from "./OrganizationMenuItems";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationManagementService = new AuthenticationManagementService();

function OrganizationDetailsPage() {

  usePageTitle("Organization Details");

  const user = useContext(UserContext);

  const { organizationId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [redirect, setRedirect] = useState(false);

  const deleteOrganization = function() {
    if (!confirm("Really delete organization?")) {
      return;
    }

    authenticationManagementService.deleteOrganization(organizationId, function() {
      setRedirect(true);
      toast.success('Organization deleted.');
    });
  }

  useEffect(() => {
    authenticationManagementService.findOrganization(organizationId, setOrganization);
  }, [organizationId])

  if (redirect) {
    return <Navigate to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX} />
  }

  if (!organization) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
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
                  <Link className="btn btn-secondary" to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Back</Link>
                  : null}{' '}

              {user.is_superadmin ?
                  <Link className="btn btn-primary"
                     to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.EDIT(organization.id)}>
                    Edit Organization
                  </Link> : null}
            </span>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={ORGANIZATION_MENU_ITEMS(organization.id)}
                            activeRoute={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}/>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <h1>Organization &quot;{organization.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <CardTitleWithControls title="Description" slim={true} />

                    <p className="mb-0">
                      {organization.description}
                    </p>
                  </div>
                </div>
              </div>
            </div>

          </div>

          <div className="col-md-6">
            {user.is_superadmin ?
                <div className="row">
                  <div className="col-md-12">
                    <div className="card">
                      <div className="card-body">
                        <CardTitleWithControls title="Delete Organization" slim={true} />

                        <p>
                          You can only delete an organization if it has no tenants and if it is not the last remaining
                          one.
                        </p>

                        <button className="btn btn-sm btn-danger" disabled={!organization.is_deletable}
                                onClick={deleteOrganization}>
                          Delete Organization
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
                : null}
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="All Active Sessions of Organization" slim={true} />

                <OrganizationSessions organizationId={organization.id}/>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default OrganizationDetailsPage;