import React from "react";

export default function ApprovalStatus({approved, ignored}) {

  if (approved) {
    return (
        <span>
          <i className="fa fa-check text-success"/> Approved
        </span>
    )
  }

  if (ignored) {
    return (
        <span>
          <i className="fa fa-info-circle text-primary" /> Ignored
        </span>
    )
  }

  return (
      <span>
        <i className="fa fa-warning text-warning"/> Not Approved
      </span>
  )

}
