import React, {useContext, useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import {toast} from "react-toastify";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import ApiRoutes from "../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import usePageTitle from "../../../../util/UsePageTitle";
import {UserContext} from "../../../../App";
import {userHasPermission} from "../../../../util/Tools";
import AssetsService from "../../../../services/ethernet/AssetsService";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import AssetLinkEvidenceTable from "./AssetLinkEvidenceTable";
import {confidenceClass, linkKindDescription, linkKindTitle, linkStatus} from "./AssetIdentityHelpers";

const assetsService = new AssetsService();

export default function AssetLinkDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_assets_manage");

  const [link, setLink] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(link ? `MAC Address Change: ${link.predecessor_mac} to ${link.successor_mac}` : "MAC Address Change Details");

  useEffect(() => {
    setLink(null);
    assetsService.findAssetLink(uuid, organizationId, tenantId, setLink, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  const setStatus = (status) => (e) => {
    e.preventDefault();

    assetsService.setAssetLinkStatus(uuid, organizationId, tenantId, status, () => {
      toast.success("Link " + status.toLowerCase() + ".");
      setRevision(new Date());
    });
  }

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this link? It can be detected again while the new address is inside the lookback. " +
        "Dismiss it instead to keep it from coming back.")) {
      return;
    }

    assetsService.deleteAssetLink(uuid, organizationId, tenantId, () => {
      toast.success("Link deleted.");
      setRedirect(true);
    });
  }

  const back = ApiRoutes.ETHERNET.ASSETS.IDENTITY.TAB("links");

  if (redirect) {
    return <Navigate to={back} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Link not found. <Link to={back}>Back to MAC Address Changes</Link>
      </div>
    )
  }

  if (!link) {
    return <LoadingSpinner />
  }

  const l = link;

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.INDEX}>Assets</Link></li>
              <li className="breadcrumb-item"><Link to={back}>MAC Address Changes</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{l.predecessor_mac} to {l.successor_mac}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>
            MAC Address Change <span className="machine-data">{l.predecessor_mac}</span>{' '}
            to <span className="machine-data">{l.successor_mac}</span>
          </h1>
        </div>

        <div className="col-4 text-end">
          {canEdit ?
            <React.Fragment>
              {l.status !== "CONFIRMED" ?
                <a href="#" className="btn btn-success me-1" onClick={setStatus("CONFIRMED")}
                   title="Mark the two assets as one device">Confirm</a> : null}
              {l.status !== "DISMISSED" ?
                <a href="#" className="btn btn-warning me-1" onClick={setStatus("DISMISSED")}
                   title="The assets are not one device. The link is kept so it is not detected again.">Dismiss</a> : null}
              {l.status !== "OPEN" ?
                <a href="#" className="btn btn-secondary me-1" onClick={setStatus("OPEN")}>Reopen</a> : null}
              <a href="#" className="btn btn-danger me-1" onClick={onDelete}>Delete</a>
            </React.Fragment> : null}
          <Link to={back} className="btn btn-primary">Back</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-4">
          <div className="card h-100">
            <div className="card-body">
              <CardTitleWithControls title="Previous Address"
                                     refreshAction={() => setRevision(new Date())}
                                     helpLink="https://go.nzyme.org/ethernet-assets-identity-mac-changes" />

              <dl className="mb-0">
                <dt>MAC Address</dt>
                <dd>
                  {l.predecessor_mac_data ? <EthernetMacAddress addressWithContext={l.predecessor_mac_data} withAssetLink withAssetName />
                    : <span className="machine-data">{l.predecessor_mac}</span>}
                </dd>
                <dt>Last Seen</dt>
                <dd className="mb-0" title={moment(l.predecessor_last_seen).format()}>{moment(l.predecessor_last_seen).fromNow()}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card h-100">
            <div className="card-body">
              <CardTitleWithControls title="New Address" />

              <dl className="mb-0">
                <dt>MAC Address</dt>
                <dd>
                  {l.successor_mac_data ? <EthernetMacAddress addressWithContext={l.successor_mac_data} withAssetLink withAssetName />
                    : <span className="machine-data">{l.successor_mac}</span>}
                </dd>
                <dt>First Seen</dt>
                <dd className="mb-0" title={moment(l.successor_first_seen).format()}>{moment(l.successor_first_seen).fromNow()}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card h-100">
            <div className="card-body">
              <CardTitleWithControls title="Metadata" />

              <dl className="mb-0">
                <dt>Kind</dt>
                <dd>{linkKindTitle(l.kind)} <span className="text-muted">{linkKindDescription(l.kind)}</span></dd>
                <dt>Confidence</dt>
                <dd><span className={confidenceClass(l.confidence)}>{numeral(l.confidence).format("0,0")}</span> of 100</dd>
                <dt>Status</dt>
                <dd>{linkStatus(l.status)}</dd>
                <dt>Detected</dt>
                <dd className="mb-0" title={moment(l.detected_at).format()}>{moment(l.detected_at).fromNow()}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Evidence" />

              <AssetLinkEvidenceTable evidence={l.evidence} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
