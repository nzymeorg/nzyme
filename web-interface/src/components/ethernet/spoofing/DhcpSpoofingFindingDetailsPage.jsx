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
import DhcpSpoofingFindingType, {dhcpSpoofingFindingTypeToName} from "./DhcpSpoofingFindingType";
import DhcpOfferedParameters from "./DhcpOfferedParameters";

const spoofingService = new SpoofingService();

export default function DhcpSpoofingFindingDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [details, setDetails] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(details ? `DHCP Spoofing: ${details.finding.server_address}` : "Ethernet DHCP Spoofing Details");

  useEffect(() => {
    setDetails(null);
    spoofingService.findDhcpFinding(uuid, organizationId, tenantId, setDetails, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this finding? It will be detected again as a new finding if the server keeps " +
        "answering clients. Trust the server or create an exclusion to stop that.")) {
      return;
    }

    spoofingService.deleteDhcpFinding(uuid, organizationId, tenantId, () => {
      toast.success("Finding deleted.");
      setRedirect(true);
    });
  }

  const onTrust = (e) => {
    e.preventDefault();

    if (!confirm("Trust this DHCP server? It will no longer raise rogue server findings, and the configuration it " +
        "hands out right now becomes its expected configuration.")) {
      return;
    }

    spoofingService.setDhcpServerTrusted(details.finding.server_uuid, organizationId, tenantId, true, () => {
      toast.success("Server is now trusted.");
      setRevision(new Date());
    });
  }

  const onAcceptConfiguration = (e) => {
    e.preventDefault();

    if (!confirm("Accept the configuration this server hands out right now as its expected configuration?")) {
      return;
    }

    spoofingService.acceptDhcpServerConfiguration(details.finding.server_uuid, organizationId, tenantId, () => {
      toast.success("Configuration accepted.");
      setRevision(new Date());
    });
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.SPOOFING.DHCP.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Finding not found. <Link to={ApiRoutes.ETHERNET.SPOOFING.DHCP.INDEX}>Back to DHCP Spoofing</Link>
      </div>
    )
  }

  if (!details) {
    return <LoadingSpinner />
  }

  const f = details.finding;
  const rogue = f.type === "ROGUE_SERVER";

  const transactionsLink = ApiRoutes.ETHERNET.ASSETS.DHCP.INDEX + "?filters=" + encodeURIComponent(JSON.stringify({
    server_mac: [{field: "server_mac", operator: "equals", value: f.server_mac}]
  }));

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX}>Spoofing</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.DHCP.INDEX}>DHCP</Link></li>
              <li className="breadcrumb-item active" aria-current="page">
                {dhcpSpoofingFindingTypeToName(f.type)}: {f.server_address}
              </li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-7">
          <h1>
            {rogue ? "Rogue DHCP Server" : "Changed DHCP Configuration from"}{' '}
            <span className="machine-data">{f.server_address}</span>
          </h1>
        </div>

        <div className="col-5 text-end">
          {canEdit ?
            <React.Fragment>
              {rogue && f.server_uuid && f.server_trusted === false ?
                <a href="#" className="btn btn-success me-1" onClick={onTrust}
                   title="Mark this server as legitimate. No more rogue server findings for it.">
                  Trust Server
                </a> : null}
              {!rogue && f.server_uuid && f.server_trusted && f.server_latest_matches_baseline === false ?
                <a href="#" className="btn btn-warning me-1" onClick={onAcceptConfiguration}
                   title="Take the configuration the server hands out now as its expected configuration.">
                  Accept Configuration
                </a> : null}
              <Link to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.CREATE + "?dhcp_finding=" + f.uuid}
                    className="btn btn-secondary me-1"
                    title="Create an exclusion so this server never shows up as spoofing again">
                Exclude
              </Link>
              <a href="#" className="btn btn-danger me-1" onClick={onDelete}>Delete Finding</a>
            </React.Fragment> : null}
          <Link to={ApiRoutes.ETHERNET.SPOOFING.DHCP.INDEX} className="btn btn-primary">Back to DHCP Spoofing</Link>
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

      {rogue && f.server_trusted ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-info mb-0">
              <strong>Resolved.</strong> The server has been trusted since this finding was raised. It is no
              longer updated. Delete it or keep it for reference.
            </div>
          </div>
        </div> : null}

      {!rogue && f.offered_accepted ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-info mb-0">
              <strong>Resolved.</strong> The configuration this finding reports has been accepted as the expected
              configuration of the server since the finding was raised. It is no longer updated. Delete it or keep
              it for reference.
            </div>
          </div>
        </div> : null}

      <div className="row mt-3">
        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Server" />

              <p className="text-muted">
                {rogue ? "The device that answered DHCP clients although it is not a trusted DHCP server."
                  : "The trusted DHCP server that handed out a configuration different from its baseline."}
              </p>

              <dl className="mb-0">
                <dt>Type</dt>
                <dd><DhcpSpoofingFindingType type={f.type} /></dd>
                <dt>Address</dt>
                <dd>
                  {f.server_address_data ?
                    <L4Address address={f.server_address_data} hidePort={true} withAssetName />
                    : <span className="machine-data">{f.server_address}</span>}
                </dd>
                {f.server_identifier && f.server_identifier !== f.server_address ?
                  <React.Fragment>
                    <dt>Server Identifier</dt>
                    <dd title="DHCP option 54. Differs from the address when the server answers through a relay.">
                      <span className="machine-data">{f.server_identifier}</span>
                    </dd>
                  </React.Fragment> : null}
                <dt>MAC Address</dt>
                <dd>
                  {f.server_mac_data ?
                    <EthernetMacAddress addressWithContext={f.server_mac_data} withAssetLink withAssetName />
                    : <span className="machine-data">{f.server_mac}</span>}
                </dd>
                <dt>Trust</dt>
                <dd className="mb-0">
                  {f.server_trusted === null || f.server_trusted === undefined ?
                    <span className="text-muted">Server no longer known</span>
                    : f.server_trusted ? <span className="text-success">Trusted</span>
                      : <span className="text-danger">Untrusted</span>}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title={rogue ? "Handed Out" : "Handed Out vs. Expected"} />

              <p className="text-muted">
                {rogue ? "What clients that took this server's lease were told to use."
                  : "What the server handed out. Values that differ from the expected configuration are highlighted."}
              </p>

              <DhcpOfferedParameters parameters={f.offered} compareTo={rogue ? undefined : f.expected} />

              {!rogue && f.expected ?
                <React.Fragment>
                  <hr />
                  <h6>Expected</h6>
                  <DhcpOfferedParameters parameters={f.expected} />
                </React.Fragment> : null}
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
                <dt>Responses</dt>
                <dd>
                  {numeral(f.response_count).format("0,0")} ({numeral(f.offer_count).format("0,0")} offers,{' '}
                  {numeral(f.ack_count).format("0,0")} acknowledgements)
                </dd>
                <dt>Transactions</dt>
                <dd>{numeral(f.transaction_count).format("0,0")}</dd>
                <dt>Victims</dt>
                <dd>{numeral(f.victim_count).format("0,0")} clients took a lease or chose this server</dd>
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
                <dt>Raw Transactions</dt>
                <dd className="mb-0">
                  <Link to={transactionsLink}>DHCP transactions of this server</Link>
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
                Clients that accepted a lease from this server or chose its offer. They are using the gateway and
                DNS servers shown above, which means their traffic can be read, changed or redirected by whoever
                runs those. These are the devices to check. Clients that only received an offer and went with
                another server are not listed.
              </p>

              {details.victims.length === 0 ?
                <div className="alert alert-info mb-0">
                  No client took a lease from this server. It only sent offers that clients did not accept.
                </div>
                : <div style={{maxHeight: 400, overflowY: "auto"}}>
                  <table className="table table-sm table-hover table-striped mb-0">
                    <thead>
                    <tr>
                      <th>Client</th>
                      <th>Assigned Address</th>
                      <th>Transactions</th>
                      <th>Leases</th>
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
                            {v.assigned_address_data ? <L4Address address={v.assigned_address_data} hidePort={true} withAssetName />
                              : v.assigned_address ? <span className="machine-data">{v.assigned_address}</span>
                                : <span className="text-muted">Unknown</span>}
                          </td>
                          <td>{numeral(v.transaction_count).format("0,0")}</td>
                          <td>{numeral(v.lease_count).format("0,0")}</td>
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
