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
import WebRTCSessionActiveIndicator from "../webrtc/WebRTCSessionActiveIndicator";
import L4SessionTags from "../../l4/L4SessionTags";
import numeral from "numeral";
import RTSPStreamActiveIndicator from "./RTSPStreamActiveIndicator";
import L4Address from "../../shared/L4Address";
import InternalAddressOnlyWrapper from "../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import moment from "moment";
import GenericConnectionStatus from "../../shared/GenericConnectionStatus";
import {formatDurationMs} from "../../../../util/Tools";

const rtspService = new RTSPService();

export default function RTSPStreamDetailsPage() {

  usePageTitle("RTSP Stream Details");

  const { sessionKey } = useParams();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [stream, setStream] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setStream(null);
    rtspService.findOneStream(sessionKey, organizationId, tenantId, selectedTaps, setStream);
  }, [sessionKey, organizationId, tenantId, selectedTaps])

  const flags = () => {
    if (!stream.flags || stream.flags.length === 0) {
      return <span className="text-muted">None</span>;
    }

    let flagsHumanReadable = [];
    stream.flags.forEach((f) => {
      switch (f) {
        case "UnauthenticatedStream":
          flagsHumanReadable.push("Unauthenticated Stream");
          break;
        case "BasicAuthCleartext":
          flagsHumanReadable.push("Cleartext Basic Authentication");
          break;
        case "AuthFailures":
          flagsHumanReadable.push("Authentication Failures");
          break;
        case "MediaRedirect":
          flagsHumanReadable.push("Media Redirect");
          break;
        case "PublishAttempt":
          flagsHumanReadable.push("Publish Attempt");
          break;
        case "UnusualTransport":
          flagsHumanReadable.push("Unusual Transport");
          break;
      }
    })

    return <span className="text-warning">{flagsHumanReadable.join(", ")}</span>
  }

  if (stream === null) {
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
              <li className="breadcrumb-item active">{sessionKey}</li>
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
            RTSP Stream {<FullCopyShortenedId value={sessionKey} />}
          </h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Stream Key</dt>
                <dd><FullCopyShortenedId value={sessionKey} /></dd>
                <dt>Is Active</dt>
                <dd><RTSPStreamActiveIndicator stream={stream} withText={true} /></dd>
                <dt>State</dt>
                <dd>{stream.state}</dd>
                <dt>Flags</dt>
                <dd>{flags()}</dd>
                <dt>Last Activity (Setup or Stream)</dt>
                <dd>{moment(stream.last_activity).format()} ({moment(stream.last_activity).fromNow()})</dd>
                <dt>Duration (Setup or Stream)</dt>
                <dd>{stream.duration_ms ? formatDurationMs(stream.duration_ms) : <span className="text-muted">n/a</span>}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Setup" />
              <dl className="mb-0">
                <dt>Source Address</dt>
                <dd>{stream.setup_source ? <L4Address address={stream.setup_source} /> : <span className="text-muted">n/a</span>}</dd>
                <dt>Source Asset</dt>
                <dd>
                  {stream.setup_source ?
                    <InternalAddressOnlyWrapper
                      address={stream.setup_source}
                      inner={stream.setup_source ?
                        <EthernetMacAddress addressWithContext={stream.setup_source.mac} withAssetLink withAssetName />
                        : null } />
                    : <span className="text-muted">n/a</span>}
                </dd>
                <dt>Destination Address</dt>
                <dd>{stream.setup_destination ? <L4Address address={stream.setup_destination} /> : <span className="text-muted">n/a</span>}</dd>
                <dt>Destination Asset</dt>
                <dd>
                  {stream.setup_destination ?
                    <InternalAddressOnlyWrapper
                      address={stream.setup_destination}
                      inner={stream.setup_destination ?
                        <EthernetMacAddress addressWithContext={stream.setup_destination.mac} withAssetLink withAssetName />
                        : null } />
                    : <span className="text-muted">n/a</span>}
                </dd>
                <dt>Connection Status</dt>
                <dd><GenericConnectionStatus status={stream.setup_connection_status} style="icon" /></dd>
                <dt>Established At</dt>
                <dd>{moment(stream.setup_established_at).format()} ({moment(stream.setup_established_at).fromNow()})</dd>
                <dt>Terminated At</dt>
                <dd>
                  {stream.setup_terminated_at ?
                    <span>{moment(stream.setup_terminated_at).format()} ({moment(stream.setup_terminated_at).fromNow()})</span>
                    : <span className="text-muted">n/a</span>}
                </dd>
                <dt>Last Activity</dt>
                <dd>
                  {moment(stream.setup_most_recent_segment_time).format()} ({moment(stream.setup_most_recent_segment_time).fromNow()})
                </dd>
                <dt>Duration</dt>
                <dd>
                  {moment.duration(
                    moment(stream.setup_most_recent_segment_time).diff(moment(stream.setup_established_at))
                  ).humanize()}
                </dd>
                <dt>Bytes Exchanged</dt>
                <dd>{numeral(stream.setup_bytes_exchanged).format("0b")}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Stream" />
              <dl className="mb-0">
                <dt>Type</dt>
                <dd>{stream.stream_l4_type ? stream.stream_l4_type : <span className="text-muted">n/a</span>}</dd>
                <dt>Source Address</dt>
                <dd>{stream.stream_source ? <L4Address address={stream.stream_source} /> : <span className="text-muted">n/a</span>}</dd>
                <dt>Source Asset</dt>
                <dd>
                  {stream.stream_source ?
                  <InternalAddressOnlyWrapper
                    address={stream.stream_source}
                    inner={stream.stream_source ?
                      <EthernetMacAddress addressWithContext={stream.stream_source.mac} withAssetLink withAssetName />
                      : null } />
                    : <span className="text-muted">n/a</span>}
                </dd>
                <dt>Destination Address</dt>
                <dd>{stream.stream_destination ? <L4Address address={stream.stream_destination} /> : <span className="text-muted">n/a</span>}</dd>
                <dt>Destination Asset</dt>
                <dd>
                  {stream.stream_destination ?
                    <InternalAddressOnlyWrapper
                      address={stream.stream_destination}
                      inner={stream.stream_destination ?
                        <EthernetMacAddress addressWithContext={stream.stream_destination.mac} withAssetLink withAssetName />
                        : null } />
                    : <span className="text-muted">n/a</span>}
                </dd>
                <dt>Bytes Received</dt>
                <dd>{numeral(stream.stream_bytes_rx).format("0b")}</dd>
                <dt>Bytes Transmitted</dt>
                <dd>{numeral(stream.stream_bytes_tx).format("0b")}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              MEDIA LOC: {JSON.stringify(stream.media_locator)}<br />
              REQ URI: {stream.request_uri}<br />
              CLIENT: {stream.client_agent}<br />
              SERVER: {stream.server_info}<br />
              AUTH: {stream.authentication}<br />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}