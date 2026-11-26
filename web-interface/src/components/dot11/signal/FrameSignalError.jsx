import React from "react";

export default function FrameSignalError({error}) {

  const tooMuch = error.response && error.response.status === 422;

  return (
      <div className="alert alert-warning mb-0">
        {tooMuch
            ? "The comparison took too long. Select a shorter time range or fewer taps."
            : "The comparison could not be loaded."}
      </div>
  )

}
