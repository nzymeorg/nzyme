import React from "react";

export default function Port(props) {

  const port = props.port;

  const subdued = props.subdued;

  return <span className={"port" + (subdued ? " port-subdued" : "")}>:{port}</span>

}