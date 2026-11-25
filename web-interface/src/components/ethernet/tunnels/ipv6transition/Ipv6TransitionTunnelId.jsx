import {Link} from "react-router-dom";
import React from "react";
import FullCopyShortenedId from "../../../shared/FullCopyShortenedId";
import ApiRoutes from "../../../../util/ApiRoutes";

export default function Ipv6TransitionTunnelId(props) {

  const tunnelId = props.tunnelId;

  return (
    <Link to={ApiRoutes.ETHERNET.TUNNELS.IPV6_TRANSITION.TUNNEL_DETAILS(tunnelId)}>
      <FullCopyShortenedId value={tunnelId} />
    </Link>
  )

}
