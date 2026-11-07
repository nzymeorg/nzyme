import React, {useContext, useEffect, useState} from "react";
import {Link, useParams} from "react-router-dom";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../misc/LoadingSpinner";
import AssetsService from "../../../services/ethernet/AssetsService";
import ApiRoutes from "../../../util/ApiRoutes";
import moment from "moment/moment";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import AssetHostnamesTable from "./AssetHostnamesTable";
import {Presets} from "../../shared/timerange/TimeRange";
import AssetIpAddressesTable from "./AssetIpAddressesTable";
import {toast} from "react-toastify";
import AssetSourceProtocols from "./AssetSourceProtocols";
import AssetDetailsAssetName from "./AssetDetailsAssetName";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import usePageTitle from "../../../util/UsePageTitle";
import ContextNotes from "../../context/ContextNotes";
import AssetProfileSummaryStrip from "./details/AssetProfileSummaryStrip";
import AssetIdentityCard from "./details/AssetIdentityCard";
import AssetRolesCard from "./details/AssetRolesCard";
import AssetRiskSignalsCard from "./details/AssetRiskSignalsCard";
import AssetExclusionsCard from "./details/AssetExclusionsCard";
import AssetActivityCard from "./details/AssetActivityCard";
import AssetCommunicationCard from "./details/AssetCommunicationCard";
import AssetTimelineCard from "./details/AssetTimelineCard";
import AssetAlertsCard from "./details/AssetAlertsCard";
import AssetFindingsCard from "./details/AssetFindingsCard";
import AssetPivotsDrawer from "./details/AssetPivotsDrawer";
import AssetIdentityNoticesCard from "./details/AssetIdentityNoticesCard";
import {UserContext} from "../../../App";
import {userHasPermission} from "../../../util/Tools";

const assetsService = new AssetsService();

