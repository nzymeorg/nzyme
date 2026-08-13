import React from "react";
import TCPSessionLink from "./TCPSessionLink";
import UDPSessionLink from "./UDPSessionLink";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";

export default function AutomaticL4SessionLink({l4Type, sessionId, startTime}) {

  switch (l4Type) {
    case "TCP":
      return <TCPSessionLink sessionId={sessionId} startTime={startTime} />
    case "UDP":
      return <UDPSessionLink sessionId={sessionId} startTime={startTime} />
    default:
      return <FullCopyShortenedId value={sessionId} />
  }

}