import React, {useContext, useEffect, useState} from "react";
import SocksService from "../../../../services/ethernet/SocksService";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import GenericConnectionStatus from "../../shared/GenericConnectionStatus";
import L4Address from "../../shared/L4Address";
import InternalAddressOnlyWrapper from "../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import SOCKSTunnelDestination from "./SOCKSTunnelDestination";
import numeral from "numeral";
import {formatDurationMs} from "../../../../util/Tools";
import moment from "moment";
import TCPSessionLink from "../../shared/TCPSessionLink";

const socksService = new SocksService();

export default function SOCKSTunnelDetails({sessionId}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [tunnel, setTunnel] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setTunnel(null);
    socksService.findTunnel(sessionId, organizationId, tenantId, selectedTaps, setTunnel);
  }, [sessionId, organizationId, tenantId])

  if (tunnel == null) {
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
                  <dt>SOCKS Type</dt>
                  <dd>{tunnel.socks_type}</dd>
                  <dt>Authentication Status</dt>
                  <dd>{tunnel.authentication_status}</dd>
                  <dt>Handshake Status</dt>
                  <dd>{tunnel.handshake_status}</dd>
                  <dt>Connection Status</dt>
                  <dd><GenericConnectionStatus status={tunnel.connection_status} /></dd>
                  <dt>Username</dt>
                  <dd>{tunnel.username ? tunnel.username : <span className="text-muted">n/a</span>}</dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Tunnel Source &amp; Destination" />

                <dl className="mb-0">
                  <dt>Client Address</dt>
                  <dd><L4Address full={true} address={tunnel.client} /></dd>
                  <dt>Client Asset</dt>
                  <dd>
                    <InternalAddressOnlyWrapper
                        address={tunnel.client}
                        inner={tunnel.client ? <EthernetMacAddress addressWithContext={tunnel.client.mac}
                                                                   withAssetLink withAssetName /> : null} />
                  </dd>
                  <dt>Server Address</dt>
                  <dd><SOCKSTunnelDestination tunnel={tunnel} /></dd>
                  <dt>Server Asset</dt>
                  <dd>
                    <InternalAddressOnlyWrapper
                        address={tunnel.socks_server}
                        inner={tunnel.socks_server ? <EthernetMacAddress addressWithContext={tunnel.socks_server.mac}
                                                                         withAssetLink withAssetName /> : null} />
                  </dd>
                  <dt>Tunnel Destination</dt>
                  <dt><SOCKSTunnelDestination tunnel={tunnel} /></dt>
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
                  <dd>{numeral(tunnel.tunneled_bytes).format("0,0b")}</dd>
                  <dt>Duration</dt>
                  <dd>{formatDurationMs(tunnel.duration_ms)}</dd>
                  <dt>Established at</dt>
                  <dd>{moment(tunnel.established_at).format()}</dd>
                  <dt>Terminated at</dt>
                  <dd>
                    {tunnel.terminated_at ? moment(tunnel.terminated_at).format() :
                        <span className="text-muted">n/a</span>}
                  </dd>
                  <dt>Underlying TCP Session</dt>
                  <dd><TCPSessionLink sessionId={tunnel.tcp_session_key} startTime={tunnel.established_at} /></dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
  )

}