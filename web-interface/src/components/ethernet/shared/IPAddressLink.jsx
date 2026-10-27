import React from "react";
import {Link} from "react-router-dom";
import IPAddress from "./IPAddress";
import ApiRoutes from "../../../util/ApiRoutes";
import Port from "./Port";

export default function IPAddressLink(props) {

  const ip = props.ip;

  // Optional.
  const port = props.port;

  return (
      <Link to={ApiRoutes.ETHERNET.IP.ADDRESS_DETAILS(ip)}>
        <IPAddress ip={ip} />{port === undefined || port === null ? null : <Port port={port} />}
      </Link>
  )

}