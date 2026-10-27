import React, {useContext, useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import {toast} from "react-toastify";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../misc/LoadingSpinner";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import usePageTitle from "../../../util/UsePageTitle";
import {UserContext} from "../../../App";
import {userHasPermission} from "../../../util/Tools";
import SpoofingService from "../../../services/ethernet/SpoofingService";
import L4Address from "../shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import NameResolutionProtocols from "./NameResolutionProtocol";

const spoofingService = new SpoofingService();

export default function NameResolutionSpoofingFindingDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [details, setDetails] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(details ? `Name Resolution Poisoning: ${details.finding.responder_mac}` : "Ethernet Name Resolution Poisoning Details");

  useEffect(() => {
    setDetails(null);
    spoofingService.findNameResolutionFinding(uuid, organizationId, tenantId, setDetails, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this finding? It will be detected again as a new finding if the device keeps " +
        "answering name queries. Create an exclusion to stop that.")) {
      return;
    }

    spoofingService.deleteNameResolutionFinding(uuid, organizationId, tenantId, () => {
      toast.success("Finding deleted.");
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Finding not found. <Link to={ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.INDEX}>Back to Name Resolution Poisoning</Link>
      </div>
    )
  }

  if (!details) {
    return <LoadingSpinner />
  }

  const f = details.finding;

  const dnsLogsLink = ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS + "?filters=" + encodeURIComponent(JSON.stringify({
    server_mac: [{field: "server_mac", operator: "equals", value: f.responder_mac}]
  }));

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX}>Spoofing</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.INDEX}>Name Resolution</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{f.responder_mac}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-7">
          <h1>
            Name Resolution Poisoning by <span className="machine-data">{f.responder_mac}</span>
          </h1>
        </div>

        <div className="col-5 text-end">
          {canEdit ?
            <React.Fragment>
              <Link to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.CREATE + "?nameres_finding=" + f.uuid}
                    className="btn btn-secondary me-1"
                    title="Create an exclusion so this device never shows up as poisoning again">
                Exclude
              </Link>
              <a href="#" className="btn btn-danger me-1" onClick={onDelete}>Delete Finding</a>
            </React.Fragment> : null}
          <Link to={ApiRoutes.ETHERNET.SPOOFING.NAME_RESOLUTION.INDEX} className="btn btn-primary">
            Back to Name Resolution Poisoning
          </Link>
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
                    <Link to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.DETAILS(ex.uuid)} className="alert-link">&quot;{ex.name}&quot;</Link>
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
              <CardTitleWithControls title="Responder" />

              <p className="text-muted">
                The device that answered name queries for names that are not its own.
              </p>

              <dl className="mb-0">
                <dt>MAC Address</dt>
                <dd>
                  {f.responder_mac_data ?
                    <EthernetMacAddress addressWithContext={f.responder_mac_data} withAssetLink withAssetName />
                    : <span className="machine-data">{f.responder_mac}</span>}
                </dd>
                <dt>Addresses</dt>
                <dd title="The addresses the device answered from, and that it pointed the names at">
                  {f.responder_addresses.length === 0 ? <span className="text-muted">Unknown</span>
                    : f.responder_addresses.map((a) => <div key={a}><span className="machine-data">{a}</span></div>)}
                </dd>
                <dt>Protocols</dt>
                <dd className="mb-0"><NameResolutionProtocols protocols={f.protocols} /></dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Status</dt>
                <dd>
                  {f.recent ?
                    <span className="text-danger">Active</span>
                    : <span className="text-muted">Not seen in the last {f.window_minutes} minutes</span>}
                </dd>
                <dt>Names Answered</dt>
                <dd>{numeral(f.name_count).format("0,0")}</dd>
                <dt>Responses</dt>
                <dd>
                  {numeral(f.response_count).format("0,0")}{' '}
                  ({numeral(f.unicast_count).format("0,0")} sent to a single client)
                </dd>
                <dt>Victims</dt>
                <dd>{numeral(f.victim_count).format("0,0")} clients received answers</dd>
                <dt>First Seen</dt>
                <dd title={moment(f.first_seen).format()}>{moment(f.first_seen).fromNow()}</dd>
                <dt>Last Seen</dt>
                <dd title={moment(f.last_seen).format()}>{moment(f.last_seen).fromNow()}</dd>
                <dt>Observed by Taps</dt>
                <dd>
                  {f.taps && f.taps.length > 0 ?
                    f.taps.map((t) => t.name ? t.name : t.uuid).join(", ")
                    : <span className="text-muted">n/a</span>}
                </dd>
                <dt>Raw Data</dt>
                <dd className="mb-0">
                  <Link to={dnsLogsLink}>DNS logs of this responder</Link>
                  <span className="text-muted"> (LLMNR and mDNS only)</span>
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Names" />

              <p className="text-muted">
                The names the device answered for. Names that do not exist, typos and short names of real hosts
                are typical: the device answers whatever is asked.
              </p>

              {f.names.length === 0 ?
                <div className="alert alert-info mb-0">No names recorded.</div>
                : <div style={{maxHeight: 300, overflowY: "auto"}}>
                  <ul className="mb-0">
                    {f.names.map((n) => <li key={n}><span className="machine-data">{n}</span></li>)}
                  </ul>
                </div>}
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Victims"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Clients that received the responder&apos;s answers. They connected, or tried to connect, to the
                responder believing it is the host they looked for. Windows clients authenticate to a file or web
                server they just resolved, so treat the credentials of their users as exposed. These are the devices
                to check.
              </p>

              {details.victims.length === 0 ?
                <div className="alert alert-info mb-0">
                  No client could be attributed to the answers. The queries were not seen by a tap.
                </div>
                : <div style={{maxHeight: 400, overflowY: "auto"}}>
                  <table className="table table-sm table-hover table-striped mb-0">
                    <thead>
                    <tr>
                      <th>Client</th>
                      <th>Address</th>
                      <th>Names Looked Up</th>
                      <th>Answers Received</th>
                      <th>First Seen</th>
                      <th>Last Seen</th>
                    </tr>
                    </thead>
                    <tbody>
                    {details.victims.map((v) => {
                      return (
                        <tr key={v.mac}>
                          <td>
                            {v.mac_data ? <EthernetMacAddress addressWithContext={v.mac_data} withAssetLink withAssetName />
                              : <span className="machine-data">{v.mac}</span>}
                          </td>
                          <td>
                            {v.address_data ? <L4Address address={v.address_data} hidePort={true} withAssetName />
                              : v.address ? <span className="machine-data">{v.address}</span>
                                : <span className="text-muted">Unknown</span>}
                          </td>
                          <td>
                            {v.names.map((n, i) => <span key={n}>{i > 0 ? ", " : ""}<span className="machine-data">{n}</span></span>)}
                          </td>
                          <td>{numeral(v.response_count).format("0,0")}</td>
                          <td title={moment(v.first_seen).format()}>{moment(v.first_seen).fromNow()}</td>
                          <td title={moment(v.last_seen).format()}>{moment(v.last_seen).fromNow()}</td>
                        </tr>
                      )
                    })}
                    </tbody>
                  </table>
                </div>}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
