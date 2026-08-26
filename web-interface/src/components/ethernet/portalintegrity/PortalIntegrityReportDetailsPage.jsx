import React from "react";
import usePageTitle from "../../../util/UsePageTitle";
import {useParams} from "react-router-dom";

export default function PortalIntegrityReportDetailsPage() {

  usePageTitle("Portal Integrity Report Details");

  const { uuid } = useParams();

  return (
    <>
      {uuid}
    </>
  )

}