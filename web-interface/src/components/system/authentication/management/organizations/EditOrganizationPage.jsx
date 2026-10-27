import React, {useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import Routes from "../../../../../util/ApiRoutes";
import OrganizationForm from "./OrganizationForm";
import {toast} from "react-toastify";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationMgmtService = new AuthenticationManagementService();

function EditOrganizationPage() {

  usePageTitle("Edit Organization");

  const { organizationId } = useParams();

  const [organization, setOrganization] = useState(null);
  const [redirect, setRedirect] = useState(false);

  const onFormSubmitted = function (name, description) {
    authenticationMgmtService.editOrganization(organization.id, name, description, function() {
      setRedirect(true);
      toast.success('Organization details updated.');
    })
  }

  useEffect(() => {
    authenticationMgmtService.findOrganization(organizationId, setOrganization);
  }, [organizationId])

  if (!organization) {
    return <LoadingSpinner />
  }

  if (redirect) {
    return <Navigate to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)} />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-9">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">
                  <Link to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</Link>
                </li>
                <li className="breadcrumb-item">Organizations</li>
                <li className="breadcrumb-item">
                  <Link to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}>
                    {organization.name}
                  </Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Edit</li>
              </ol>
            </nav>
          </div>

          <div className="col-3">
            <span className="float-end">
              <Link className="btn btn-secondary"
                 to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.ORGANIZATIONS.DETAILS(organization.id)}>Back</Link>{' '}
            </span>
          </div>

          <div className="col-12">
            <h1>Organization &quot;{organization.name}&quot;</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Edit Organization Details</h3>

                <OrganizationForm onClick={onFormSubmitted}
                                  name={organization.name}
                                  description={organization.description}
                                  submitText="Edit Organization"/>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default EditOrganizationPage;