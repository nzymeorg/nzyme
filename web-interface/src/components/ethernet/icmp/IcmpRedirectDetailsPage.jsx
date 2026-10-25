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
import L4AddressList from "../shared/L4AddressList";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import IcmpService from "../../../services/ethernet/IcmpService";

const icmpService = new IcmpService();

export default function IcmpRedirectDetailsPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();
  const [redirect, setRedirect] = useState(null);
  const [notFound, setNotFound] = useState(false);

  usePageTitle(redirect ? "ICMP Redirect Details" : "ICMP Redirect");

  useEffect(() => {
    setRedirect(null);
    setNotFound(false);
    icmpService.findRedirect(uuid, organizationId, tenantId, setRedirect, () => setNotFound(true));
  }, [uuid, organizationId, tenantId]);

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Redirect not found. <Link to={ApiRoutes.ETHERNET.ICMP.REDIRECTS.INDEX}>Back to Redirects</Link>
      </div>
    )
  }

  if (!redirect) {
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
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ICMP.REDIRECTS.INDEX}>Redirects</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Redirect</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-9"><h1>ICMP Redirect</h1></div>
        <div className="col-3 text-end">
          <Link to={ApiRoutes.ETHERNET.ICMP.REDIRECTS.INDEX} className="btn btn-secondary">Back</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <div className="card"><div className="card-body">
            <CardTitleWithControls title="Redirect" />
            <dl className="mb-0">
              <dt>Sender</dt>
              <dd>{addr(redirect.sender_address_data, redirect.sender_address)}</dd>
              <dt>Sender MAC</dt>
              <dd>{redirect.sender_mac ? <EthernetMacAddress addressWithContext={redirect.sender_mac} withAssetLink withAssetName /> : <span className="text-muted">n/a</span>}</dd>
              <dt>Told Host</dt>
              <dd>{addr(redirect.target_address_data, redirect.target_address)}</dd>
              <dt>To Use Gateway</dt>
              <dd className="mb-0">{addr(redirect.gateway_address_data, redirect.gateway_address)}</dd>
            </dl>
          </div></div>
        </div>

        <div className="col-6">
          <div className="card"><div className="card-body">
            <CardTitleWithControls title="Activity" />
            <dl className="mb-0">
              <dt>Redirected Destinations</dt>
              <dd><L4AddressList addresses={redirect.redirected_destinations} count={10} asList /></dd>
              <dt>Packets</dt>
              <dd>{numeral(redirect.packet_count).format("0,0")}</dd>
              <dt>First Seen</dt>
              <dd title={moment(redirect.first_seen).format()}>{moment(redirect.first_seen).fromNow()}</dd>
              <dt>Last Seen</dt>
              <dd className="mb-0" title={moment(redirect.last_seen).format()}>{moment(redirect.last_seen).fromNow()}</dd>
            </dl>
          </div></div>
        </div>
      </div>
    </React.Fragment>
  )

}
