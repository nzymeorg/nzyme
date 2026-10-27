import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../util/ApiRoutes";
import AuthenticationSettings from "./AuthenticationSettings";
import usePageTitle from "../../../../util/UsePageTitle";

function AuthenticationSettingsPage() {

  usePageTitle("Authentication Settings");

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-8">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Authentication</Link></li>
                <li className="breadcrumb-item active" aria-current="page">Settings</li>
              </ol>
            </nav>
          </div>

          <div className="col-4">
            <span className="float-end">
              <Link className="btn btn-primary" to={ApiRoutes.SYSTEM.AUTHENTICATION.MANAGEMENT.INDEX}>Back</Link>
            </span>
          </div>

          <div className="col-12">
            <h1>Authentication Settings</h1>
          </div>
        </div>

        <div className="row mt-2">
          <div className="col-xl-12 col-xxl-6">
            <div className="card">
              <div className="card-body">
                <h3>Configuration</h3>

                <div className="alert alert-warning">
                  <strong>These settings apply to super administrators and organization administrators.</strong> Each
                  tenant has individual authentication settings.
                </div>

                <AuthenticationSettings />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default AuthenticationSettingsPage;