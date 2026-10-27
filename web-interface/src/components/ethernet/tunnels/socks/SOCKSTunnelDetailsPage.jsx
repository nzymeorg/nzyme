import React from "react";
import {Link, useParams} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import SOCKSTunnelDetails from "./SOCKSTunnelDetails";
import usePageTitle from "../../../../util/UsePageTitle";

export default function SOCKSTunnelDetailsPage() {

  usePageTitle("SOCKS Tunnel Details");

  const { sessionId } = useParams();

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
                <li className="breadcrumb-item">Tunnels</li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.TUNNELS.SOCKS.INDEX}>SOCKS Tunnels</Link></li>
                <li className="breadcrumb-item active" aria-current="page">{sessionId}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <h1>
              SOCKS Tunnel &quot;{sessionId}&quot;
            </h1>
          </div>
        </div>

        <SOCKSTunnelDetails sessionId={sessionId} />

      </React.Fragment>
  )

}