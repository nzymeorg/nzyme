import React, {useContext, useState} from "react";
import UserProfile from "./UserProfile";
import MfaRecoveryCodes from "./MfaRecoveryCodes";
import UserProfileService from "../../services/UserProfileService";
import {toast} from "react-toastify";
import ApiRoutes from "../../util/ApiRoutes";
import {UserContext} from "../../App";
import usePageTitle from "../../util/UsePageTitle";
import WithPermission from "../misc/WithPermission";
import UserApiKeysTable from "./apikeys/UserApiKeysTable";
import CreateUserApiKeyForm from "./apikeys/CreateUserApiKeyForm";

const userProfileService = new UserProfileService();

function UserProfilePage(props) {

  usePageTitle("User Profile");

  const user = useContext(UserContext);

  const onMfaReset = props.onMfaReset;
  const [showRecoveryCodes, setShowRecoveryCodes] = useState(false);

  const [unitSystem, setUnitSystem] = useState(user.unit_system);

  const [apiKeysRevision, setApiKeysRevision] = useState(new Date());

  const resetMfa = function() {
    if (!confirm("Really reset your MFA? You will be logged out and prompted to set up a new MFA method " +
        "after you log in again.")) {
      return;
    }

    userProfileService.resetOwnMfa(function () {
      toast.success('MFA successfully reset.');
      onMfaReset();
    })
  }

  const onSaveUnitSystem = (e) => {
    e.preventDefault();

    userProfileService.setOwnUnitSystem(unitSystem, () => {
      toast.success('Unit system selection updated.');
    })
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <h1>Your User Profile</h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-8">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>User Data</h3>

                    <UserProfile/>

                    <a className="btn btn-sm btn-secondary mt-2" href={ApiRoutes.USERPROFILE.PASSWORD}>
                      Change Password
                    </a>
                  </div>
                </div>
              </div>
            </div>

            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Unit System</h3>

                    <div className="text-muted">
                      You can set your preferred unit system that will be used across the web interface.
                    </div>


                    <div className="input-group mt-2">
                      <select className="form-select"
                              value={unitSystem}
                              onChange={(e) => {
                                setUnitSystem(e.target.value);
                              }}>
                        <option value="imperial">Imperial</option>
                        <option value="metric">Metric</option>
                      </select>
                      <button className="btn btn-primary" type="button" onClick={onSaveUnitSystem}>Save</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            { user.has_mfa_disabled ? null :
            <div className="row mt-3">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Multi-Factor Authentication</h3>

                    { /* We can show this statically because if you reached this, your MFA is set up and active. */}
                    <div className="alert alert-success">
                      <i className="fa fa-check-circle"/>{' '}
                      Your account is protected by multi-factor authentication.
                    </div>

                    <hr/>

                    <h4>Reset MFA</h4>
                    <p>
                      You can reset your own multi-factor authentication. After the reset, you will be logged out and
                      prompted to set up a new MFA method after you log in again.
                    </p>

                    <button className="btn btn-sm btn-secondary" onClick={resetMfa}>
                      Reset my MFA configuration
                    </button>

                    <hr/>

                    <h4>Recovery Codes</h4>
                    <p>
                      If you lose access to your code generator, you can use each recovery code <strong>once</strong> to
                      pass multi-factor authentication challenges. Store these keys in a safe place and do not share
                      them with anyone.
                    </p>

                    <MfaRecoveryCodes show={showRecoveryCodes}/>

                    <button className="btn btn-sm btn-secondary mt-2"
                            onClick={() => setShowRecoveryCodes(!showRecoveryCodes)}>
                      {showRecoveryCodes ? "Hide Codes" : "Show Codes"}
                    </button>
                  </div>
                </div>
              </div>
            </div>
            }

            <WithPermission permission="api_keys_manage_own">
              <div className="row mt-3">
                <div className="col-md-12">
                  <div className="card">
                    <div className="card-body">
                      <h3>API Keys</h3>

                      <p>
                        API keys let scripts and other clients access the Nzyme REST API with your permissions.
                        Explore all available endpoints in the{' '}
                        <a href="https://api.docs.nzyme.org/" target="_blank" rel="noreferrer">
                          interactive API browser
                        </a>.
                      </p>

                      <h4>Create API Key</h4>

                      <CreateUserApiKeyForm onKeyCreated={() => setApiKeysRevision(new Date())} />

                      <hr/>

                      <h4>Your API Keys</h4>

                      <UserApiKeysTable revision={apiKeysRevision}
                                        onKeyDeleted={() => setApiKeysRevision(new Date())} />
                    </div>
                  </div>
                </div>
              </div>
            </WithPermission>
          </div>

          <div className="col-md-4">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    For security reasons, users can manage their own password and MFA but not edit their name or
                    email address. Please contact your administrator to perform such a change.
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
)

}

export default UserProfilePage;