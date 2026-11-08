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
import TcpFingerprint, {describeStackDifference} from "./TcpFingerprint";
import L4Address from "../../shared/L4Address";
import DuplicateMacDestinationsTable from "./DuplicateMacDestinationsTable";
import PivotLink from "../../../shared/PivotLink";
import {filteredLink, macFilter} from "../details/AssetProfileHelpers";

const assetsService = new AssetsService();

export default function DuplicateMacFindingDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_assets_manage");

  const [finding, setFinding] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(finding ? `Duplicate MAC Address: ${finding.mac}` : "Duplicate MAC Address Details");

  useEffect(() => {
    setFinding(null);
    assetsService.findDuplicateMacFinding(uuid, organizationId, tenantId, setFinding, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this finding? It will be detected again as a new finding if the stacks keep " +
        "sharing the address.")) {
      return;
    }

    assetsService.deleteDuplicateMacFinding(uuid, organizationId, tenantId, () => {
      toast.success("Finding deleted.");
      setRedirect(true);
    });
  }

  const back = ApiRoutes.ETHERNET.ASSETS.IDENTITY.TAB("duplicates");

  if (redirect) {
    return <Navigate to={back} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Finding not found. <Link to={back}>Back to Duplicate MAC Addresses</Link>
      </div>
    )
  }

  if (!finding) {
    return <LoadingSpinner />
  }

  const f = finding;
  const fingerprints = f.stacks.map((s) => s.fingerprint);

  const stackSessionsLink = (fingerprint) => filteredLink(ApiRoutes.ETHERNET.L4.SESSIONS, {
    ...macFilter("source_mac", f.mac),
    fingerprint: [{field: "fingerprint", operator: "equals", value: fingerprint}]
  });

  const sourceSessionsLink = (fingerprint, address) => filteredLink(ApiRoutes.ETHERNET.L4.SESSIONS, {
    ...macFilter("source_mac", f.mac),
    fingerprint: [{field: "fingerprint", operator: "equals", value: fingerprint}],
    source_address: [{field: "source_address", operator: "equals", value: address}]
  });

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.ASSETS.INDEX}>Assets</Link></li>
              <li className="breadcrumb-item"><Link to={back}>Duplicate MAC Addresses</Link></li>
              <li className="breadcrumb-item active" aria-current="page">Duplicate MAC address {f.mac}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>
            Duplicate MAC Address <span className="machine-data">{f.mac}</span>
          </h1>
        </div>

        <div className="col-4 text-end">
          {canEdit ?
            <React.Fragment>
              <Link to={ApiRoutes.ETHERNET.ASSETS.IDENTITY.EXCLUSIONS.CREATE + "?mac=" + f.mac + "&type=DUPLICATE_MAC"}
                 className="btn btn-secondary me-1"
                 title="Create an exclusion so this address never shows up as duplicate again">
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
              <CardTitleWithControls title="Asset"
                                     refreshAction={() => setRevision(new Date())}
                                     helpLink="https://go.nzyme.org/ethernet-assets-identity-duplicates" />

              <dl className="mb-0">
                <dt>MAC Address</dt>
                <dd>
                  {f.mac_data ? <EthernetMacAddress addressWithContext={f.mac_data} withAssetLink withAssetName />
                    : <span className="machine-data">{f.mac}</span>}
                </dd>
                <dt>Observed by Taps</dt>
                <dd className="mb-0">
                  {f.taps && f.taps.length > 0 ?
                    f.taps.map((t) => t.name ? t.name : t.uuid).join(", ")
                    : <span className="text-muted">n/a</span>}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Stacks" />

              <dl className="mb-0">
                <dt>Stacks</dt>
                <dd>{numeral(f.stack_count).format("0,0")} different TCP stacks used the address at the same time, none of them behind a router or NAT box.</dd>
                <dt>Sessions</dt>
                <dd className="mb-0">{numeral(f.session_count).format("0,0")}</dd>
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
                    <span className="text-danger">Active</span>
                    : <span className="text-muted">Not seen in the last {f.window_minutes} minutes</span>}
                </dd>
                <dt>First Seen</dt>
                <dd title={moment(f.first_seen).format()}>{moment(f.first_seen).fromNow()}</dd>
                <dt>Last Seen</dt>
                <dd className="mb-0" title={moment(f.last_seen).format()}>{moment(f.last_seen).fromNow()}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      {f.stack_count > f.stacks.length ?
        <div className="row mt-3">
          <div className="col-12">
            <p className="text-muted mb-0">
              A finding stores at most {numeral(f.stacks.length).format("0,0")} stacks. Shown are the{' '}
              {numeral(f.stacks.length).format("0,0")} busiest of {numeral(f.stack_count).format("0,0")}, ranked by
              sessions. The others are counted but not stored.
            </p>
          </div>
        </div> : null}

      <div className="row mt-3">
        {f.stacks.map((s, i) => {
          return (
            <div className={f.stacks.length === 2 ? "col-6 mb-3" : "col-4 mb-3"} key={s.fingerprint}>
              <div className="card h-100">
                <div className="card-body">
                  <CardTitleWithControls title={"Stack " + (i + 1)} />

                  <p>{describeStackDifference(s.fingerprint, fingerprints)}</p>

                  <dl className="mb-0">
                    <dt>Fingerprint</dt>
                    <dd><TcpFingerprint value={s.fingerprint} /></dd>
                    <dt>Signatures</dt>
                    <dd>
                      {s.signatures.map((sig, j) => <React.Fragment key={sig}>{j > 0 ? <br /> : null}<TcpFingerprint value={sig} /></React.Fragment>)}
                    </dd>
                    <dt>Sessions</dt>
                    <dd>
                      {numeral(s.sessions).format("0,0")}
                      <PivotLink to={stackSessionsLink(s.fingerprint)} title="Sessions of this stack"
                                 className="ms-2 small" icon={true} />
                    </dd>
                    <dt>Seen</dt>
                    <dd>
                      <span title={moment(s.first_seen).format()}>{moment(s.first_seen).fromNow()}</span>
                      {s.first_seen !== s.last_seen ?
                        <React.Fragment> to <span title={moment(s.last_seen).format()}>{moment(s.last_seen).fromNow()}</span></React.Fragment> : null}
                    </dd>
                    <dt>Source</dt>
                    <dd>
                      <ul className="list-unstyled mb-0">
                        {s.source_addresses.map((a) => (
                          <li key={a.address}>
                            <L4Address address={a}
                                       suffixElement={<PivotLink to={sourceSessionsLink(s.fingerprint, a.address)}
                                                                 title="Sessions of this stack from this address"
                                                                 className="small" icon={true} />} />
                          </li>
                        ))}
                      </ul>
                    </dd>
                  </dl>

                  <h6 className="mb-1 mt-3">Destinations</h6>

                  <DuplicateMacDestinationsTable destinations={s.destinations} total={s.destination_count} mac={f.mac}
                                                 fingerprint={s.fingerprint} />
                </div>
              </div>
            </div>
          )
        })}
      </div>

    </React.Fragment>
  )

}
