import React from "react";
import {Link, useParams} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import Ipv6TransitionTunnelDetails from "./Ipv6TransitionTunnelDetails";
import usePageTitle from "../../../../util/UsePageTitle";

export default function Ipv6TransitionTunnelDetailsPage() {

  usePageTitle("IPv6 Transition Tunnel Details");

  const { sessionId } = useParams();

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
                <li className="breadcrumb-item">Tunnels</li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.TUNNELS.IPV6_TRANSITION.INDEX}>IPv6 Transition Tunnels</Link></li>
                <li className="breadcrumb-item active" aria-current="page">{sessionId}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-9">
            <h1>
              IPv6 Transition Tunnel &quot;{sessionId}&quot;
            </h1>
          </div>
          <div className="col-3 text-end">
            <Link to={ApiRoutes.ETHERNET.TUNNELS.IPV6_TRANSITION.INDEX} className="btn btn-secondary">Back</Link>
          </div>
        </div>

        <Ipv6TransitionTunnelDetails sessionId={sessionId} />

      </React.Fragment>
  )

}
