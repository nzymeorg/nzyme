import {Link} from "react-router-dom";
import React from "react";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";
import ApiRoutes from "../../../util/ApiRoutes";

export default function SSHSessionKey(props) {

  const sessionKey = props.sessionKey;

  return (
    <Link to={ApiRoutes.ETHERNET.REMOTE.SSH.SESSION_DETAILS(sessionKey)}>
      <FullCopyShortenedId value={sessionKey} />
    </Link>
  )

}