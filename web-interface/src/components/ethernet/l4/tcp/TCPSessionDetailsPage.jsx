import {useParams} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import React, {useContext, useEffect, useState} from "react";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import L4Service from "../../../../services/ethernet/L4Service";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import {TapContext} from "../../../../App";
import L4SessionDetails from "../L4SessionDetails";
import usePageTitle from "../../../../util/UsePageTitle";

const l4Service = new L4Service();

export default function TCPSessionDetailsPage() {

  const {sessionKey} = useParams();
  const {startTime} = useParams();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;
  const [organizationId, tenantId] = useSelectedTenant();

  const [session, setSession] = useState(null);

  usePageTitle(session ? `TCP Session: ${session.session_key}` : "TCP Session Details");

  useEffect(() => {
    setSession(null);
    l4Service.findSession(sessionKey, "TCP", startTime, organizationId, tenantId, selectedTaps, setSession);
  }, [sessionKey, startTime, organizationId, tenantId]);

  if (!session) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><a href={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</a></li>
                <li className="breadcrumb-item">TCP/UDP</li>
                <li className="breadcrumb-item"><a href={ApiRoutes.ETHERNET.L4.OVERVIEW}>Sessions</a></li>
                <li className="breadcrumb-item">TCP</li>
                <li className="breadcrumb-item active" aria-current="page">{sessionKey}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <h1>
              TCP Session &quot;{sessionKey}&quot;
            </h1>
          </div>
        </div>

        <L4SessionDetails type="TCP" session={session} />

      </React.Fragment>
  )

}