export default function AssetDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "ethernet_assets_manage");

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

  const [summary, setSummary] = useState(null);
  const [attributes, setAttributes] = useState(null);

  usePageTitle(asset ? `Details: ${asset.mac.address}` : "Ethernet Asset Details");

  useEffect(() => {
    setHostnamesPage(1);
    setIpAddressesPage(1);
  }, [uuid]);

  useEffect(() => {
    setAsset(null);
    assetsService.findAsset(uuid, organizationId, tenantId, setAsset);
  }, [uuid, organizationId, tenantId, revision]);

  useEffect(() => {
    setSummary(null);
    setAttributes(null);
    assetsService.getAssetProfileSummary(uuid, organizationId, tenantId, setSummary);
    assetsService.getAssetProfileAttributes(uuid, organizationId, tenantId, ["INBOUND_PORT", "INBOUND_PORT_PROTOCOL"], setAttributes);
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
  }, [uuid, organizationId, tenantId, hostnamesTimeRange, hostnamesOrderColumn, hostnamesOrderDirection, hostnamesPage, revision])

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
  }, [uuid, organizationId, tenantId, ipAddressesTimeRange, ipAddressesOrderColumn, ipAddressesOrderDirection, ipAddressesPage, revision])

  const refresh = () => setRevision(new Date());

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

  const description = () => {
    if (!asset.mac || !asset.mac.context || !asset.mac.context.description
      || asset.mac.context.description.trim().length === 0) {
      return <span className="text-muted">n/a</span>
    }

    return asset.mac.context.description;
  }

  const notes = () => {
    if (!asset.mac || !asset.mac.context) {
      return <div className="alert alert-info mb-0">
        This asset has no associated notes. Notes are kept in the MAC address context, which this asset does not
        have yet.
      </div>
    }

    if (!asset.mac.context.notes || asset.mac.context.notes.trim().length === 0) {
      return <div className="alert alert-info mb-0">
        This asset has no associated notes. You can add notes on the{' '}
        <Link to={ApiRoutes.CONTEXT.MAC_ADDRESSES.SHOW(asset.mac.context.uuid)}>Asset Context page</Link>.
      </div>
    }

    return <ContextNotes notes={asset.mac.context.notes} />
  }

  if (!asset) {
    return <LoadingSpinner />
  }

  return (
      <React.Fragment>
        <div className="row">
          <div className="col-12">
            <nav aria-label="breadcrumb">
              <ol className="breadcrumb">
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
                <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.INDEX}>Assets</Link></li>
                <li className="breadcrumb-item active" aria-current="page">{asset.mac.address}</li>
              </ol>
            </nav>
          </div>
        </div>

        <div className="row mt-2">
          <div className="col-8">
            <h1>
              Asset <span className="machine-data">{asset.mac.address}</span>{' '}
              {asset.name ? <span className="context-name context-name-asset">{asset.name}</span>
                  : null}
            </h1>
          </div>

          <div className="col-4 text-end">
            {asset.mac && asset.mac.context ?
              <Link to={ApiRoutes.CONTEXT.MAC_ADDRESSES.SHOW(asset.mac.context.uuid)} className="btn btn-secondary me-1">Open Asset Context</Link>
              : null}
            <Link to={ApiRoutes.ETHERNET.ASSETS.INDEX} className="btn btn-primary">Back to Assets List</Link>
          </div>
        </div>

        <AssetProfileSummaryStrip summary={summary} />

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <AssetIdentityNoticesCard asset={asset}
                                          organizationId={organizationId}
                                          tenantId={tenantId}
                                          revision={revision}
                                          onRefresh={() => setRevision(new Date())}
                                          canEdit={canManage} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Details" refreshAction={refresh} />

                <dl className="mb-0">
                  <dt>MAC Address</dt>
                  <dd>
                    <EthernetMacAddress addressWithContext={asset.mac}
                                        hideActiveIndicator={true} />
                  </dd>
                  <dt>Name</dt>
                  <dd><AssetDetailsAssetName asset={asset} setRevision={setRevision} /></dd>
                  <dt>Context Description</dt>
                  <dd>{description()}</dd>
                  <dt>OUI</dt>
                  <dd>{asset.oui ? asset.oui : <span className="text-muted">n/a</span>}</dd>
                </dl>
              </div>
            </div>
          </div>

          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Metadata" refreshAction={refresh} />

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
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Notes" refreshAction={refresh} />

                {notes()}
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="Hostnames"
                                       timeRange={hostnamesTimeRange}
                                       setTimeRange={setHostnamesTimeRange}
                                       refreshAction={refresh} />

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

          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <CardTitleWithControls title="IP Addresses"
                                       timeRange={ipAddressesTimeRange}
                                       setTimeRange={setIpAddressesTimeRange}
                                       refreshAction={refresh} />

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

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <AssetRolesCard asset={asset} organizationId={organizationId} tenantId={tenantId}
                                attributes={attributes} summary={summary} revision={revision}
                                onRefresh={refresh} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <AssetActivityCard asset={asset}
                                   organizationId={organizationId}
                                   tenantId={tenantId}
                                   revision={revision}
                                   onRefresh={refresh} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <AssetCommunicationCard asset={asset}
                                        organizationId={organizationId}
                                        tenantId={tenantId}
                                        attributes={attributes}
                                        revision={revision}
                                        onRefresh={refresh} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <AssetRiskSignalsCard asset={asset}
                                      organizationId={organizationId}
                                      tenantId={tenantId}
                                      revision={revision}
                                      onRefresh={refresh} />
              </div>
            </div>
          </div>

          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <AssetExclusionsCard asset={asset}
                                     organizationId={organizationId}
                                     tenantId={tenantId}
                                     revision={revision}
                                     onRefresh={refresh} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <AssetTimelineCard key={asset.uuid}
                                   asset={asset}
                                   organizationId={organizationId}
                                   tenantId={tenantId}
                                   revision={revision}
                                   onRefresh={refresh} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <AssetAlertsCard asset={asset}
                                 organizationId={organizationId}
                                 tenantId={tenantId}
                                 revision={revision}
                                 onRefresh={refresh} />
              </div>
            </div>
          </div>

          <div className="col-6">
            <div className="card">
              <div className="card-body">
                <AssetFindingsCard asset={asset}
                                   organizationId={organizationId}
                                   tenantId={tenantId}
                                   revision={revision}
                                   onRefresh={refresh} />
              </div>
            </div>
          </div>
        </div>

        <div className="row mt-3">
          <div className="col-12">
            <div className="card">
              <div className="card-body">
                <AssetIdentityCard attributes={attributes}
                                   onRefresh={refresh} />
              </div>
            </div>
          </div>
        </div>

        <AssetPivotsDrawer asset={asset} />

      </React.Fragment>
  )

}
