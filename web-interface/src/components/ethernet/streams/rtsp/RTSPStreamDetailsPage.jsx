import React, {useContext, useEffect, useState} from "react";
import {useParams} from "react-router-dom";
import usePageTitle from "../../../../util/UsePageTitle";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import RTSPService from "../../../../services/ethernet/RTSPService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../util/ApiRoutes";
import FullCopyShortenedId from "../../../shared/FullCopyShortenedId";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";

const rtspService = new RTSPService();

export default function RTSPStreamDetailsPage() {

  usePageTitle("RTSP Stream Details");

  const { negotiationKey } = useParams();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [session, setSession] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setSession(null);
    rtspService.findOneStream(negotiationKey, organizationId, tenantId, selectedTaps, setSession);
  }, [negotiationKey, organizationId, tenantId, selectedTaps])

  if (session === null) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row mt-3">
        <div className="col-10">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item">Streams</li>
              <li className="breadcrumb-item"><a href={ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX}>RTSP Sessions</a></li>
              <li className="breadcrumb-item active">{negotiationKey}</li>
            </ol>
          </nav>
        </div>
        <div className="col-2">
          <a href={ApiRoutes.ETHERNET.STREAMS.RTSP.INDEX} className="btn btn-primary float-end">
            Back
          </a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <h1>
            RTSP Stream {<FullCopyShortenedId value={negotiationKey} />}
          </h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
              </dl>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}