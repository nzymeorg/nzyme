import React from "react";
import {truncate} from "../../../../../util/Tools";
import numeral from "numeral";

export default function FirstContextIpAddress(props) {

  const addresses = props.addresses;

  if (!addresses || addresses.length === 0) {
    return <span className="text-muted">n/a</span>
  }

  return (
      <span>{truncate(addresses[0].ip_address, 20)} ({numeral(addresses.length).format("0,0")} total)</span>
  )

}