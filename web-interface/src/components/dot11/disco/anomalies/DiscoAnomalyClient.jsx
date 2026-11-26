import React from "react";
import AutomaticDot11MacAddressLink from "../../../shared/context/macs/AutomaticDot11MacAddressLink";

export default function DiscoAnomalyClient({anomaly, filterElement = null}) {

  if (anomaly.client) {
    return <AutomaticDot11MacAddressLink mac={anomaly.client.mac.address}
                                         type={anomaly.client.type}
                                         addressWithContext={anomaly.client.mac}
                                         filterElement={filterElement} />
  }

  return <span className="text-muted">All Clients</span>

}
