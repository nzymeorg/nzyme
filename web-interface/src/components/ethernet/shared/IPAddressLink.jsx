import React from "react";
import {Link} from "react-router-dom";
import IPAddress from "./IPAddress";
import ApiRoutes from "../../../util/ApiRoutes";
import Port from "./Port";

export default function IPAddressLink(props) {

  const ip = props.ip;

  // Optional.
  const port = props.port;

  // Optional. Links to the IP address details page unless another target is passed.
  const to = props.to ? props.to : ApiRoutes.ETHERNET.IP.ADDRESS_DETAILS(ip);

  const full = props.full;

  const withPort = port !== undefined && port !== null;

  return (
      <Link to={to}>
        <IPAddress ip={ip} full={full} />{withPort ? <Port port={port} subdued={String(ip).includes(":")} /> : null}
      </Link>
  )

}