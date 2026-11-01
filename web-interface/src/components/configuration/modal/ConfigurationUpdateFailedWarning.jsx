import React from 'react'

function ConfigurationUpdateFailedWarning (props) {
  if (props.failed) {
    return (
            <div className="alert alert-danger">
                <i className="fa-solid fa-shake fa-triangle-exclamation"></i> Could not save the configuration. Please
                check your Nzyme log file for errors. <a href="https://go.nzyme.org/config-update-failed" target="_blank" rel="noreferrer">Help</a>
            </div>
    )
  } else {
    return null
  }
}

export default ConfigurationUpdateFailedWarning
