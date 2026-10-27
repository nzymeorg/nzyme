import {Link} from "react-router-dom";
import React from "react";
import ApiRoutes from "../../../../util/ApiRoutes";

function PGPKeysOutOfSyncWarning(props) {

  if (props.show) {
    return (
        <div className="alert alert-danger">
          <i className="fa-solid fa-warning"></i>{' '}
          <strong>Not every node has the same PGP key.</strong> This will cause potentially irreversible problems.
          Please make sure all nzyme nodes use the same local PGP keys.
          Please follow the instructions on the <Link to={ApiRoutes.SYSTEM.HEALTH.INDEX}>Health Console</Link> page for
          resolution.
        </div>
    )
  } else {
    return null;
  }

}

export default PGPKeysOutOfSyncWarning;