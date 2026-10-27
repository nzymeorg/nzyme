import {Link} from "react-router-dom";
import React from "react";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";
import ApiRoutes from "../../../util/ApiRoutes";

export default function TLSCertificateFingerprint(props) {

  const sha256 = props.sha256;

  return (
    <Link to={ApiRoutes.ETHERNET.TLS.CERTIFICATES.DETAILS(sha256)}>
      <FullCopyShortenedId value={sha256} />
    </Link>
  )

}
