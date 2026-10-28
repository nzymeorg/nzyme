import React from "react";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import SSHSessionDetails from "../remote/ssh/SSHSessionDetails";
import ApiRoutes from "../../../util/ApiRoutes";
import SOCKSTunnelDetails from "../tunnels/socks/SOCKSTunnelDetails";
import VPNTunnelDetails from "../tunnels/vpn/VPNTunnelDetails";
import NTPTransactionDetails from "../time/ntp/NTPTransactionDetails";
import STUNSessionDetails from "../nat/traversal/shared/STUNSessionDetails";
import RTSPStreamDetails from "../streams/rtsp/RTSPStreamDetails";
import TLSSessionDetails from "../tls/TLSSessionDetails";

export default function L4TagDetails({session, type}) {

  if (session.tags === null || session.tags.length === 0) {
    return null;
  }

  const ssh = () => {
    if (session.tags.includes("SSH")) {
      return (
          <div className="row mt-3">
            <div className="col-12">
              <div className="card">
                <div className="card-body card-container">
                  <CardTitleWithControls title="Encapsulated SSH Session"
                                         internalLink={ApiRoutes.ETHERNET.REMOTE.SSH.SESSION_DETAILS(session.session_key)} />

                  <SSHSessionDetails sessionId={session.session_key} />
                </div>
              </div>
            </div>
          </div>
      )
    }
  }

  const socks = () => {
    if (session.tags.includes("SOCKS")) {
      return (
          <div className="row mt-3">
            <div className="col-12">
              <div className="card">
                <div className="card-body card-container">
                  <CardTitleWithControls title="Encapsulated SOCKS Tunnel"
                                         internalLink={ApiRoutes.ETHERNET.TUNNELS.SOCKS.TUNNEL_DETAILS(session.session_key)} />

                  <SOCKSTunnelDetails sessionId={session.session_key} />
                </div>
              </div>
            </div>
          </div>
      )
    }
  }

  const vpn = () => {
    if (session.tags.includes("WireGuard") || session.tags.includes("OpenVPN") || session.tags.includes("DERP")) {
      return (
          <div className="row mt-3">
            <div className="col-12">
              <div className="card">
                <div className="card-body card-container">
                  <CardTitleWithControls title="Encapsulated VPN Connection" />

                  <VPNTunnelDetails l4Type={type}
                                    l4SessionKey={session.session_key}
                                    l4StartTime={session.start_time} />
                </div>
              </div>
            </div>
          </div>
      )
    }
  }

  const ntp = () => {
    if (session.tags.includes("NTP")) {
      return (
        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body card-container">
                <CardTitleWithControls title="Encapsulated NTP Transaction" />

                <NTPTransactionDetails transactionId={session.session_key} />
              </div>
            </div>
          </div>
        </div>
      )
    }
  }

  const tls = () => {
    if (session.tags.includes("TLS")) {
      return (
        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body card-container">
                <CardTitleWithControls title="Encapsulated TLS Session"
                                       internalLink={ApiRoutes.ETHERNET.TLS.SESSIONS.DETAILS(session.session_key)} />

                <TLSSessionDetails sessionKey={session.session_key} />
              </div>
            </div>
          </div>
        </div>
      )
    }
  }

  const rtsp = () => {
    if (session.tags.includes("RTSP")) {
      return (
        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body card-container">
                <CardTitleWithControls title="Encapsulated RTSP Setup and/or Stream" />

                <RTSPStreamDetails sessionKey={session.session_key} />
              </div>
            </div>
          </div>
        </div>
      )
    }
  }

  return (
      <React.Fragment>
        {ssh()}
        {socks()}
        {vpn()}
        {tls()}
        {ntp()}
        {rtsp()}
      </React.Fragment>
  )

}