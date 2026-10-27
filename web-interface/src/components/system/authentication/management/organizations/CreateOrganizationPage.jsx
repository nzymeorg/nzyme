import React, {useState} from "react";
import Routes from "../../../../../util/ApiRoutes";
import OrganizationForm from "./OrganizationForm";
import AuthenticationManagementService from "../../../../../services/AuthenticationManagementService";
import {Link, Navigate} from "react-router-dom";
import {toast} from "react-toastify";
import usePageTitle from "../../../../../util/UsePageTitle";

const authenticationMgmtService = new AuthenticationManagementService();

function CreateOrganizationPage() {

  usePageTitle("Create Organization");

  const [redirect, setRedirect] = useState(false);

  const onFormSubmitted = function (name, description) {
    authenticationMgmtService.createOrganization(name, description, function() {
      toast.success('Organization created.');
      setRedirect(true);
    })
  }

  if (redirect) {
    return <Navigate to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX} />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">
                  <Link to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Create Organization</li>
              </ol>
            </nav>
          </div>

          <div className="col-2">
            <Link className="btn btn-secondary float-end" to={Routes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Back</Link>
          </div>

          <div className="col-12">
            <h1>Create Organization</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <OrganizationForm onClick={onFormSubmitted} submitText="Create Organization" />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default CreateOrganizationPage;