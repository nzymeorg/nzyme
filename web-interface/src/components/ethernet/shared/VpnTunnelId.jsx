import {Link} from "react-router-dom";
import React from "react";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";
import ApiRoutes from "../../../util/ApiRoutes";

export default function VpnTunnelId(props) {

  const tunnelId = props.tunnelId;

  return (
    <Link to={ApiRoutes.ETHERNET.TUNNELS.VPN.TUNNEL_DETAILS(tunnelId)}>
      <FullCopyShortenedId value={tunnelId} />
    </Link>
  )

}
