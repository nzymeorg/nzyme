import React from "react";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";

export default function SegmentationAddressMac({address}) {

  if (!address || !address.mac) {
    return (
        <span className="text-muted" title="No single asset is known to have used this address in the selected time range.">
          n/a
        </span>
    )
  }

  return <EthernetMacAddress addressWithContext={address.mac} withAssetLink withAssetName />

}
