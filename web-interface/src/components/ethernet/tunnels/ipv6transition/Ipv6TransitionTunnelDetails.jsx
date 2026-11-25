import React, {useContext, useEffect, useState} from "react";
import Ipv6TransitionService from "../../../../services/ethernet/Ipv6TransitionService";
import Ipv6TransitionTunnelDetailsTable from "./Ipv6TransitionTunnelDetailsTable";
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
import Ipv6TransitionTunnelType from "./Ipv6TransitionTunnelType";
import Ipv6TransitionTunnelId from "./Ipv6TransitionTunnelId";

const ipv6TransitionService = new Ipv6TransitionService();

export default function Ipv6TransitionTunnelDetails({sessionId, l4Type, l4SessionKey, l4StartTime}) {

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
      ipv6TransitionService.findTunnel(sessionId, organizationId, tenantId, selectedTaps, setTunnel);
    } else {
      ipv6TransitionService.findTunnelOfL4Session(
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

  const directlyInIp = tunnel.transport === "IP";

  return (
      <React.Fragment>
        <div className="row mt-3">
          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Details" />

                <dl className="mb-0">
                  <dt>Tunnel Type</dt>
                  <dd><Ipv6TransitionTunnelType type={tunnel.tunnel_type} /></dd>
                  <dt>Transport</dt>
                  <dd>
                    {directlyInIp ? <span title="The tunnel is carried directly in IP, as protocol 41. It has no ports.">IP</span> : tunnel.transport}
                  </dd>
                  <dt>Status</dt>
                  <dd><GenericConnectionStatus status={tunnel.connection_status} /></dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-4">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Tunnel Client &amp; Server" />

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
                  <dd><L4Address full={true} address={tunnel.server} /></dd>
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
                  <dd><Ipv6TransitionTunnelId tunnelId={tunnel.session_key} /></dd>
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
                  {directlyInIp ? null :
                    <React.Fragment>
                      <dt>First Underlying {tunnel.transport} Session</dt>
                      <dd>
                        <AutomaticL4SessionLink l4Type={tunnel.transport}
                                                sessionId={tunnel.session_key}
                                                startTime={tunnel.established_at} />
                      </dd>
                    </React.Fragment>}
                </dl>
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Tunnel Details" />

                <Ipv6TransitionTunnelDetailsTable details={tunnel.details} />
              </div>
            </div>
          </div>
        </div>
      </React.Fragment>
  )

}
