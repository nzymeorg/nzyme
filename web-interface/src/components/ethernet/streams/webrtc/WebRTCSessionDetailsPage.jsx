import React, {useContext, useEffect, useState} from "react";
import usePageTitle from "../../../../util/UsePageTitle";
import {useParams} from "react-router-dom";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import WebRTCService from "../../../../services/ethernet/WebRTCService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../util/ApiRoutes";
import FullCopyShortenedId from "../../../shared/FullCopyShortenedId";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import WebRTCSessionActiveIndicator from "./WebRTCSessionActiveIndicator";
import numeral from "numeral";
import L4Address from "../../shared/L4Address";
import InternalAddressOnlyWrapper from "../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import L4SessionTags from "../../l4/L4SessionTags";
import moment from "moment/moment";
import WebRTCSessionsTable from "./WebRTCSessionsTable";
import WebRTCSessionsTableHead from "./WebRTCSessionsTableHead";
import WebRTCSessionsTableRow from "./WebRTCSessionsTableRow";
import STUNConnectionsTableHead from "../../nat/traversal/stun_connections/STUNConnectionsTableHead";
import STUNConnectionsTableRow from "../../nat/traversal/stun_connections/STUNConnectionsTableRow";

const webRTCService = new WebRTCService();

export default function WebRTCSessionDetailsPage() {

  usePageTitle("WebRTC Session Details");

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
    webRTCService.findOneSessions(negotiationKey, organizationId, tenantId, selectedTaps, setSession);
  }, [negotiationKey, organizationId, tenantId, selectedTaps])

  const content = (session) => {
    if (!session.has_rtp && !session.has_dtls && !session.has_audio && !session.has_video) {
      return <span className="text-muted">n/a</span>
    }

    let contentTypes = [];

    if (session.has_rtp) { contentTypes.push("RTP"); }
    if (session.has_dtls) { contentTypes.push("DTLS"); }
    if (session.has_audio) { contentTypes.push("Audio"); }
    if (session.has_video) { contentTypes.push("Video"); }

    return contentTypes.join(", ")
  }

  const subSessions = () => {
    if (!session.sub_sessions || session.sub_sessions.length <= 1) {
      return <div className="alert alert-info mb-0">This session consists of a single network flow, shown above.</div>
    }

    return (
      <table className="table table-sm table-striped">
        <thead>
        <WebRTCSessionsTableHead />
        </thead>
        <tbody>
        {session.sub_sessions.map((session, i) => {
          return <WebRTCSessionsTableRow key={i} session={session} setFilters={null} />
        })}
        </tbody>
      </table>
    )
  }

  const directionLabel = (direction) => {
    switch (direction) {
      case "CLIENT_TO_SERVER": return <span>Peer A <i className="fa fa-long-arrow-right" /> Peer B</span>;
      case "SERVER_TO_CLIENT": return <span>Peer B <i className="fa fa-long-arrow-right" /> Peer A</span>;
      default: return direction;
    }
  }

  const rtpStreams = () => {
    if (!session.rtp_streams || session.rtp_streams.length === 0) {
      return <div className="alert alert-info mb-0">This session did not contain RTP streams.</div>
    }

    return (
      <table className="table table-sm table-striped table-hover">
        <thead>
        <tr>
          <th>SSRC</th>
          <th>Media Type</th>
          <th>Direction</th>
          <th>Packets</th>
        </tr>
        </thead>
        <tbody>
        {session.rtp_streams.map((stream, i) => {
          return (
            <tr key={i}>
              <td><span className="machine-data">{stream.ssrc}</span></td>
              <td>{stream.media_kind}</td>
              <td>{directionLabel(stream.direction)}</td>
              <td>{numeral(stream.packet_count).format("0,0")}</td>
            </tr>
          )
        })}
        </tbody>
      </table>
    )
  }

  const stunNegotiation = () => {
    if (!session.stun_negotiation) {
      return <div className="alert alert-info mb-0">The related STUN negotiation could not be found.</div>
    }

    return (
      <table className="table table-sm table-striped">
        <thead>
        <STUNConnectionsTableHead />
        </thead>
        <tbody>
          <STUNConnectionsTableRow connection={session.stun_negotiation} setFilters={null} />
        </tbody>
      </table>
    )
  }

  if (session == null) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-10">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><a href={ApiRoutes.ETHERNET.STREAMS.WEBRTC.INDEX}>WebRTC Streams</a></li>
              <li className="breadcrumb-item">Sessions</li>
              <li className="breadcrumb-item active" aria-current="page">{negotiationKey}</li>
            </ol>
          </nav>
        </div>
        <div className="col-2">
          <a href={ApiRoutes.ETHERNET.STREAMS.WEBRTC.INDEX} className="btn btn-primary float-end">
            Back
          </a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <h1>
            WebRTC Session {<FullCopyShortenedId value={negotiationKey} />}
          </h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Negotiation Key</dt>
                <dd className="machine-data">{session.negotiation_key}</dd>
                <dt>Transport</dt>
                <dd>{session.transport}</dd>
                <dt>Is Active</dt>
                <dd><WebRTCSessionActiveIndicator session={session} withText={true} /></dd>
                <dt>Tags</dt>
                <dd><L4SessionTags tags={session.tags} /></dd>
                <dt>RTP Streams</dt>
                <dd>{numeral(session.stream_count).format("0,0")}</dd>
                <dt>Sub-Sessions</dt>
                <dd>{session.sub_sessions ? numeral(session.sub_sessions.length).format("0,0") : "0"}</dd>
                <dt>Content</dt>
                <dd>
                  {content(session)}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Source &amp; Destination" />

              <dl className="mb-0">
                <dt>Bytes Exchanged</dt>
                <dd>{numeral(session.bytes_exchanged).format("0b")}</dd>
                <dt>Peer A Address</dt>
                <dd><L4Address address={session.source} hidePort={true}/></dd>
                <dt>Peer A Asset</dt>
                <dd>
                  <InternalAddressOnlyWrapper
                    address={session.source}
                    inner={session.source ?
                      <EthernetMacAddress addressWithContext={session.source.mac} withAssetLink withAssetName />
                      : null } />
                </dd>
                <dt>Peer B Address</dt>
                <dd><L4Address address={session.destination} hidePort={true}/></dd>
                <dt>Peer B Asset</dt>
                <dd>
                  <InternalAddressOnlyWrapper
                    address={session.destination}
                    inner={session.destination ?
                      <EthernetMacAddress addressWithContext={session.destination.mac} withAssetLink withAssetName />
                      : null } />
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Metadata" />

              <dl className="mb-0">
                <dt>Initiated At</dt>
                <dd>
                  {moment(session.first_seen).format()} ({moment(session.first_seen).fromNow()})
                </dd>
                <dt>Last Activity</dt>
                <dd>
                  {moment(session.last_activity).format()} ({moment(session.last_activity).fromNow()})
                </dd>
                <dt>Duration</dt>
                <dd>
                  {moment.duration(
                    moment(session.last_activity).diff(moment(session.first_seen))
                  ).humanize()}
                </dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Sub-Sessions" />

              <p className="text-muted">
                A WebRTC session can span multiple underlying network flows. For example,
                separate flows for control, audio or video. Nzyme groups all flows that share the
                same STUN negotiation into a single session on the overview page, and breaks that
                session down into its individual sub-sessions here.
              </p>

              {subSessions()}
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Related STUN Negotiation" />

              {stunNegotiation()}
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="RTP Streams" />

              {rtpStreams()}
            </div>
          </div>
        </div>
      </div>

    </React.Fragment>
  )

}