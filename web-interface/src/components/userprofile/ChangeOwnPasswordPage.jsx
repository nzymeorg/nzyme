import {Link} from "react-router-dom";
import React, {useContext, useEffect, useRef, useState} from "react";
import UserProfileService from "../../services/UserProfileService";
import Routes from "../../util/ApiRoutes";

import InlineFormValidationMessage from "../misc/InlineFormValidationMessage";
import FormSubmitErrorMessage from "../misc/FormSubmitErrorMessage";
import {toast} from "react-toastify";
import {AppContext} from "../../App";
import usePageTitle from "../../util/UsePageTitle";

const userProfileService = new UserProfileService();

function ChangeOwnPasswordPage() {

  usePageTitle("Change Password");

  const app = useContext(AppContext);

  const maskCurrent = useRef();
  const [maskedCurrent, setMaskedCurrent] = useState(true);
  const maskNew = useRef();
  const [maskedNew, setMaskedNew] = useState(true);

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");

  const [passwordValidation, setPasswordValidation] = useState(undefined);
  const [formSubmitting, setFormSubmitting] = useState(false);

  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    if (newPassword && (newPassword.length < 12 || newPassword.length > 128)) {
      setPasswordValidation("Must be between 12 and 128 characters long.");
    } else {
      setPasswordValidation(undefined);
    }
  }, [newPassword])

  const toggleMaskCurrent = function() {
    if (maskedCurrent) {
      // Unmask password.
      maskCurrent.current.classList.remove('text-muted');
      setMaskedCurrent(false);
    } else {
      // Mask password.
      maskCurrent.current.classList.add('text-muted');
      setMaskedCurrent(true);
    }
  }

  const toggleMaskNew = function() {
    if (maskedNew) {
      // Unmask password.
      maskNew.current.classList.remove('text-muted');
      setMaskedNew(false);
    } else {
      // Mask password.
      maskNew.current.classList.add('text-muted');
      setMaskedNew(true);
    }
  }

  const onSubmit = function(e) {
    e.preventDefault();

    setErrorMessage(null);
    setFormSubmitting(true);

    userProfileService.changeOwnPassword(currentPassword, newPassword, function() {
      toast.success('Password updated.');
      app.logout();
    }, function(error) {
      setFormSubmitting(false);
      setErrorMessage(error.response.data.message);
    })
  }

  const updateValue = function(e, setter) {
    setter(e.target.value);
  }

  const formIsReady = function() {
    return currentPassword && currentPassword.trim().length > 0 &&
        newPassword && newPassword.trim().length > 0 && !passwordValidation
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-8">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item">
                  <Link to={Routes.USERPROFILE.PROFILE}>Your Profile</Link>
                </li>
                <li className="breadcrumb-item active" aria-current="page">Change your password</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row">
          <div className="col-md-8">
            <div className="row">
              <div className="col-md-12">
                <div className="card">
                  <div className="card-body">
                    <h3>Change your password</h3>

                    <form>
                      <div className="mb-3">
                        <label htmlFor="currentPassword" className="form-label">Current Password</label>
                        <div style={{display: "block"}}>
                          <input type={maskedCurrent ? "password" : "input"} className="form-control" id="currentPassword" aria-describedby="currentPassword"
                                 value={currentPassword} onChange={(e) => { updateValue(e, setCurrentPassword) }}
                                 style={{width: "100%", display: "inline-block"}} />
                          <i className="fa fa-eye text-muted" style={{marginLeft: -30, cursor: "pointer"}} onClick={toggleMaskCurrent} ref={maskCurrent}></i>
                        </div>
                        <div className="form-text">
                          Your current password
                        </div>
                      </div>

                      <div className="mb-3">
                        <label htmlFor="password" className="form-label">New Password</label>
                        <div style={{display: "block"}}>
                          <input type={maskedNew ? "password" : "input"} className="form-control" id="password" aria-describedby="password"
                                 autoComplete="new-password" value={newPassword} onChange={(e) => { updateValue(e, setNewPassword) }}
                                 style={{width: "100%", display: "inline-block"}} />
                          <i className="fa fa-eye text-muted" style={{marginLeft: -30, cursor: "pointer"}} onClick={toggleMaskNew} ref={maskNew}></i>
                        </div>
                        <div className="form-text">
                          Your new password{' '}
                          <InlineFormValidationMessage message={passwordValidation} />
                        </div>
                      </div>

                      <div className="alert alert-info">
                        You will be logged out and prompted to log in again after changing your password.
                      </div>

                      <button className="btn btn-sm btn-primary" onClick={onSubmit} disabled={!formIsReady() || formSubmitting}>
                        {formSubmitting ? "Please wait ..." : "Change Password"}
                      </button>

                      <FormSubmitErrorMessage message={errorMessage} />
                    </form>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}

export default ChangeOwnPasswordPage;