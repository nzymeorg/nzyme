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
import DnsTunnelsService from "../../../../services/ethernet/DnsTunnelsService";
import L4Address from "../../shared/L4Address";
import EthernetMacAddress from "../../../shared/context/macs/EthernetMacAddress";
import {DnsTunnelIndicators} from "./DnsTunnelIndicator";

const dnsTunnelsService = new DnsTunnelsService();

export default function DnsTunnelFindingDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [finding, setFinding] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(finding ? `DNS Tunnel: ${finding.domain}` : "Ethernet DNS Tunnel Details");

  useEffect(() => {
    setFinding(null);
    dnsTunnelsService.findFinding(uuid, organizationId, tenantId, setFinding, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this finding? It will be detected again as a new finding if the client keeps " +
        "querying the domain. Create an exclusion to stop that.")) {
      return;
    }

    dnsTunnelsService.deleteFinding(uuid, organizationId, tenantId, () => {
      toast.success("Finding deleted.");
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.DNS.TUNNELS.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Finding not found. <Link to={ApiRoutes.ETHERNET.DNS.TUNNELS.INDEX}>Back to DNS Tunnels</Link>
      </div>
    )
  }

  if (!finding) {
    return <LoadingSpinner />
  }

  const f = finding;

  const dnsLogsLink = ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS + "?filters=" + encodeURIComponent(JSON.stringify({
    client_address: [{field: "client_address", operator: "equals", value: f.client_address}],
    query_etld: [{field: "query_etld", operator: "equals", value: f.domain}]
  }));

  const recordTypes = f.record_types ? Object.keys(f.record_types)
    .sort((a, b) => f.record_types[b] - f.record_types[a]) : [];
  const recordTypeTotal = recordTypes.reduce((sum, t) => sum + f.record_types[t], 0);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.DNS.INDEX}>DNS</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.DNS.TUNNELS.INDEX}>Tunnels</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{f.domain} from {f.client_address}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-7">
          <h1>
            DNS Tunnel to <span className="machine-data">{f.domain}</span>{' '}
            from <span className="machine-data">{f.client_address}</span>
          </h1>
        </div>

        <div className="col-5 text-end">
          {canEdit ?
            <React.Fragment>
              <Link to={ApiRoutes.ETHERNET.DNS.TUNNELS.EXCLUSIONS.CREATE + "?finding=" + f.uuid}
                    className="btn btn-secondary me-1"
                    title="Create an exclusion so queries from this client to this domain never show up as a tunnel again">
                Exclude
              </Link>
              <a href="#" className="btn btn-danger me-1" onClick={onDelete}>Delete Finding</a>
            </React.Fragment> : null}
          <Link to={ApiRoutes.ETHERNET.DNS.TUNNELS.INDEX} className="btn btn-primary">
            Back to DNS Tunnels
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
                    <Link to={ApiRoutes.ETHERNET.DNS.TUNNELS.EXCLUSIONS.DETAILS(ex.uuid)} className="alert-link">&quot;{ex.name}&quot;</Link>
                  </React.Fragment>
                )
              })}. It is no longer updated and does not raise alerts. Delete it or keep it for reference.
            </div>
          </div>
        </div> : null}

      <div className="row mt-3">
        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Client and Domain" />

              <dl className="mb-0">
                <dt>Client</dt>
                <dd title="The client that sent the queries">
                  {f.client_address_data ?
                    <L4Address address={f.client_address_data} hidePort={true} withAssetName />
                    : <span className="machine-data">{f.client_address}</span>}
                </dd>
                <dt>Client MAC</dt>
                <dd title="The MAC address the queries came from. On a routed segment this is the gateway.">
                  {f.client_mac_data ?
                    <EthernetMacAddress addressWithContext={f.client_mac_data} withAssetLink withAssetName />
                    : f.client_mac ? <span className="machine-data">{f.client_mac}</span>
                      : <span className="text-muted">Unknown</span>}
                </dd>
                <dt>Domain</dt>
                <dd title="The registered domain the names were looked up under. Its authoritative server is the other end of the tunnel.">
                  <span className="machine-data">{f.domain}</span>
                </dd>
                <dt>Resolvers Used</dt>
                <dd className="mb-0" title="The DNS servers the client sent the queries to">
                  {f.server_addresses && f.server_addresses.length > 0 ?
                    f.server_addresses.map((s) => <div key={s}><span className="machine-data">{s}</span></div>)
                    : <span className="text-muted">Unknown</span>}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-6">
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
                <dt>Indicators</dt>
                <dd><DnsTunnelIndicators indicators={f.indicators} /></dd>
                <dt>Queries</dt>
                <dd>{numeral(f.query_count).format("0,0")}</dd>
                <dt>Distinct Names</dt>
                <dd>{numeral(f.distinct_name_count).format("0,0")}</dd>
                <dt>Negative Replies</dt>
                <dd title="Replies that carried no answer: NXDOMAIN, SERVFAIL, REFUSED or similar">
                  {numeral(f.negative_count).format("0,0")}
                </dd>
                <dt>Entropy</dt>
                <dd title="Bits per character of the part of the name below the registered domain. Random data approaches 4 bits for hex, 5 for base32 and 6 for base64.">
                  {numeral(f.average_entropy).format("0.00")} average,{' '}
                  {numeral(f.maximum_entropy).format("0.00")} maximum bits per character
                </dd>
                <dt>Name Length</dt>
                <dd title="Characters below the registered domain">
                  {numeral(f.average_name_length).format("0.0")} average,{' '}
                  {numeral(f.maximum_name_length).format("0,0")} maximum characters
                </dd>
                <dt>Record Types</dt>
                <dd>
                  {recordTypes.length === 0 ?
                    <span className="text-muted">None recorded</span>
                    : recordTypes.map((t, i) =>
                      <span key={t}>
                        {i > 0 ? ", " : ""}
                        <span className="machine-data">{t}</span>{' '}
                        <span className="text-muted">
                          ({numeral(f.record_types[t]).format("0,0")}
                          {recordTypeTotal > 0 ? ", " + numeral(f.record_types[t] / recordTypeTotal).format("0%") : ""})
                        </span>
                      </span>)}
                </dd>
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
                  <Link to={dnsLogsLink}>DNS logs of this client&apos;s queries to the domain</Link>
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
              <CardTitleWithControls title="Names" refreshAction={() => setRevision(new Date())} />

              <p>
                <strong>
                  Showing {numeral(f.names ? f.names.length : 0).format("0,0")} of{' '}
                  {numeral(f.distinct_name_count).format("0,0")} distinct names, in the order they were first seen.
                </strong>{' '}
                <span className="text-muted">
                  In a tunnel, the part below the registered domain is the encoded payload, so the names change with
                  every query and never repeat.
                </span>
              </p>

              {!f.names || f.names.length === 0 ?
                <span className="text-muted">No names recorded.</span>
                : <div style={{maxHeight: 400, overflowY: "auto"}}>
                  <ol className="mb-0">
                    {f.names.map((n) => <li key={n}><span className="machine-data">{n}</span></li>)}
                  </ol>
                </div>}
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
