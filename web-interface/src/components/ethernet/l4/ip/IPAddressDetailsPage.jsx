import React, {useEffect, useState} from "react";
import {useParams} from "react-router-dom";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import IPAddressesService from "../../../../services/ethernet/IPAddressesService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Paginator from "../../../misc/Paginator";
import numeral from "numeral";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import {truncate} from "../../../../util/Tools";
import AssetActiveIndicator from "../../assets/AssetActiveIndicator";
import AssetHostnames from "../../assets/AssetHostnames";
import AssetIpAddresses from "../../assets/AssetIpAddresses";
import moment from "moment/moment";
import Flag from "../../../misc/Flag";
import LatLonMap from "../../../shared/LatLonMap";
import * as L from "leaflet";

const ipAddressesService = new IPAddressesService();

export default function IPAddressDetailsPage() {

  const { addressParam } = useParams()

  const [organizationId, tenantId] = useSelectedTenant();

  const [pageTitle, setPageTile] = useState("IP Details");
  const [address, setAddress] = useState();

  const [page, setPage] = useState(1);
  const perPage = 50;

  usePageTitle(pageTitle);

  const locationIcon = L.icon({
    iconUrl: window.appConfig.assetsUri + 'static/leaflet/icon-location.png',
    iconSize: [40, 52],
    iconAnchor: [20, 52],
    tooltipAnchor: [0, -52]
  });

  useEffect(() => {
    setAddress(null);

    ipAddressesService.findAddress(addressParam, organizationId, tenantId, perPage, (page-1)*perPage, setAddress);
  }, [addressParam, organizationId, tenantId, page, perPage])

  useEffect(() => {
    if (address) {
      setPageTile("IP " + address.address + " Details");
    }
  }, [address])

  const assetsTable = () => {
    if (address.assets === null || address.assets.assets.length === 0) {
      return <div className="alert alert-info mb-0">No assets are associated with this IP address.</div>
    }

    return (
      <React.Fragment>
        <p className="mb-2 mt-0">
          <strong>Total:</strong> {numeral(address.assets.total).format("0,0")}
        </p>

        <table className="table table-sm table-hover table-striped mb-4 mt-3">
          <thead>
          <tr>
            <th style={{width: 170}}>MAC Address</th>
            <th>OUI</th>
            <th>Active</th>
            <th>Name</th>
            <th>Hostname</th>
            <th>IP Address</th>
            <th>First Seen</th>
            <th>Last Seen</th>
            <th>&nbsp;</th>
          </tr>
          </thead>
          <tbody>
          {address.assets.assets.map((a, i) => {
            const l4Filters = {
              "source_mac": [{
                field: "source_mac",
                operator: "equals",
                value: a.mac.address,
              }],
              "source_address": [{
                field: "source_address",
                operator: "equals",
                value: address.address,
              }]};

            return (
              <tr key={i}>
                <td>
                  <EthernetMacAddress addressWithContext={a.mac}
                                      href={ApiRoutes.ETHERNET.ASSETS.DETAILS(a.uuid)}
                                      hideActiveIndicator={true} />
                </td>
                <td>{a.oui ? truncate(a.oui, 30, false) : <span className="text-muted">Unknown</span>}</td>
                <td><AssetActiveIndicator active={a.is_active} /></td>
                <td>{a.name ? <span className="context-name context-name-asset">{a.name}</span> : <span className="text-muted">None</span>}</td>
                <td><AssetHostnames hostnames={a.hostnames} /></td>
                <td><AssetIpAddresses addresses={a.ip_addresses} /></td>
                <td title={moment(a.first_seen).format()}>{moment(a.first_seen).fromNow()}</td>
                <td title={moment(a.last_seen).format()}>{moment(a.last_seen).fromNow()}</td>
                <td>
                  <a href={ApiRoutes.ETHERNET.L4.SESSIONS + "?filters=" + JSON.stringify(l4Filters)}>Show TCP/UDP Connections</a>
                </td>
              </tr>
            )
          })}
          </tbody>
        </table>

        <Paginator itemCount={address.assets.total} perPage={perPage} setPage={setPage} page={page} />
      </React.Fragment>
    )
  }

  const networkContextTable = () => {
    if (address.context === null || address.context.length === 0) {
      return <div className="alert alert-info mb-0">No Network Context is attached to this IP address.</div>
    }

    return (
      <table className="table table-sm table-hover table-striped mb-4 mt-3">
        <thead>
        <tr>
          <th>Network</th>
          <th>Name</th>
          <th>Description</th>
          <th>Has Notes</th>
        </tr>
        </thead>
        <tbody>
        {address.context.map((c, i) => {
          return <tr key={i}>
            <td>
              <a href={ApiRoutes.CONTEXT.NETWORKS.SHOW(c.uuid)} className="machine-data">{c.cidr}</a>
            </td>
            <td>
              {c.name ? <span className="context-name context-name-network">{c.name}</span>
                : <span className="text-muted">n/a</span> }
            </td>
            <td>{c.description ? c.description : <span className="text-muted">n/a</span> }</td>
            <td>{c.notes ? <span className="bold text-warning">Yes</span> : "No"}</td>
          </tr>
        })}
        </tbody>
      </table>
    )
  }

  if (!address) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><a href={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</a></li>
                <li className="breadcrumb-item">IP</li>
                <li className="breadcrumb-item">Addresses</li>
                <li className="breadcrumb-item active" aria-current="page">{address.address}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row">
          <div className="col-md-12">
            <h1>
              IP Address <span className="machine-data">{address.address}</span>
            </h1>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Geo Information" />

                <dl className="mb-0">
                  <dt>ASN</dt>
                  <dd>
                    {address.geo && address.geo.asn_number ? <span className="machine-data">{address.geo.asn_number}</span>
                      : <span className="text-muted">n/a</span> }{' '}
                    {address.geo && address.geo.asn_name ? <span>({address.geo.asn_name})</span>
                      : null }
                  </dd>
                  <dt>Domain</dt>
                  <dd>
                    {address.geo && address.geo.asn_domain ? <span className="machine-data">{address.geo.asn_domain}</span>
                      : <span className="text-muted">n/a</span> }
                  </dd>
                  <dt>Country</dt>
                  <dd>
                    {address.geo && address.geo.country_code ? <span><Flag code={address.geo.country_code} />&nbsp;</span> : null }
                    {address.geo && address.geo.country ? <span>{address.geo.country}</span> : <span className="text-muted">n/a</span> }
                  </dd>
                  <dt>City</dt>
                  <dd>
                    {address.geo && address.geo.city ? <span>{address.geo.city}</span>
                      : <span className="text-muted">n/a</span> }
                  </dd>
                </dl>

              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Coordinates" />

                { address.geo && address.geo.latitude && address.geo.longitude ?
                  <LatLonMap editMode={false}
                               containerHeight={190}
                               defaultZoomLevel={5}
                               icon={locationIcon}
                               latitude={address.geo.latitude}
                               longitude={address.geo.longitude} />
                  : <span className="text-muted">This IP address does not carry geo location information..</span>}
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Associated Assets" />

                <p className="text-muted">
                  The following assets were observed using this IP address as their source address in the previous 7 days.
                </p>

                {assetsTable()}

              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Network Context" />

                <p className="text-muted">
                  This IP address has the following Network Context attached to it:
                </p>

                {networkContextTable()}
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
  )

}