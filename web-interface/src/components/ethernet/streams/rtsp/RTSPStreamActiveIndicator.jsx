import React from "react";

export default function RTSPStreamActiveIndicator({stream}) {

    if (stream.is_active === null || stream.is_active === undefined) {
        return <i className="fa fa-circle text-muted" title="Could not determine if stream is active or not" />
    }

    if (stream.is_active) {
        return <i className="fa fa-circle text-success" title="Stream is active" />
    } else {
        return <i className="fa fa-circle text-danger" title="Stream is not active" />
    }

}