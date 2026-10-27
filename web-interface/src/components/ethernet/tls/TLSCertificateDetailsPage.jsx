import React from "react";
import {Link, useParams} from "react-router-dom";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import TLSCertificateDetails from "./TLSCertificateDetails";

export default function TLSCertificateDetailsPage() {

  usePageTitle("TLS Certificate Details");

  const { sha256 } = useParams()

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
                <li className="breadcrumb-item">TLS</li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.TLS.CERTIFICATES.INDEX}>Certificates</Link></li>
                <li className="breadcrumb-item active" aria-current="page">{sha256}</li>
              </ol>
            </nav>
          </div>
        </div>

        <TLSCertificateDetails sha256={sha256} />

      </React.Fragment>
  )

}
