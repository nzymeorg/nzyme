  import React, {useState} from "react";
import AssetStylesheet from "../misc/AssetStylesheet";
import CreateUserForm from "../system/authentication/management/users/shared/CreateUserForm";
import {toast} from "react-toastify";
import AuthenticationManagementService from "../../services/AuthenticationManagementService";
  import usePageTitle from "../../util/UsePageTitle";
  import TermsOfUseModal from "./TermsOfUseModal";

const authenticationMgmtService = new AuthenticationManagementService();

function SetupWizardPage(props) {

  usePageTitle("Setup");

  const onActionCompleted = props.onActionCompleted;
  const [errorMessage, setErrorMessage] = useState(null);

  const onCreateUserClick = function (email, password, name, disableMfa, callback) {
    authenticationMgmtService.createInitialUser(email, password, name, disableMfa, function() {
      // Success.
      toast.success('First Super Administrator created. Please log in.');
      callback();
      onActionCompleted();
    }, function (error) {
      // Error.
      setErrorMessage(error.response.data.message);
      callback();
    })
  }

  return (
      <React.Fragment>
        <AssetStylesheet filename="onebox.css" />

        <section className="vh-100 start setupwizard">
          <div className="container py-5 vh-100 mb-5">
            <div className="row d-flex justify-content-center align-items-center h-100">
              <div className="col col-xl-10">
                <div className="card main-card">
                  <div className="row g-0 vh-100">
                    <div className="col-md-5 d-flex">
                      <div className="card-body p-4 p-lg-5 text-black">
                        <h1 className="mb-3 pb-3">Welcome.</h1>

                        <p>
                          This setup wizard will guide you through the few initial setups required to get started
                          with Nzyme.
                        </p>

                        <TermsOfUseModal />

                        <hr className="mb-4" />

                        <h2 className="mb-3">First Super Administrator User</h2>

                        <CreateUserForm onClick={onCreateUserClick}
                                        errorMessage={errorMessage}
                                        submitText="Create Super Administrator" />
                      </div>
                    </div>
                    <div className="col-md-7 d-none d-md-block justify-content-center right-half">
                      <video id="background-video" autoPlay loop muted poster={window.appConfig.assetsUri + "static/loginsplash_preview.jpg"}>
                        <source src={window.appConfig.assetsUri + "static/loginsplash.mp4"} type="video/mp4" />
                      </video>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </React.Fragment>
  )

}

export default SetupWizardPage;