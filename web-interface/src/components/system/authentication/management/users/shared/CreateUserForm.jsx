import React, {useEffect, useRef, useState} from "react";
import InlineFormValidationMessage from "../../../../../misc/InlineFormValidationMessage";
import FormSubmitErrorMessage from "../../../../../misc/FormSubmitErrorMessage";

function CreateUserForm(props) {

  const EMAILREGEX = /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i;

  const mask = useRef();
  const [masked, setMasked] = useState(true);

  const onClick = props.onClick;
  const submitText = props.submitText;
  const errorMessage = props.errorMessage;

  const [email, setEmail] = useState(props.email ? props.email : "");
  const [password, setPassword] = useState("");
  const [name, setName] = useState(props.name ? props.name : "");
  const [disableMfa, setDisableMfa] = useState(props.disableMfa !== null && props.disableMfa !== undefined ? props.disableMfa : false);

  const [emailValidation, setEmailValidation] = useState(undefined);
  const [passwordValidation, setPasswordValidation] = useState(undefined);
  const [formSubmitting, setFormSubmitting] = useState(false);

  useEffect(() => {
    if (email && !EMAILREGEX.test(email)) {
      setEmailValidation("Must be a valid email address.");
    } else {
      setEmailValidation(undefined);
    }
  }, [email])

  useEffect(() => {
    if (password && (password.length < 12 || password.length > 128)) {
      setPasswordValidation("Must be between 12 and 128 characters long.");
    } else {
      setPasswordValidation(undefined);
    }
  }, [password])

  const toggleMask = function() {
    if (masked) {
      // Unmask password.
      mask.current.classList.remove('text-muted');
      setMasked(false);
    } else {
      // Mask password.
      mask.current.classList.add('text-muted');
      setMasked(true);
    }
  }

  const updateValue = function(e, setter) {
    setter(e.target.value);
  }

  const formIsReady = function() {
    return email && email.trim().length > 0 && password && password.trim().length > 0 && name && name.trim().length > 0
      && !emailValidation && !passwordValidation
  }

  const submit = function(e) {
    e.preventDefault();
    setFormSubmitting(true);
    onClick(email, password, name, disableMfa, function() {
      setFormSubmitting(false);
    });
  }

  return (
      <form>
        <div className="mb-3">
          <div className="mb-3">
            <label htmlFor="name" className="form-label">Full Name</label>
            <input type="email" className="form-control" id="name" maxLength={128} aria-describedby="name"
                   value={name} onChange={(e) => { updateValue(e, setName) }} />
            <div className="form-text">The full name of the new user.</div>
          </div>

          <label htmlFor="email" className="form-label">Email Address / Username</label>
          <input type="email" className="form-control" id="email" maxLength={128} aria-describedby="email"
                 value={email} onChange={(e) => { updateValue(e, setEmail) }} />
          <div className="form-text">
            The email address of the new user. This will be the username.{' '}
            <InlineFormValidationMessage message={emailValidation} />
          </div>
        </div>

        <div className="mb-3">
          <label htmlFor="password" className="form-label">Password</label>
          <div style={{display: "block"}}>
            <input type={masked ? "password" : "input"} className="form-control" id="password" aria-describedby="password"
                   autoComplete="new-password" value={password} onChange={(e) => { updateValue(e, setPassword) }}
                   style={{width: "100%", display: "inline-block"}} />
            <i className="fa fa-eye text-muted" style={{marginLeft: -30, cursor: "pointer"}} onClick={toggleMask} ref={mask}></i>
          </div>
          <div className="form-text">
            The password of the new user.{' '}
            <InlineFormValidationMessage message={passwordValidation} />
          </div>
        </div>


        <div className="mb-3">
          <div className="form-check">
            <input className="form-check-input" type="checkbox" checked={disableMfa}
                   id="disable_mfa" onChange={(e) => setDisableMfa(e.target.checked)} />
            <label className="form-check-label" htmlFor="disable_mfa">
              Disable Multi-Factor Authentication (MFA)
            </label>

            <div className="form-text">
              The user will not be able to use Multi-Factor Authentication (MFA) if this option is selected. Not
              recommended.
            </div>
          </div>
        </div>

        <button className="btn btn-sm btn-primary" onClick={submit} disabled={!formIsReady() || formSubmitting}>
          {formSubmitting ? "Please wait ..." : submitText}
        </button>

        <FormSubmitErrorMessage message={errorMessage} />
      </form>
  )

}

export default CreateUserForm;