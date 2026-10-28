import React, {useContext, useEffect, useState} from "react";
import VpnService from "../../../../services/ethernet/VpnService";
import VPNTunnelDetailsTable from "./VPNTunnelDetailsTable";
import {TapContext} from "../../../../App";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../../misc/TapSelector";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import GenericConnectionStatus from "../../shared/GenericConnectionStatus";
import L4Address from "../../shared/L4Address";
import InternalAddressOnlyWrapper from "../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import numeral from "numeral";
import {formatDurationMs} from "../../../../util/Tools";
import moment from "moment";
import AutomaticL4SessionLink from "../../shared/AutomaticL4SessionLink";
import VPNType from "./VPNType";
import VPNHandshakeStatus from "./VPNHandshakeStatus";
import VPNMessageTypes from "./VPNMessageTypes";
import VpnTunnelId from "../../shared/VpnTunnelId";

const vpnService = new VpnService();

export default function VPNTunnelDetails({sessionId, l4Type, l4SessionKey, l4StartTime}) {

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [tunnel, setTunnel] = useState(null);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setTunnel(null);
    setNotFound(false);

    if (sessionId) {
      vpnService.findTunnel(sessionId, organizationId, tenantId, selectedTaps, setTunnel);
    } else {
      vpnService.findTunnelOfL4Session(
        l4Type, l4SessionKey, l4StartTime, organizationId, tenantId, selectedTaps, setTunnel, () => setNotFound(true)
      );
    }
  }, [sessionId, l4Type, l4SessionKey, l4StartTime, organizationId, tenantId, selectedTaps])

  if (notFound) {
    return <div className="alert alert-info mb-0">No tunnel details recorded for this session.</div>
  }

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
                  <dt>VPN Type</dt>
                  <dd><VPNType type={tunnel.vpn_type} /></dd>
                  <dt>Provider</dt>
                  <dd>{tunnel.provider ? tunnel.provider : <span className="text-muted">Unknown</span>}</dd>
                  <dt>Server Name</dt>
                  <dd>{tunnel.server_name ? tunnel.server_name : <span className="text-muted">n/a</span>}</dd>
                  <dt>Transport</dt>
                  <dd>{tunnel.l4_type}</dd>
                  <dt>Handshake Status</dt>
                  <dd><VPNHandshakeStatus status={tunnel.handshake_status} /></dd>
                  <dt>Connection Status</dt>
                  <dd><GenericConnectionStatus status={tunnel.connection_status} /></dd>
                  <dt>Observed Message Types</dt>
                  <dd><VPNMessageTypes messageTypes={tunnel.message_types} /></dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="VPN Connection Client &amp; Server" />

                <dl className="mb-0">
                  <dt>Client Address</dt>
                  <dd><L4Address address={tunnel.client} /></dd>
                  <dt>Client Asset</dt>
                  <dd>
                    <InternalAddressOnlyWrapper
                        address={tunnel.client}
                        inner={tunnel.client ? <EthernetMacAddress addressWithContext={tunnel.client.mac}
                                                                   withAssetLink withAssetName /> : null} />
                  </dd>
                  <dt>Server Address</dt>
                  <dd><L4Address address={tunnel.server} /></dd>
                  <dt>Server Asset</dt>
                  <dd>
                    <InternalAddressOnlyWrapper
                        address={tunnel.server}
                        inner={tunnel.server ? <EthernetMacAddress addressWithContext={tunnel.server.mac}
                                                                   withAssetLink withAssetName /> : null} />
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
                  <dt>Tunnel ID</dt>
                  <dd><VpnTunnelId tunnelId={tunnel.session_key} /></dd>
                  <dt>Tunneled Bytes</dt>
                  <dd>{numeral(tunnel.tunneled_bytes).format("0,0b")}</dd>
                  <dt>Duration</dt>
                  <dd>{formatDurationMs(tunnel.duration_ms)}</dd>
                  <dt>Established at</dt>
                  <dd>{moment(tunnel.established_at).format()}</dd>
                  <dt>Most Recent Activity</dt>
                  <dd>{moment(tunnel.most_recent_segment_time).format()} ({moment(tunnel.most_recent_segment_time).fromNow()})</dd>
                  <dt>Terminated at</dt>
                  <dd>
                    {tunnel.terminated_at ? moment(tunnel.terminated_at).format() :
                        <span className="text-muted">n/a</span>}
                  </dd>
                  <dt>First Underlying {tunnel.l4_type} Session</dt>
                  <dd>
                    <AutomaticL4SessionLink l4Type={tunnel.l4_type}
                                            sessionId={tunnel.session_key}
                                            startTime={tunnel.established_at} />
                  </dd>
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Handshake &amp; Transport Details" />

                <VPNTunnelDetailsTable details={tunnel.details} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}
