import React from "react";

export default function DHCPFingerprintExclusionCriteria({exclusion}) {

  return (
    <React.Fragment>
      <span className="text-nowrap">
        {exclusion.mac_is_prefix ? "Vendor prefix" : "MAC"}{' '}
        <span className="machine-data">{exclusion.mac}</span>
      </span>
      {exclusion.transaction_types && exclusion.transaction_types.length > 0 ?
        <React.Fragment>
          <span className="text-muted"> and </span>
          <span className="text-nowrap">Types {exclusion.transaction_types.join(", ")}</span>
        </React.Fragment>
        : <span className="text-muted"> (all transaction types)</span>}
    </React.Fragment>
  )

}
