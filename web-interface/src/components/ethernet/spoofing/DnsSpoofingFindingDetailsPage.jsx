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

const spoofingService = new SpoofingService();

export default function DnsSpoofingFindingDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [details, setDetails] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(details ? `DNS Spoofing: ${details.finding.server_address}` : "Ethernet DNS Spoofing Details");

  useEffect(() => {
    setDetails(null);
    spoofingService.findDnsFinding(uuid, organizationId, tenantId, setDetails, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this finding? It will be detected again as a new finding if the device keeps " +
        "forging replies. Create an exclusion to stop that.")) {
      return;
    }

    spoofingService.deleteDnsFinding(uuid, organizationId, tenantId, () => {
      toast.success("Finding deleted.");
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.SPOOFING.DNS.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Finding not found. <Link to={ApiRoutes.ETHERNET.SPOOFING.DNS.INDEX}>Back to DNS Spoofing</Link>
      </div>
    )
  }

  if (!details) {
    return <LoadingSpinner />
  }

  const f = details.finding;
  const lostCount = f.forged_count - f.won_count - f.unanswered_count;

  const dnsLogsLink = ApiRoutes.ETHERNET.DNS.TRANSACTION_LOGS + "?filters=" + encodeURIComponent(JSON.stringify({
    server_address: [{field: "server_address", operator: "equals", value: f.server_address}]
  }));

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX}>Spoofing</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.DNS.INDEX}>DNS</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{f.server_address} by {f.spoofer_mac}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-7">
          <h1>
            DNS Spoofing of <span className="machine-data">{f.server_address}</span>{' '}
            by <span className="machine-data">{f.spoofer_mac}</span>
          </h1>
        </div>

        <div className="col-5 text-end">
          {canEdit ?
            <React.Fragment>
              <Link to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.CREATE + "?dns_finding=" + f.uuid}
                    className="btn btn-secondary me-1"
                    title="Create an exclusion so replies from this device for this resolver never show up as spoofing again">
                Exclude
              </Link>
              <a href="#" className="btn btn-danger me-1" onClick={onDelete}>Delete Finding</a>
            </React.Fragment> : null}
          <Link to={ApiRoutes.ETHERNET.SPOOFING.DNS.INDEX} className="btn btn-primary">
            Back to DNS Spoofing
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
              <CardTitleWithControls title="Resolver and Devices" />

              <dl className="mb-0">
                <dt>Impersonated Resolver</dt>
                <dd title="The DNS server address the forged replies claimed to come from">
                  {f.server_address_data ?
                    <L4Address address={f.server_address_data} hidePort={true} withAssetName />
                    : <span className="machine-data">{f.server_address}</span>}
                </dd>
                <dt>Spoofer</dt>
                <dd title="The MAC address the forged replies actually came from">
                  {f.spoofer_mac_data ?
                    <EthernetMacAddress addressWithContext={f.spoofer_mac_data} withAssetLink withAssetName />
                    : <span className="machine-data">{f.spoofer_mac}</span>}
                </dd>
                <dt>Legitimate Owner</dt>
                <dd className="mb-0" title="The MAC address the resolver's replies normally come from: the resolver itself or the gateway in front of it">
                  {f.owner_mac_data ?
                    <EthernetMacAddress addressWithContext={f.owner_mac_data} withAssetLink withAssetName />
                    : <span className="machine-data">{f.owner_mac}</span>}
                </dd>
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
                <dt>Forged Replies</dt>
                <dd>{numeral(f.forged_count).format("0,0")}</dd>
                <dt>Races</dt>
                <dd title="Won: the forged reply arrived before the owner's. Lost: after it. Unanswered: the owner never replied to that query.">
                  {numeral(f.won_count).format("0,0")} won, {numeral(lostCount).format("0,0")} lost,{' '}
                  {numeral(f.unanswered_count).format("0,0")} unanswered
                </dd>
                <dt>Names Spoofed</dt>
                <dd>{numeral(f.name_count).format("0,0")}</dd>
                <dt>Victims</dt>
                <dd>{numeral(f.victim_count).format("0,0")} clients received forged replies</dd>
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
                  <Link to={dnsLogsLink}>DNS logs of queries to this resolver</Link>
                  <span className="text-muted"> (expand a query to see both replies)</span>
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Names and Answers" />

              <p className="text-muted">
                The names the spoofer answered for, and the addresses it pointed them at. A few names of interest
                with the spoofer&apos;s own address as answer is the usual picture.
              </p>

              <dl className="mb-0">
                <dt>Answers</dt>
                <dd>
                  {f.answers.length === 0 ?
                    <span className="text-muted">None recorded</span>
                    : f.answers.map((a) => <div key={a}><span className="machine-data">{a}</span></div>)}
                </dd>
                <dt>Names</dt>
                <dd className="mb-0">
                  {f.names.length === 0 ?
                    <span className="text-muted">No names recorded. The queries were not seen by a tap.</span>
                    : <div style={{maxHeight: 250, overflowY: "auto"}}>
                      <ul className="mb-0">
                        {f.names.map((n) => <li key={n}><span className="machine-data">{n}</span></li>)}
                      </ul>
                    </div>}
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
              <CardTitleWithControls title="Victims"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Clients that received forged replies. A client uses the first reply it gets, so every race the
                spoofer won or the owner never answered likely sent that client to the spoofer&apos;s address for
                the name it looked up. These are the devices to check.
              </p>

              {details.victims.length === 0 ?
                <div className="alert alert-info mb-0">
                  No client could be attributed to the forged replies.
                </div>
                : <div style={{maxHeight: 400, overflowY: "auto"}}>
                  <table className="table table-sm table-hover table-striped mb-0">
                    <thead>
                    <tr>
                      <th>Client</th>
                      <th>Address</th>
                      <th>Names Looked Up</th>
                      <th>Forged Replies</th>
                      <th>Likely Accepted</th>
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
                          <td>{numeral(v.forged_count).format("0,0")}</td>
                          <td title="Forged replies that arrived before the owner's reply, or that the owner never answered">
                            {numeral(v.won_count + v.unanswered_count).format("0,0")}
                          </td>
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
