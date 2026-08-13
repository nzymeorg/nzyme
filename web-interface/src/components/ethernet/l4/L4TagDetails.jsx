import React from "react";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import SSHSessionDetails from "../remote/ssh/SSHSessionDetails";
import ApiRoutes from "../../../util/ApiRoutes";
import SOCKSTunnelDetails from "../tunnels/socks/SOCKSTunnelDetails";
import NTPTransactionDetails from "../time/ntp/NTPTransactionDetails";
import STUNSessionDetails from "../nat/stun/STUNSessionDetails";

export default function L4TagDetails({session}) {

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

  const stun = () => {
    if (session.tags.includes("STUN")) {
      return (
        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body card-container">
                <CardTitleWithControls title="Encapsulated STUN Session" />

                <STUNSessionDetails sessionId={session.session_key} />
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
        {ntp()}
        {stun()}
      </React.Fragment>
  )

}