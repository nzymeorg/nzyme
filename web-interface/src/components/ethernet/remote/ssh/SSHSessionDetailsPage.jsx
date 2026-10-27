import React from "react";
import {Link, useParams} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import SSHSessionDetails from "./SSHSessionDetails";
import usePageTitle from "../../../../util/UsePageTitle";

export default function SSHSessionDetailsPage() {

  usePageTitle("SSH Session Details");

  const { sessionId } = useParams()

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
                <li className="breadcrumb-item">Remote Access</li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.REMOTE.SSH.INDEX}>SSH Sessions</Link></li>
                <li className="breadcrumb-item active" aria-current="page">{sessionId}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <h1>
              SSH Session &quot;{sessionId}&quot;
            </h1>
          </div>
        </div>

        <SSHSessionDetails sessionId={sessionId} />

      </React.Fragment>
  )

}