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

export default function IcmpTracerouteDetailsPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();
  const [traceroute, setTraceroute] = useState(null);
  const [notFound, setNotFound] = useState(false);

  usePageTitle(traceroute ? "Traceroute Details" : "Traceroute");

  useEffect(() => {
    setTraceroute(null);
    setNotFound(false);
    icmpService.findTraceroute(uuid, organizationId, tenantId, setTraceroute, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Traceroute not found. <Link to={ApiRoutes.ETHERNET.ICMP.TRACEROUTES.INDEX}>Back to Traceroutes</Link>
      </div>
    )
  }

  if (!traceroute) {
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
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ICMP.TRACEROUTES.INDEX}>Traceroutes</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Traceroute</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-9"><h1>Traceroute</h1></div>
        <div className="col-3 text-end">
          <Link to={ApiRoutes.ETHERNET.ICMP.TRACEROUTES.INDEX} className="btn btn-secondary">Back</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <div className="card"><div className="card-body">
            <CardTitleWithControls title="Traceroute" />
            <dl className="mb-0">
              <dt>Tracing Host</dt>
              <dd>{addr(traceroute.source_address_data, traceroute.source_address)}</dd>
              <dt>Tracing Host MAC</dt>
              <dd>{traceroute.source_mac ? <EthernetMacAddress addressWithContext={traceroute.source_mac} withAssetLink withAssetName /> : <span className="text-muted">n/a</span>}</dd>
              <dt>Destination</dt>
              <dd>{addr(traceroute.destination_address_data, traceroute.destination_address)}</dd>
              <dt>Probe Protocol</dt>
              <dd className="mb-0">{traceroute.protocol_name ? traceroute.protocol_name : <span className="text-muted">n/a</span>}</dd>
            </dl>
          </div></div>
        </div>

        <div className="col-6">
          <div className="card"><div className="card-body">
            <CardTitleWithControls title="Path" />
            <dl className="mb-0">
              <dt>Destination Reached</dt>
              <dd>{traceroute.destination_reached ? "Yes" : <span className="text-muted">No</span>}</dd>
              <dt>Routers Answering ({traceroute.routers.length})</dt>
              <dd className="machine-data">
                {traceroute.routers.length === 0 ? <span className="text-muted">None</span> : traceroute.routers.join(", ")}
              </dd>
              <dt>Packets</dt>
              <dd>{numeral(traceroute.packet_count).format("0,0")}</dd>
              <dt>First Seen</dt>
              <dd title={moment(traceroute.first_seen).format()}>{moment(traceroute.first_seen).fromNow()}</dd>
              <dt>Last Seen</dt>
              <dd className="mb-0" title={moment(traceroute.last_seen).format()}>{moment(traceroute.last_seen).fromNow()}</dd>
            </dl>
          </div></div>
        </div>
      </div>
    </React.Fragment>
  )

}
