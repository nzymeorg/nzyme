import React, {useContext, useEffect, useState} from "react";
import usePageTitle from "../../../../../util/UsePageTitle";
import {useParams} from "react-router-dom";
import ApiRoutes from "../../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import {TapContext} from "../../../../../App";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../../../misc/TapSelector";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import NATService from "../../../../../services/ethernet/NATService";

const natService = new NATService();

export default function STUNConnectionDetailsPage() {

  usePageTitle("STUN Connection Details");

  const { negotiationKey } = useParams();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [connection, setConnection] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setConnection(null);
    natService.findOneSTUNConnection(negotiationKey, organizationId, tenantId, selectedTaps, setConnection);
  }, [negotiationKey, organizationId, tenantId, selectedTaps])

  if (connection == null) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-10">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><a href={ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.INDEX}>STUN Connections</a></li>
              <li className="breadcrumb-item">Connections</li>
              <li className="breadcrumb-item active" aria-current="page">{negotiationKey}</li>
            </ol>
          </nav>
        </div>
        <div className="col-2">
          <a href={ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.INDEX} className="btn btn-primary float-end">
            Back
          </a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <h1>
            STUN Connection &quot;{negotiationKey.substring(0,6)}&quot;
          </h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Source &amp; Destination" />
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Metadata" />
            </div>
          </div>
        </div>
      </div>

    </React.Fragment>
  )

}