import {Link} from "react-router-dom";
import React from "react";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";
import ApiRoutes from "../../../util/ApiRoutes";

export default function TLSSessionKey(props) {

  const sessionKey = props.sessionKey;

  return (
    <Link to={ApiRoutes.ETHERNET.TLS.SESSIONS.DETAILS(sessionKey)}>
      <FullCopyShortenedId value={sessionKey} />
    </Link>
  )

}
