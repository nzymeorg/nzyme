import React, {useContext, useEffect, useState} from "react";
import usePageTitle from "../../../../../util/UsePageTitle";
import {useParams} from "react-router-dom";
import ApiRoutes from "../../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../../shared/CardTitleWithControls";
import {TapContext} from "../../../../../App";
import useSelectedTenant from "../../../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../../../misc/TapSelector";
import LoadingSpinner from "../../../../misc/LoadingSpinner";
import NATService from "../../../../../services/ethernet/NATService";
import STUNConnectionSuccessIndicator from "./STUNConnectionSuccessIndicator";
import STUNConnectionActiveIndicator from "./STUNConnectionActiveIndicator";

import numeral from "numeral";
import L4Address from "../../../shared/L4Address";
import InternalAddressOnlyWrapper from "../../../shared/InternalAddressOnlyWrapper";
import EthernetMacAddress from "../../../../shared/context/macs/EthernetMacAddress";
import moment from "moment";

const natService = new NATService();

export default function STUNConnectionDetailsPage() {

  usePageTitle("STUN Connection Details");

  const { negotiationKey } = useParams();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [connection, setConnection] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setConnection(null);
    natService.findOneSTUNConnection(negotiationKey, organizationId, tenantId, selectedTaps, setConnection);
  }, [negotiationKey, organizationId, tenantId, selectedTaps])

  if (connection == null) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-10">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><a href={ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.INDEX}>STUN Connections</a></li>
              <li className="breadcrumb-item">Connections</li>
              <li className="breadcrumb-item active" aria-current="page">{negotiationKey}</li>
            </ol>
          </nav>
        </div>
        <div className="col-2">
          <a href={ApiRoutes.ETHERNET.NAT.TRAVERSAL.STUN_CONNECTIONS.INDEX} className="btn btn-primary float-end">
            Back
          </a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <h1>
            STUN Connection &quot;{negotiationKey.substring(0,6)}&quot;
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
                <dd className="machine-data">{connection.negotiation_key}</dd>
                <dt>Transport</dt>
                <dd>{connection.transport}</dd>
                <dt>Is TURN</dt>
                <dd>{connection.is_turn ? "True" : "False"}</dd>
                <dt>Is Active</dt>
                <dd><STUNConnectionActiveIndicator active={connection.is_active} withText={true} /></dd>
                <dt>Successful</dt>
                <dd><STUNConnectionSuccessIndicator successful={connection.successful} withText={true} /></dd>
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
                <dd>{numeral(connection.bytes_exchanged).format("0b")}</dd>
                <dt>Client Address</dt>
                <dd><L4Address address={connection.source} hidePort={true}/></dd>
                <dt>Client Asset</dt>
                <dd>
                  <InternalAddressOnlyWrapper
                    address={connection.source}
                    inner={connection.source ?
                      <EthernetMacAddress addressWithContext={connection.source.mac} withAssetLink withAssetName />
                      : null } />
                </dd>
                <dt>Destination Address</dt>
                <dd><L4Address address={connection.destination} hidePort={true}/></dd>
                <dt>Destination Asset</dt>
                <dd>
                  <InternalAddressOnlyWrapper
                    address={connection.destination}
                    inner={connection.destination ?
                      <EthernetMacAddress addressWithContext={connection.destination.mac} withAssetLink withAssetName />
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
                  {moment(connection.first_seen).format()} ({moment(connection.first_seen).fromNow()})
                </dd>
                <dt>Last Activity</dt>
                <dd>
                  {moment(connection.last_activity).format()} ({moment(connection.last_activity).fromNow()})
                </dd>
                <dt>Duration</dt>
                <dd>
                  {moment.duration(
                    moment(connection.last_activity).diff(moment(connection.first_seen))
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
              <CardTitleWithControls title="Flows" />
            </div>
          </div>
        </div>
      </div>

    </React.Fragment>
  )

}