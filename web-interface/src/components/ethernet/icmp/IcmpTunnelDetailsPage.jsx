import React, {useEffect, useState} from "react";
import {Link, useParams} from "react-router-dom";
import moment from "moment";
import numeral from "numeral";
import ApiRoutes from "../../../util/ApiRoutes";
import usePageTitle from "../../../util/UsePageTitle";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../misc/LoadingSpinner";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import L4Address from "../shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import IcmpService from "../../../services/ethernet/IcmpService";

const icmpService = new IcmpService();

export default function IcmpTunnelDetailsPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();
  const [tunnel, setTunnel] = useState(null);
  const [notFound, setNotFound] = useState(false);

  usePageTitle(tunnel ? "ICMP Tunnel Details" : "ICMP Tunnel");

  useEffect(() => {
    setTunnel(null);
    setNotFound(false);
    icmpService.findTunnel(uuid, organizationId, tenantId, setTunnel, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Tunnel not found. <Link to={ApiRoutes.ETHERNET.ICMP.TUNNELS.INDEX}>Back to Tunnels</Link>
      </div>
    )
  }

  if (!tunnel) {
    return <LoadingSpinner />
  }

  const addr = (data, plain) => data ? <L4Address address={data} hidePort withAssetName /> : <span className="machine-data">{plain}</span>;

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ICMP.OVERVIEW}>ICMP</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ICMP.TUNNELS.INDEX}>Tunnels</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Tunnel</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-9"><h1>Suspected ICMP Tunnel</h1></div>
        <div className="col-3 text-end">
          <Link to={ApiRoutes.ETHERNET.ICMP.TUNNELS.INDEX} className="btn btn-secondary">Back</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <div className="card"><div className="card-body">
            <CardTitleWithControls title="Tunnel" />
            <dl className="mb-0">
              <dt>Source</dt>
              <dd>{addr(tunnel.source_address_data, tunnel.source_address)}</dd>
              <dt>Source MAC</dt>
              <dd>{tunnel.source_mac ? <EthernetMacAddress addressWithContext={tunnel.source_mac} withAssetLink withAssetName /> : <span className="text-muted">n/a</span>}</dd>
              <dt>Peer</dt>
              <dd className="mb-0">{addr(tunnel.destination_address_data, tunnel.destination_address)}</dd>
            </dl>
          </div></div>
        </div>

        <div className="col-6">
          <div className="card"><div className="card-body">
            <CardTitleWithControls title="Activity" />
            <dl className="mb-0">
              <dt>Max Echo Payload</dt>
              <dd>{numeral(tunnel.max_payload_bytes).format("0,0")} bytes</dd>
              <dt>Packets</dt>
              <dd>{numeral(tunnel.packet_count).format("0,0")}</dd>
              <dt>First Seen</dt>
              <dd title={moment(tunnel.first_seen).format()}>{moment(tunnel.first_seen).fromNow()}</dd>
              <dt>Last Seen</dt>
              <dd className="mb-0" title={moment(tunnel.last_seen).format()}>{moment(tunnel.last_seen).fromNow()}</dd>
            </dl>
          </div></div>
        </div>
      </div>
    </React.Fragment>
  )

}
