import React, {useContext, useEffect, useState} from "react";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import GenericConnectionStatus from "../../shared/GenericConnectionStatus";
import SSHVersion from "./SSHVersion";
import L4Address from "../../shared/L4Address";
import InternalAddressOnlyWrapper from "../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import numeral from "numeral";
import {formatDurationMs} from "../../../../util/Tools";
import moment from "moment/moment";
import TCPSessionLink from "../../shared/TCPSessionLink";
import SSHService from "../../../../services/ethernet/SSHService";

const sshService = new SSHService();

export default function SSHSessionDetails({ sessionId }) {

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
    sshService.findSession(sessionId, organizationId, tenantId, selectedTaps, setSession);
  }, [sessionId, organizationId, tenantId, selectedTaps])

  if (session == null) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Details" />

                <dl className="mb-0">
                  <dt>Connection Status</dt>
                  <dd><GenericConnectionStatus status={session.connection_status}/></dd>
                  <dt>Client SSH Version</dt>
                  <dd><SSHVersion version={session.client_version} /></dd>
                  <dt>Server SSH Version</dt>
                  <dd><SSHVersion version={session.server_version} /></dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Session Source &amp; Destination" />

                <dl className="mb-0">
                  <dt>Client Address</dt>
                  <dd><L4Address full={true} address={session.client} hidePort={true}/></dd>
                  <dt>Client Asset</dt>
                  <dd>
                    <InternalAddressOnlyWrapper
                        address={session.client}
                        inner={session.client ?
                            <EthernetMacAddress addressWithContext={session.client.mac} withAssetLink withAssetName />
                            : null } />
                  </dd>
                  <dt>Server Address</dt>
                  <dd><L4Address full={true} address={session.server} hidePort={true}/></dd>
                  <dt>Server Asset</dt>
                  <dd>
                    <InternalAddressOnlyWrapper
                        address={session.server}
                        inner={session.server ?
                            <EthernetMacAddress addressWithContext={session.server.mac} withAssetLink withAssetName />
                            : null} />
                  </dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Metadata" />

                <dl className="mb-0">
                  <dt>Tunneled Bytes</dt>
                  <dd>{numeral(session.tunneled_bytes).format("0,0b")}</dd>
                  <dt>Duration</dt>
                  <dd>{formatDurationMs(session.duration_ms)}</dd>
                  <dt>Established at</dt>
                  <dd>{moment(session.established_at).format()}</dd>
                  <dt>Terminated at</dt>
                  <dd>
                    {session.terminated_at ? moment(session.terminated_at).format() :
                        <span className="text-muted">n/a</span>}
                  </dd>
                  <dt>Underlying TCP Session</dt>
                  <dd><TCPSessionLink sessionId={session.tcp_session_key} startTime={session.established_at} /></dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
  )

}