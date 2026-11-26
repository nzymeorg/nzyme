import React from "react";
import AutomaticDot11MacAddressLink from "../../shared/context/macs/AutomaticDot11MacAddressLink";
import {frameSignalSourceColor} from "./FrameSignalChart";

export default function FrameSignalSourceAddress({source, index}) {

  return (
      <React.Fragment>
        <span style={{display: "inline-block", width: 12, height: 12, borderRadius: 6, marginRight: 8,
          backgroundColor: frameSignalSourceColor(index)}} />
        <AutomaticDot11MacAddressLink mac={source.address} type={source.mac.type} addressWithContext={source.mac.mac} />
      </React.Fragment>
  )

}
