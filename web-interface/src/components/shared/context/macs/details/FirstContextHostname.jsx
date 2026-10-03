import React from 'react';
import numeral from "numeral";
import {truncate} from "../../../../../util/Tools";

export default function FirstContextHostname(props) {

  const hostnames = props.hostnames;

  if (!hostnames || hostnames.length === 0) {
    return <span className="text-muted">n/a</span>
  }

  return (
      <span>{truncate(hostnames[0].hostname, 20)} ({numeral(hostnames.length).format("0,0")} total)</span>
  )

}