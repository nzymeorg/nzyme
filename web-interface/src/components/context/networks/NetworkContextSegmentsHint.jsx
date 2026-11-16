import React from "react";
import {Link} from "react-router-dom";
import ApiRoutes from "../../../util/ApiRoutes";
import WithSubsystem from "../../misc/WithSubsystem";

export default function NetworkContextSegmentsHint() {

  return (
    <WithSubsystem subsystem="ethernet">
      <p className="text-muted">
        For parts of your own network, consider a{' '}
        <Link to={ApiRoutes.ETHERNET.SEGMENTATION.SEGMENTS.INDEX}>segment</Link>.
      </p>
    </WithSubsystem>
  )

}
