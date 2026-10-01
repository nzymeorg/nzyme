import React from "react";

export default function AssetIpAddresses({addresses, filterElement = undefined}) {

  const additional = () => {
    if (addresses.length < 2) {
      return null
    }

    return <span>[+{addresses.length-1}]</span>
  }

  if (!addresses || addresses.length === 0) {
    return <span className="text-muted">None</span>;
  }

  return (
      <span title={addresses.join(", ")}>
        <span className="ip-address">{addresses[0]}</span>{' '}{additional()}{filterElement}
      </span>
  )

}