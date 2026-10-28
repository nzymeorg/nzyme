import React from "react";
import {Link, useParams} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import VPNTunnelDetails from "./VPNTunnelDetails";
import usePageTitle from "../../../../util/UsePageTitle";

export default function VPNTunnelDetailsPage() {

  usePageTitle("VPN Connection Details");

  const { sessionId } = useParams();

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
                <li className="breadcrumb-item">Tunnels</li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX}>VPN Connections</Link></li>
                <li className="breadcrumb-item active" aria-current="page">{sessionId}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-9">
            <h1>
              VPN Connection &quot;{sessionId}&quot;
            </h1>
          </div>
          <div className="col-3 text-end">
            <Link to={ApiRoutes.ETHERNET.TUNNELS.VPN.INDEX} className="btn btn-secondary">Back</Link>
          </div>
        </div>

        <VPNTunnelDetails sessionId={sessionId} />

      </React.Fragment>
  )

}
