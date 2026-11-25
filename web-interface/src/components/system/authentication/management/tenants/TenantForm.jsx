import React, {useState} from "react";

function TenantForm(props) {

  const onClick = props.onClick;
  const submitText = props.submitText;

  const [name, setName] = useState(props.name ? props.name : "");
  const [description, setDescription] = useState(props.description ? props.description : "");

  const [sessionTimeoutMinutes, setSessionTimeoutMinutes] = useState(
      props.sessionTimeoutMinutes ? props.sessionTimeoutMinutes : ""
  );
  const [sessionTimeoutMinutesUnit, setSessionTimeoutMinutesUnit] = useState(
      props.sessionTimeoutMinutesUnit ? props.sessionTimeoutMinutesUnit : "minutes"
  )

  const [sessionInactivityTimeoutMinutes, setSessionInactivityTimeoutMinutes] = useState(
      props.sessionInactivityTimeoutMinutes ? props.sessionInactivityTimeoutMinutes : ""
  );
  const [sessionInactivityTimeoutMinutesUnit, setSessionInactivityTimeoutMinutesUnit] = useState(
      props.sessionInactivityTimeoutMinutesUnit ? props.sessionInactivityTimeoutMinutesUnit : "minutes"
  );

  const [mfaTimeoutMinutes, setMfaTimeoutMinutes] = useState(
      props.mfaTimeoutMinutes ? props.mfaTimeoutMinutes : ""
  );
  const [mfaTimeoutMinutesUnit, setMfaTimeoutMinutesUnit] = useState(
      props.mfaTimeoutMinutesUnit ? props.mfaTimeoutMinutesUnit : "minutes"
  );

  const updateValue = function(e, setter) {
    setter(e.target.value);
  }

  const formIsReady = function() {
    return name && name.trim().length > 0
        && description && description.trim().length > 0
        && sessionTimeoutMinutes && parseInt(sessionTimeoutMinutes, 10) > 0
        && sessionInactivityTimeoutMinutes && parseInt(sessionInactivityTimeoutMinutes, 10) > 0
        && mfaTimeoutMinutes && parseInt(mfaTimeoutMinutes, 10) > 0
  }

  const submit = function(e) {
    e.preventDefault();

    let sessionTimeoutMinutesInt = parseInt(sessionTimeoutMinutes, 10);
    let sessionInactivityTimeoutMinutesInt = parseInt(sessionInactivityTimeoutMinutes, 10);
    let mfaTimeoutMinutesInt = parseInt(mfaTimeoutMinutes, 10);

    if (sessionTimeoutMinutesUnit === "hours") {
      sessionTimeoutMinutesInt = sessionTimeoutMinutesInt*60;
    }

    if (sessionInactivityTimeoutMinutesUnit === "hours") {
      sessionInactivityTimeoutMinutesInt = sessionInactivityTimeoutMinutesInt*60;
    }

    if (mfaTimeoutMinutesUnit === "hours") {
      mfaTimeoutMinutesInt = mfaTimeoutMinutesInt*60;
    }

    onClick(
        name,
        description,
        sessionTimeoutMinutesInt,
        sessionInactivityTimeoutMinutesInt,
        mfaTimeoutMinutesInt
    );
  }

  return (
      <form>
        <div className="mb-3">
          <label htmlFor="name" className="form-label">Name</label>
          <input type="text" className="form-control" id="name" maxLength={256}
                 value={name} onChange={(e) => { updateValue(e, setName) }} />
          <div className="form-text">The name of the tenant.</div>
        </div>

        <div className="mb-3">
          <label htmlFor="description" className="form-label">Description</label>
          <textarea className="form-control" id="description" maxLength={10000} rows="3"
                    value={description} onChange={(e) => { updateValue(e, setDescription) }} />
          <div className="form-text">A short description of the tenant.</div>
        </div>

        <div className="mb-3">
          <i className="fa-solid fa-caret-right"></i>&nbsp;{' '}
          <a data-bs-toggle="collapse" href="#additionalTenantOptions">
             Additional options
          </a>

          <div className="mb-3 mt-3 collapse" id="additionalTenantOptions">
            <div className="mb-3">
              <label htmlFor="session-timeout-minutes" className="form-label">Session Timeout</label>
              <div className="input-group">
                <input type="number" className="form-control" id="session-timeout-minutes"
                       value={sessionTimeoutMinutes} onChange={(e) => { updateValue(e, setSessionTimeoutMinutes) }} />
                <select className="form-select"
                        value={sessionTimeoutMinutesUnit}
                        onChange={(e) => { updateValue(e, setSessionTimeoutMinutesUnit(e.target.value)) }}>
                  <option value="minutes">Minutes</option>
                  <option value="hours">Hours</option>
                </select>
              </div>
              <div className="form-text">
                How long a user can be logged in before they are logged out automatically, no matter if active or not.
              </div>
            </div>

            <div className="mb-3">
              <label htmlFor="session-inactivity-timeout-minutes" className="form-label">Session Inactivity Timeout</label>
              <div className="input-group">
                <input type="number" className="form-control" id="session-inactivity-timeout-minutes"
                       value={sessionInactivityTimeoutMinutes} onChange={(e) => { updateValue(e, setSessionInactivityTimeoutMinutes) }} />
                <select className="form-select"
                        value={sessionInactivityTimeoutMinutesUnit}
                        onChange={(e) => { updateValue(e, setSessionInactivityTimeoutMinutesUnit(e.target.value)) }} >
                  <option value="minutes">Minutes</option>
                  <option value="hours">Hours</option>
                </select>
              </div>
              <div className="form-text">
                How long a user can be inactive before they are logged out automatically. Note that having the web interface
                open in a browser counts as activity, even if the user is not performing any actions.
              </div>
            </div>

            <div className="mb-3">
              <label htmlFor="mfa-timeout-minutes" className="form-label">Multi-Factor Entry Timeout</label>
              <div className="input-group">
                <input type="number" className="form-control" id="mfa-timeout-minutes"
                       value={mfaTimeoutMinutes} onChange={(e) => { updateValue(e, setMfaTimeoutMinutes) }} />
                <select className="form-select"
                        value={mfaTimeoutMinutesUnit}
                        onChange={(e) => { updateValue(e, setMfaTimeoutMinutesUnit(e.target.value)) }} >
                  <option value="minutes">Minutes</option>
                  <option value="hours">Hours</option>
                </select>
              </div>
              <div className="form-text">
                How long a user has to complete the multi-factor challenge after a successful login before the process
                is canceled and the user has to start over.
              </div>
            </div>
          </div>
        </div>

        <button className="btn btn-primary" onClick={submit} disabled={!formIsReady()}>
          {submitText}
        </button>
      </form>
  )

}

export default TenantForm;