import React from "react";

export default function STUNConnectionActiveIndicator({active, withText = false}) {
  if (active) {
    return (
      <>
        <i className="fa fa-circle text-success blink" title="Connection is Active" />
        {withText ? <span>&nbsp; Active</span> : null}
      </>
    )
  } else {
    return (
      <>
        <i className="fa fa-circle text-muted" title="Connection is Inactive" />
        {withText ? <span>&nbsp; Inactive</span> : null}
      </>
    )
  }
}