import React, {useEffect, useState} from "react";
import {useParams} from "react-router-dom";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../misc/LoadingSpinner";
import AssetsService from "../../../services/ethernet/AssetsService";
import SectionMenuBar from "../../shared/SectionMenuBar";
import {ASSETS_MENU_ITEMS} from "./AssetsMenuItems";
import ApiRoutes from "../../../util/ApiRoutes";
import moment from "moment/moment";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import AssetHostnamesTable from "./AssetHostnamesTable";
import {Presets} from "../../shared/timerange/TimeRange";
import AssetIpAddressesTable from "./AssetIpAddressesTable";
import {toast} from "react-toastify";
import AssetSourceProtocols from "./AssetSourceProtocols";
import AssetDetailsDNSTransactions from "./AssetDetailsDNSTransactions";
import AssetActiveIndicator from "./AssetActiveIndicator";
import AssetDetailsSSHSessions from "./AssetDetailsSSHSessions";
import ComponentCycle from "../../shared/ComponentCycle";
import AssetDetailsSOCKSTunnels from "./AssetDetailsSOCKSTunnels";
import AssetDetailsL4Sessions from "./AssetDetailsL4Sessions";
import AssetDetailsL4Histograms from "./AssetDetailsL4Histograms";
import AssetDetailsL4Ports from "./AssetDetailsL4Ports";
import AssetDetailsAssetName from "./AssetDetailsAssetName";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import AssetDetailsNTPTransactions from "./AssetDetailsNTPTransactions";
import usePageTitle from "../../../util/UsePageTitle";

const assetsService = new AssetsService();

