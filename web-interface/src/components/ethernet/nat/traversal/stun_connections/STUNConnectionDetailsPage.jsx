import React from "react";
import usePageTitle from "../../../../../util/UsePageTitle";
import {useParams} from "react-router-dom";

export default function STUNConnectionDetailsPage() {

  usePageTitle("STUN Transaction Details");

  const { negotiationKey } = useParams();

  return <span>{negotiationKey}</span>

}