import React from "react";

function LoginThrottleWarning(props) {

  const show = props.show;

  if (!show) {
    return null;
  }

  return (
      <div className="row mt-2">
        <div className="col-md-12">
          <div className="alert alert-warning">
            This user's login attempts have been automatically slowed down following a sequence of unsuccessful tries.
            To safeguard against credential stuffing and password spraying attacks, Nzyme has implemented an extra delay
            to each login attempt. This slowing measure will automatically be lifted following the next successful login.
          </div>
        </div>
      </div>
  )

}

export default LoginThrottleWarning;