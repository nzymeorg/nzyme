import React from "react";

export default function STUNConnectionSuccessIndicator({successful, withText = false}) {
  if (successful) {
    return (
      <>
        <i className="fa fa-circle text-success" title="Connection Successful" />
        {withText ? <span>&nbsp; Successful</span> : null}
      </>
    )
  } else {
    return (
      <>
        <i className="fa fa-circle text-danger" title="Connection Failed" />
        {withText ? <span>&nbsp; Failure</span> : null}
      </>
    )
  }
}