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
import TcpFingerprint from "./TcpFingerprint";
import {changeSignalDescription, changeSignalTitle} from "./AssetIdentityHelpers";

const assetsService = new AssetsService();

const isFingerprintSignal = (signal) => signal.startsWith("TCP_STACK") || signal.startsWith("TCP_FINGERPRINT")
  || signal.startsWith("TCP_SERVER_FINGERPRINT");

export default function IdentityChangeFindingDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_assets_manage");

  const [finding, setFinding] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(finding ? `Identity Change: ${finding.mac}` : "Identity Change Details");

  useEffect(() => {
    setFinding(null);
    assetsService.findIdentityChangeFinding(uuid, organizationId, tenantId, setFinding, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this finding? It will be detected again as a new finding while the changes stay " +
        "inside the detection window.")) {
      return;
    }

    assetsService.deleteIdentityChangeFinding(uuid, organizationId, tenantId, () => {
      toast.success("Finding deleted.");
      setRedirect(true);
    });
  }

  const back = ApiRoutes.ETHERNET.ASSETS.IDENTITY.TAB("changes");

  if (redirect) {
    return <Navigate to={back} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Finding not found. <Link to={back}>Back to Identity Changes</Link>
      </div>
    )
  }

  if (!finding) {
    return <LoadingSpinner />
  }

  const f = finding;

  const value = (signal, v) => {
    if (!v) {
      return <span className="text-muted">n/a</span>;
    }

    if (isFingerprintSignal(signal)) {
      return v.split(", ").map((fp, i) => <React.Fragment key={fp}>{i > 0 ? <br /> : null}<TcpFingerprint value={fp} /></React.Fragment>);
    }

    return <span className="machine-data">{v}</span>;
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.INDEX}>Assets</Link></li>
              <li className="breadcrumb-item"><Link to={back}>Identity Changes</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Identity change of {f.mac}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>
            Identity Change of <span className="machine-data">{f.mac}</span>
          </h1>
        </div>

        <div className="col-4 text-end">
          {canEdit ?
            <React.Fragment>
              <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.CREATE + "?mac=" + f.mac + "&type=IDENTITY_CHANGE"}
                 className="btn btn-secondary me-1"
                 title="Create an exclusion so identity changes of this asset are not reported again">
                Exclude
              </Link>
              <a href="#" className="btn btn-danger me-1" onClick={onDelete}>Delete Finding</a>
            </React.Fragment> : null}
          <Link to={back} className="btn btn-primary">Back</Link>
        </div>
      </div>

      {f.exclusions && f.exclusions.length > 0 ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-warning mb-0">
              <strong>Excluded.</strong> This finding matches the{' '}
              {f.exclusions.length === 1 ? "exclusion" : "exclusions"}{' '}
              {f.exclusions.map((ex, i) => {
                return (
                  <React.Fragment key={ex.uuid}>
                    {i > 0 ? ", " : ""}
                    <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.DETAILS(ex.uuid)} className="alert-link">&quot;{ex.name}&quot;</Link>
                  </React.Fragment>
                )
              })}. It is no longer updated and does not raise alerts. Delete it or keep it for reference.
            </div>
          </div>
        </div> : null}

      <div className="row mt-3">
        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Asset" refreshAction={() => setRevision(new Date())} />

              <dl className="mb-0">
                <dt>MAC Address</dt>
                <dd className="mb-0">
                  {f.mac_data ? <EthernetMacAddress addressWithContext={f.mac_data} withAssetLink withAssetName />
                    : <span className="machine-data">{f.mac}</span>}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Score" />

              <dl className="mb-0">
                <dt>Score</dt>
                <dd>{numeral(f.score).format("0,0")}</dd>
                <dt>Threshold</dt>
                <dd className={f.previous_fingerprint || f.new_fingerprint ? "" : "mb-0"}>{numeral(f.threshold).format("0,0")}</dd>
                {f.previous_fingerprint || f.new_fingerprint ?
                  <React.Fragment>
                    <dt>Previous TCP Fingerprint</dt>
                    <dd><TcpFingerprint value={f.previous_fingerprint} /></dd>
                    <dt>New TCP Fingerprint</dt>
                    <dd className="mb-0"><TcpFingerprint value={f.new_fingerprint} /></dd>
                  </React.Fragment> : null}
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Metadata" />

              <dl className="mb-0">
                <dt>Status</dt>
                <dd>
                  {f.recent ?
                    <span className="text-warning">Active</span>
                    : <span className="text-muted">No changes inside the current detection window</span>}
                </dd>
                <dt>First Change</dt>
                <dd title={moment(f.first_seen).format()}>{moment(f.first_seen).fromNow()}</dd>
                <dt>Latest Change</dt>
                <dd className="mb-0" title={moment(f.last_seen).format()}>{moment(f.last_seen).fromNow()}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Changes" />

              <table className="table table-sm table-hover table-striped mb-0">
                <thead>
                <tr>
                  <th>Change</th>
                  <th>Weight</th>
                  <th>Before</th>
                  <th>Now</th>
                  <th>First Seen</th>
                </tr>
                </thead>
                <tbody>
                {f.changes.map((c) => {
                  return (
                    <tr key={c.signal}>
                      <td>
                        <strong>{changeSignalTitle(c.signal)}</strong>
                        <br />
                        <span className="text-muted small">{changeSignalDescription(c.signal)}</span>
                      </td>
                      <td>{numeral(c.weight).format("0,0")}</td>
                      <td>{value(c.signal, c.previous_value)}</td>
                      <td>{value(c.signal, c.new_value)}</td>
                      <td title={moment(c.seen_at).format()}>{moment(c.seen_at).fromNow()}</td>
                    </tr>
                  )
                })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