export default function AssetDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [asset, setAsset] = useState(null);

  const [hostnames, setHostnames] = useState(null);
  const [hostnamesTimeRange, setHostnamesTimeRange] = useState(Presets.ALL_TIME);
  const [hostnamesOrderColumn, setHostnamesOrderColumn] = useState("last_seen");
  const [hostnamesOrderDirection, setHostnamesOrderDirection] = useState("DESC");
  const hostnamesPerPage = 10;
  const [hostnamesPage, setHostnamesPage] = useState(1);

  const [ipAddresses, setIpAddresses] = useState(null);
  const [ipAddressesTimeRange, setIpAddressesTimeRange] = useState(Presets.ALL_TIME);
  const [ipAddressesOrderColumn, setIpAddressesOrderColumn] = useState("last_seen");
  const [ipAddressesOrderDirection, setIpAddressesOrderDirection] = useState("DESC");
  const ipAddressesPerPage = 10;
  const [ipAddressesPage, setIpAddressesPage] = useState(1);

  const [revision, setRevision] = useState(new Date());

  usePageTitle(asset ? `Details: ${asset.mac.address}` : "Ethernet Asset Details");

  useEffect(() => {
    setAsset(null);
    assetsService.findAsset(uuid, organizationId, tenantId, setAsset);
  }, [uuid, organizationId, tenantId, revision]);

  useEffect(() => {
    setHostnames(null);
    assetsService.findAssetHostnames(
        uuid,
        organizationId,
        tenantId,
        hostnamesTimeRange,
        hostnamesOrderColumn,
        hostnamesOrderDirection,
        hostnamesPerPage,
        (hostnamesPage-1)*hostnamesPerPage,
        setHostnames
    );
  }, [organizationId, tenantId, hostnamesTimeRange, hostnamesOrderColumn, hostnamesOrderDirection, hostnamesPage, revision])

  useEffect(() => {
    setIpAddresses(null);
    assetsService.findAssetIpAddresses(
        uuid,
        organizationId,
        tenantId,
        ipAddressesTimeRange,
        ipAddressesOrderColumn,
        ipAddressesOrderDirection,
        ipAddressesPerPage,
        (ipAddressesPage-1)*ipAddressesPerPage,
        setIpAddresses
    );
  }, [organizationId, tenantId, ipAddressesTimeRange, ipAddressesOrderColumn, ipAddressesOrderDirection, ipAddressesPage, revision])

  const onDeleteHostname = (e, id) => {
    e.preventDefault();

    if (!confirm("Really delete hostname?")) {
      return;
    }

    assetsService.deleteAssetHostname(id, uuid, organizationId, tenantId, () => {
      setRevision(new Date());
      toast.success("Hostname deleted.");
    })
  }

  const onDeleteIpAddress = (e, id) => {
    e.preventDefault();

    if (!confirm("Really delete IP address?")) {
      return;
    }

    assetsService.deleteAssetIpAddress(id, uuid, organizationId, tenantId, () => {
      setRevision(new Date());
      toast.success("IP address deleted.");
    })
  }

  if (!asset) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-md-12">
            <SectionMenuBar items={ASSETS_MENU_ITEMS}
                            activeRoute={ApiRoutes.ETHERNET.ASSETS.INDEX} />
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-8">
            <h1>
              <AssetActiveIndicator active={asset.is_active} />{' '}
              Asset <span className="machine-data">{asset.mac.address}</span>{' '}
              {asset.name ? <span className="context-name context-name-asset">{asset.name}</span>
                  : null}
            </h1>
          </div>

          <div className="col-md-4 text-end">
            <a href={ApiRoutes.ETHERNET.ASSETS.INDEX} className="btn btn-primary">Back to Assets List</a>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Details" />

                <dl className="mb-0">
                  <dt>MAC Address</dt>
                  <dd>
                    <EthernetMacAddress addressWithContext={asset.mac}
                                        hideActiveIndicator={true} />
                  </dd>
                  <dt>Name</dt>
                  <dd><AssetDetailsAssetName asset={asset} setRevision={setRevision} /></dd>
                  <dt>OUI</dt>
                  <dd>{asset.oui ? asset.oui : <span className="text-muted">n/a</span>}</dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-md-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Metadata" />

                <p className="text-muted">Asset metadata is never automatically retention cleaned.</p>

                <dl className="mb-0">
                  <dt>First Seen</dt>
                  <dd>
                    {moment(asset.first_seen).format("YYYY-MM-DDTHH:mm:ss.SSSZ")} ({moment(asset.first_seen).fromNow()})
                  </dd>
                  <dt>Last Seen</dt>
                  <dd>
                    {moment(asset.last_seen).format("YYYY-MM-DDTHH:mm:ss.SSSZ")} ({moment(asset.last_seen).fromNow()})
                  </dd>
                  <dt>Source Protocols</dt>
                  <dd><AssetSourceProtocols asset={asset} /></dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="row mt-3">
            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="Hostnames"
                                         timeRange={hostnamesTimeRange}
                                         setTimeRange={setHostnamesTimeRange} />

                  <AssetHostnamesTable hostnames={hostnames}
                                       page={hostnamesPage}
                                       setPage={setHostnamesPage}
                                       perPage={hostnamesPerPage}
                                       setOrderColumn={setHostnamesOrderColumn}
                                       orderColumn={hostnamesOrderColumn}
                                       setOrderDirection={setHostnamesOrderDirection}
                                       orderDirection={hostnamesOrderDirection}
                                       onDeleteHostname={onDeleteHostname} />
                </div>
              </div>
            </div>

            <div className="col-md-6">
              <div className="card">
                <div className="card-body">
                  <CardTitleWithControls title="IP Addresses"
                                         timeRange={ipAddressesTimeRange}
                                         setTimeRange={setIpAddressesTimeRange} />

                  <AssetIpAddressesTable addresses={ipAddresses}
                                         page={ipAddressesPage}
                                         setPage={setIpAddressesPage}
                                         perPage={ipAddressesPerPage}
                                         setOrderColumn={setIpAddressesOrderColumn}
                                         orderColumn={ipAddressesOrderColumn}
                                         setOrderDirection={setIpAddressesOrderDirection}
                                         orderDirection={ipAddressesOrderDirection}
                                         onDeleteIpAddress={onDeleteIpAddress} />
                </div>
              </div>
            </div>
          </div>
        </div>

        <AssetDetailsDNSTransactions asset={asset}/>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Remote Access" />

                <ComponentCycle components={[
                    {name: "Outbound SSH Sessions", element:
                          <AssetDetailsSSHSessions title="Outbound SSH Sessions"
                                                   filters={{
                                                     "client_mac": [{
                                                       field: "client_mac",
                                                       operator: "equals",
                                                       value: asset.mac.address,
                                                     }]}} />},
                  {name: "Inbound SSH Sessions", element:
                        <AssetDetailsSSHSessions title="Inbound SSH Sessions"
                                                 filters={{
                                                   "server_mac": [{
                                                     field: "server_mac",
                                                     operator: "equals",
                                                     value: asset.mac.address,
                                                   }]}} />},
                ]} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Tunnels"  />

                <ComponentCycle components={[
                  {name: "Outbound SOCKS Tunnels", element:
                        <AssetDetailsSOCKSTunnels title="Outbound SOCKS Tunnels"
                                                  filters={{
                                                    "client_mac": [{
                                                      field: "client_mac",
                                                      operator: "equals",
                                                      value: asset.mac.address,
                                                    }]}} />},
                  {name: "Inbound SOCKS Tunnels", element:
                        <AssetDetailsSOCKSTunnels title="Inbound SOCKS Tunnels"
                                                  filters={{
                                                    "server_mac": [{
                                                      field: "server_mac",
                                                      operator: "equals",
                                                      value: asset.mac.address,
                                                    }]}} />},
                ]} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="TCP/UDP Sessions"  />

                <ComponentCycle components={[
                  {name: "Destinations", element:
                        <AssetDetailsL4Histograms title="Destinations"
                                                  filters={{
                                                    "source_mac": [{
                                                      field: "source_mac",
                                                      operator: "equals",
                                                      value: asset.mac.address,
                                                    }]}} />},
                  {name: "Ports", element:
                        <AssetDetailsL4Ports title="Ports"
                                                  filters={{
                                                    "source_mac": [{
                                                      field: "source_mac",
                                                      operator: "equals",
                                                      value: asset.mac.address,
                                                    }]}} />},
                  {name: "Outbound Sessions", element:
                      <AssetDetailsL4Sessions title="Outbound Sessions"
                                              filters={{
                                                "source_mac": [{
                                                  field: "source_mac",
                                                  operator: "equals",
                                                  value: asset.mac.address,
                                                }]}} />},
                  {name: "Inbound Sessions", element:
                      <AssetDetailsL4Sessions title="Inbound Sessions"
                                              filters={{
                                                "destination_mac": [{
                                                  field: "destination_mac",
                                                  operator: "equals",
                                                  value: asset.mac.address,
                                                }]}} />},
                ]} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Time"  />

                <ComponentCycle components={[
                  {name: "NTP Client Requests", element:
                      <AssetDetailsNTPTransactions title="NTP Client Requests"
                                                   filters={{
                                                     "client_mac": [{
                                                       field: "client_mac",
                                                       operator: "equals",
                                                       value: asset.mac.address,
                                                     }]}} />},
                  {name: "NTP Server Responses", element:
                      <AssetDetailsNTPTransactions title="NTP Server Responses"
                                                   filters={{
                                                     "server_mac": [{
                                                       field: "server_mac",
                                                       operator: "equals",
                                                       value: asset.mac.address,
                                                     }]}} />},
                ]} />
              </div>
            </div>
          </div>
        </div>

      </React.Fragment>
  )

}