import React from "react";
import {shortenIpAddress} from "../shared/IPAddress";

const TITLE_LIMIT = 10;

// MAX_LISTED_IP_ADDRESSES of the node.
const LISTED_LIMIT = 100;

export default function AssetIpAddresses({addresses, filterElement = undefined}) {

  const additional = () => {
    if (addresses.length < 2) {
      return null
    }

    return <span>[+{addresses.length-1}{addresses.length >= LISTED_LIMIT ? " or more" : ""}]</span>
  }

  if (!addresses || addresses.length === 0) {
    return <span className="text-muted">None</span>;
  }

  return (
      <span title={addresses.slice(0, TITLE_LIMIT).join(", ") + (addresses.length > TITLE_LIMIT ? ", …" : "")}>
        <span className="ip-address">{shortenIpAddress(addresses[0])}</span>{' '}{additional()}{filterElement}
      </span>
  )

}