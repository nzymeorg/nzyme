import React, {useEffect, useState} from "react";
import AssetStylesheet from "../../misc/AssetStylesheet";
import AuthenticationService from "../../../services/AuthenticationService";
import LoadingSpinner from "../../misc/LoadingSpinner";
import MFASetupStep1 from "./MFASetupStep1";
import MFASetupStep2 from "./MFASetupStep2";
import LoginImage from "./LoginImage";
import usePageTitle from "../../../util/UsePageTitle";

const authenticationService = new AuthenticationService();

function MFASetupPage(props) {

  usePageTitle("Setup");

  const onActionCompleted = props.onActionCompleted;
  const customImage = props.customImage;

  const [userSecret, setUserSecret] = useState(null);
  const [userEmail, setUserEmail] = useState(null);
  const [recoveryCodes, setRecoveryCodes] = useState(null);

  const [completionInProgress, setCompletionInProgress] = useState(false);

  const [stage, setStage] = useState(1);

  useEffect(() => {
    authenticationService.initializeMFASetup(setUserSecret, setUserEmail, setRecoveryCodes);
  }, [])

  const buildOTPAuthURL = function() {
    return "otpauth://totp/nzyme:" + userEmail + "?secret=" + userSecret + "&issuer=Nzyme&algorithm=SHA1&digits=6&period=30"
  }

  const advanceToStage2 = function() {
    setStage(2);
  }

  const finishSetup = function() {
    setCompletionInProgress(true); // This will never go back to false. App lifecycle takes over to prompt login.
    authenticationService.finishMFASetup(onActionCompleted);
  }

  if (!userSecret || !userEmail || !recoveryCodes) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <AssetStylesheet filename="onebox.css"/>

        <section className="vh-100 start">
          <div className="container py-5 h-100 mb-5">
            <div className="row d-flex justify-content-center align-items-center h-100">
              <div className="col col-xl-10">
                <div className="card main-card">
                  <div className="row g-0 vh-100">

                    <div className="col-md-5 d-flex align-items-center">
                      <div className="card-body p-4 p-lg-5 text-black">
                        <h1 className="mb-3 pb-3">Multi-Factor Authentication</h1>

                        <p>Setup required before you can proceed.</p>

                        <hr className="mb-4"/>

                        <MFASetupStep1 show={stage === 1}
                                       onAdvance={advanceToStage2}
                                       otpAuthUrl={buildOTPAuthURL()} />

                        <MFASetupStep2 show={stage === 2}
                                       onAdvance={finishSetup}
                                       recoveryCodes={recoveryCodes}
                                       completionInProgress={completionInProgress} />
                      </div>
                    </div>

                    <div className="col-md-7 d-none d-md-block justify-content-center right-half">
                      <LoginImage customImage={customImage} />
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

export default MFASetupPage;