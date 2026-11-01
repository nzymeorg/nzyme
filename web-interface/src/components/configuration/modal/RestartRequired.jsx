import React from 'react'

function RestartRequired (props) {
  if (props.required) {
    return (
            <div className="alert alert-warning mt-2">
                Changing this value <strong>does require a restart of Nzyme</strong> to take effect.
            </div>
    )
  } else {
    return (
            <div className="alert alert-primary mt-2">
                Changing this value does <strong>not</strong> require a restart of Nzyme to take effect.
            </div>
    )
  }
}

export default RestartRequired
