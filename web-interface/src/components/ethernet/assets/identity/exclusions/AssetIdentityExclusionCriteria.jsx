import React from "react";
import {typeTitle} from "./AssetIdentityExclusionForm";

export default function AssetIdentityExclusionCriteria({exclusion}) {

  return (
    <React.Fragment>
      <span className="text-nowrap">
        {exclusion.mac_is_prefix ? "Vendor prefix" : "MAC"}{' '}
        <span className="machine-data">{exclusion.mac}</span>
      </span>
      {exclusion.types && exclusion.types.length > 0 ?
        <React.Fragment>
          <span className="text-muted"> and </span>
          <span className="text-nowrap">Types {exclusion.types.map((t) => typeTitle(t)).join(", ")}</span>
        </React.Fragment>
        : <span className="text-muted"> (all detection types)</span>}
    </React.Fragment>
  )

}
