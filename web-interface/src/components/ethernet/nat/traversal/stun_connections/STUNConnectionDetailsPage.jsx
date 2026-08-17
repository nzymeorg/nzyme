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
import L4AddressList from "../../../shared/L4AddressList";
import FullCopyShortenedId from "../../../../shared/FullCopyShortenedId";

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

  const flows = () => {
    if (connection.flows === null || connection.flows.length === 0) {
      return <div className="alert alert-info mb-0">No Negotiation Flows recorded.</div>
    }

    return (
      <table className="table table-sm table-hover table-striped mb-4 mt-3">
        <thead>
        <tr>
          <th><i className="fa fa-regular fa-circle-check" /></th>
          <th className="hide-narrow">Source MAC</th>
          <th>Source Address</th>
          <th className="hide-narrow">Destination MAC</th>
          <th>Destination Address</th>
          <th title="Mapped Addresses">Mapped</th>
          <th title="Peer Addresses">Peer</th>
          <th title="Relayed Addresses">Relayed</th>
          <th>Bytes</th>
        </tr>
        </thead>
        <tbody>
        {connection.flows.map((f, i) => {
          return (
            <tr key={i}>
              <td><STUNConnectionSuccessIndicator successful={f.successful} /></td>
              <td className="hide-narrow">
                <InternalAddressOnlyWrapper
                  address={f.source}
                  inner={f.source ? <EthernetMacAddress addressWithContext={f.source.mac} withAssetLink withAssetName /> : null} />
              </td>
              <td>
                <L4Address address={f.source} hidePort={true} />
              </td>
              <td className="hide-narrow">
                <InternalAddressOnlyWrapper
                  address={f.destination}
                  inner={f.destination ? <EthernetMacAddress addressWithContext={f.destination.mac} withAssetLink withAssetName /> : null} />
              </td>
              <td>
                <L4Address address={f.destination} hidePort={true} />
              </td>
              <td><L4AddressList addresses={f.mapped_addresses} count={5} asList={true} /></td>
              <td><L4AddressList addresses={f.peer_addresses} count={5} asList={true} /></td>
              <td><L4AddressList addresses={f.relayed_addresses} count={5} asList={true} /></td>
              <td>{f.bytes_exchanged === null ? <span className="text-muted">n/a</span> : numeral(f.bytes_exchanged).format("0b")}</td>
            </tr>
          )
        })}
        </tbody>
      </table>
    )
  }

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
            STUN Connection {<FullCopyShortenedId value={negotiationKey} />}
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
        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Mapped Addresses" />

              <L4AddressList addresses={connection.mapped_addresses} count={15} asList={true} />
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Peer Addresses" />

              <L4AddressList addresses={connection.peer_addresses} count={15} asList={true} />
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Relayed Addresses" />

              <L4AddressList addresses={connection.relayed_addresses} count={15} asList={true} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Negotiation Flows" />

              {flows()}
            </div>
          </div>
        </div>
      </div>

    </React.Fragment>
  )

}