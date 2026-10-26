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
import ArpSpoofingClaimTimeline from "./ArpSpoofingClaimTimeline";

const spoofingService = new SpoofingService();

export default function ArpSpoofingFindingDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [details, setDetails] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(details ? `ARP Poisoning: ${details.finding.address}` : "Ethernet ARP Poisoning Details");

  useEffect(() => {
    setDetails(null);
    spoofingService.findArpFinding(uuid, organizationId, tenantId, setDetails, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this finding? It will be detected again as a new finding if the device keeps " +
        "claiming the address.")) {
      return;
    }

    spoofingService.deleteArpFinding(uuid, organizationId, tenantId, () => {
      toast.success("Finding deleted.");
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Finding not found. <Link to={ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX}>Back to ARP Poisoning</Link>
      </div>
    )
  }

  if (!details) {
    return <LoadingSpinner />
  }

  const f = details.finding;

  const arpPacketsLink = ApiRoutes.ETHERNET.ASSETS.ARP.INDEX + "?filters=" + encodeURIComponent(JSON.stringify({
    arp_sender_address: [{field: "arp_sender_address", operator: "equals", value: f.address}]
  }));

  const share = (count) => {
    if (!f.reply_count) {
      return "";
    }

    return " (" + numeral(count / f.reply_count).format("0%") + " of replies)";
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX}>Spoofing</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX}>ARP</Link></li>
              <li className="breadcrumb-item active" aria-current="page">
                {f.address} claimed by {f.spoofer_mac}
              </li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>
            ARP Poisoning of <span className="machine-data">{f.address}</span>
            {' '}by <span className="machine-data">{f.spoofer_mac}</span>
          </h1>
        </div>

        <div className="col-4 text-end">
          {canEdit ?
            <React.Fragment>
              <Link to={ApiRoutes.ETHERNET.SPOOFING.EXCLUSIONS.CREATE + "?finding=" + f.uuid}
                 className="btn btn-secondary me-1"
                 title="Create an exclusion so this never shows up as poisoning again">
                Exclude
              </Link>
              <a href="#" className="btn btn-danger me-1" onClick={onDelete}>Delete Finding</a>
            </React.Fragment> : null}
          <Link to={ApiRoutes.ETHERNET.SPOOFING.ARP.INDEX} className="btn btn-primary">Back to ARP Poisoning</Link>
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
              <CardTitleWithControls title="Spoofer" />

              <p className="text-muted">
                The device that claimed the address although it belongs to another device.
              </p>

              <dl className="mb-0">
                <dt>MAC Address</dt>
                <dd>
                  {f.spoofer_mac_data ?
                    <EthernetMacAddress addressWithContext={f.spoofer_mac_data} withAssetLink withAssetName />
                    : <span className="machine-data">{f.spoofer_mac}</span>}
                </dd>
                <dt>Claims</dt>
                <dd>{numeral(f.claim_count).format("0,0")} ARP packets claiming the address</dd>
                <dt>Replies</dt>
                <dd>{numeral(f.reply_count).format("0,0")}</dd>
                <dt>Unsolicited Replies</dt>
                <dd title="Replies that no device had asked for in the seconds before">
                  {numeral(f.unsolicited_count).format("0,0")}{share(f.unsolicited_count)}
                </dd>
                <dt>Broadcast Replies</dt>
                <dd className="mb-0" title="Gratuitous replies sent to every device on the segment">
                  {numeral(f.broadcast_count).format("0,0")}{share(f.broadcast_count)}
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Legitimate Owner" />

              <p className="text-muted">
                The device that claimed the address first and kept claiming it while the spoofer did too.
              </p>

              <dl className="mb-0">
                <dt>Address</dt>
                <dd>
                  {f.address_data ?
                    <L4Address address={f.address_data} hidePort={true} withAssetName />
                    : <span className="machine-data">{f.address}</span>}
                </dd>
                <dt>MAC Address</dt>
                <dd>
                  {f.owner_mac_data ?
                    <EthernetMacAddress addressWithContext={f.owner_mac_data} withAssetLink withAssetName />
                    : <span className="machine-data">{f.owner_mac}</span>}
                </dd>
                <dt>Claims</dt>
                <dd className="mb-0">{numeral(f.owner_claim_count).format("0,0")} ARP packets claiming the address</dd>
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
                <dt>Victims</dt>
                <dd>{numeral(f.victim_count).format("0,0")} devices received replies from the spoofer</dd>
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
                <dt>Raw Packets</dt>
                <dd className="mb-0">
                  <Link to={arpPacketsLink}>ARP packets of this address</Link>
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
              <CardTitleWithControls title="Who Claimed the Address"
                                     refreshAction={() => setRevision(new Date())} />

              <ArpSpoofingClaimTimeline finding={f} timeline={details.claim_timeline} />
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
                Devices that received the spoofer&apos;s replies for the address. Their ARP cache may now point at
                the spoofer, which means their traffic for the address goes to the spoofer first. These are the
                devices to check. A man-in-the-middle attack poisons both directions and produces one finding per
                address, so the device listed here is usually the spoofer&apos;s other victim in a second finding.
              </p>

              {details.victims.length === 0 ?
                <div className="alert alert-info mb-0">
                  No device received a reply from the spoofer. The spoofer only announced the address.
                </div>
                : <div style={{maxHeight: 400, overflowY: "auto"}}>
                  <table className="table table-sm table-hover table-striped mb-0">
                    <thead>
                    <tr>
                      <th>Device</th>
                      <th>MAC Address</th>
                      <th>Replies Received</th>
                      <th>Unsolicited</th>
                      <th>First Seen</th>
                      <th>Last Seen</th>
                    </tr>
                    </thead>
                    <tbody>
                    {details.victims.map((v) => {
                      return (
                        <tr key={v.mac}>
                          <td>
                            {v.address_data ? <L4Address address={v.address_data} hidePort={true} withAssetName />
                              : v.address ? <span className="machine-data">{v.address}</span>
                                : <span className="text-muted">Unknown</span>}
                          </td>
                          <td>
                            {v.mac_data ? <EthernetMacAddress addressWithContext={v.mac_data} withAssetLink withAssetName />
                              : <span className="machine-data">{v.mac}</span>}
                          </td>
                          <td>{numeral(v.packet_count).format("0,0")}</td>
                          <td>{numeral(v.unsolicited_count).format("0,0")}</td>
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
