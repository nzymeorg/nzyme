import React, {useState} from "react";

import {toast} from "react-toastify";
import AuthenticationManagementService from "../../../../../../services/AuthenticationManagementService";
import ApiRoutes from "../../../../../../util/ApiRoutes";
import {Link, Navigate} from "react-router-dom";
import CreateUserForm from "../shared/CreateUserForm";
import EditSuperAdminPage from "./EditSuperAdminPage";
import usePageTitle from "../../../../../../util/UsePageTitle";

const authenticationMgmtService = new AuthenticationManagementService();

function CreateTenantUserPage() {

  usePageTitle("Create Super Administrator");

  const [errorMessage, setErrorMessage] = useState(null);
  const [redirect, setRedirect] = useState(false);

  const onFormSubmitted = function (email, password, name, disableMfa, callback) {
    authenticationMgmtService.createSuperAdministrator(email, password, name, disableMfa, function() {
      // Success.
      toast.success('Super administrator created.');
      setRedirect(true);
      callback();
    }, function (error) {
      // Error.
      setErrorMessage(error.response.data.message);
      callback();
    })
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX} />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-10">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">
                  <Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication &amp; Authorization</Link>
                </li>
                <li className="breadcrumb-item">Super Administrators</li>
                <li className="breadcrumb-item active" aria-current="page">Create Super Administrator</li>
              </ol>
            </nav>
          </div>

          <div className="col-2">
            <Link className="btn btn-secondary float-end"
               to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>
              Back
            </Link>
          </div>

          <div className="col-12">
            <h1>Create Super Administrator</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <CreateUserForm onClick={onFormSubmitted} errorMessage={errorMessage} submitText="Create Super Administrator" />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default CreateTenantUserPage;