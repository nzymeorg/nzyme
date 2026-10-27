import React from "react";
import {Link, useParams} from "react-router-dom";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import TLSSessionDetails from "./TLSSessionDetails";

export default function TLSSessionDetailsPage() {

  usePageTitle("TLS Session Details");

  const { sessionKey } = useParams()

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
                <li className="breadcrumb-item">TLS</li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.TLS.SESSIONS.INDEX}>TLS Sessions</Link></li>
                <li className="breadcrumb-item active" aria-current="page">{sessionKey}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <h1>
              TLS Session &quot;{sessionKey}&quot;
            </h1>
          </div>
        </div>

        <TLSSessionDetails sessionKey={sessionKey} />

      </React.Fragment>
  )

}
