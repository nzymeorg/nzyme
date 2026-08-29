import React from "react";
import {useParams} from "react-router-dom";
import usePageTitle from "../../../../util/UsePageTitle";

export default function RTSPStreamDetailsPage() {

  usePageTitle("RTSP Stream Details");

  const { sessionId } = useParams();

  return (
    <>
      {sessionId}
    </>
  )

}