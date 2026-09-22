import React from "react";

export default function RTSPStreamActiveIndicator({stream, withText = false}) {

    if (stream.is_active === null || stream.is_active === undefined) {
        return (
          <>
            <i className="fa fa-circle text-muted" title="Could not determine if stream is active or not" />
            {withText ? <span>&nbsp; Unknown</span> : null}
          </>
        )
    }

    if (stream.is_active) {
        return (
          <>
            <i className="fa fa-circle text-success blink" title="Stream is active" />
              {withText ? <span>&nbsp; Active</span> : null}
          </>
        )
    } else {
        return (
          <>
             <i className="fa fa-circle text-muted" title="Stream is not active" />
              {withText ? <span>&nbsp; Inactive</span> : null}
          </>
        )
    }

}