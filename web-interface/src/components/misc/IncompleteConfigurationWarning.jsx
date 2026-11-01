import React from 'react'

export default function IncompleteConfigurationWarning (props) {
  if (props.show) {
    return (
      <div className="row">
        <div className="col-md-12">
          <div className="alert alert-warning mb-0">
            Required settings are missing and marked in red on this page. Please set all variables and
            restart Nzyme if required. The configuration dialog will tell you if a restart is required
            or not.
          </div>
        </div>
      </div>
    )
  } else {
    return null
  }
}