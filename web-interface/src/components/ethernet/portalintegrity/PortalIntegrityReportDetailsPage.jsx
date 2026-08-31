import React, {useContext, useEffect, useState} from "react";
import usePageTitle from "../../../util/UsePageTitle";
import {useParams} from "react-router-dom";
import {TapContext} from "../../../App";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import {disableTapSelector, enableTapSelector} from "../../misc/TapSelector";
import PortalIntegrityService from "../../../services/ethernet/PortalIntegrityService";
import ApiRoutes from "../../../util/ApiRoutes";
import FullCopyShortenedId from "../../shared/FullCopyShortenedId";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import LoadingSpinner from "../../misc/LoadingSpinner";
import moment from "moment";
import numeral from "numeral";
import PortalIntegrityVerdict from "./PortalIntegrityVerdict";
import {truncate} from "../../../util/Tools";
import L4Address from "../shared/L4Address";
import FilterValueIcon from "../../shared/filtering/FilterValueIcon";
import {STUN_CONNECTIONS_FILTER_FIELDS} from "../nat/traversal/stun_connections/STUNConnectionsFilterFields";

const portalIntegrityService = new PortalIntegrityService();

export default function PortalIntegrityReportDetailsPage() {

  usePageTitle("Portal Integrity Report Details");

  const { uuid } = useParams();

  const tapContext = useContext(TapContext);
  const selectedTaps = tapContext.taps;

  const [organizationId, tenantId] = useSelectedTenant();

  const [report, setReport] = useState(null);

  useEffect(() => {
    enableTapSelector(tapContext);

    return () => {
      disableTapSelector(tapContext);
    }
  }, [tapContext]);

  useEffect(() => {
    setReport(null);
    portalIntegrityService.findOneReport(uuid, organizationId, tenantId, selectedTaps, setReport);
  }, [uuid, organizationId, tenantId, selectedTaps])

  const lastHopUrl = () => {
    if (!report.last_hop_url) {
      return "No URL"
    }

    return (
      <code>{report.last_hop_url}</code>
    )
  }

  const lastHopRaw = () => {
    if (!report.hops || report.hops.length === 0) {
      return <span className="text-muted">No hops were recorded.</span>
    }

    const sortedHops = [...report.hops].sort((a, b) => a.hop_index - b.hop_index);
    const lastHop = sortedHops[sortedHops.length - 1];

    if (!lastHop.raw) {
      return <span className="text-muted">The last hop had no raw data.</span>
    }

    return (
      <code className="mb-0 machine-data">{lastHop.raw}</code>
    )
  }

  const hopsTable = () => {
    if (!report.hops || report.hops.length === 0) {
      return <span className="text-muted">No hops were recorded.</span>
    }

    return (
      <table className="table table-sm table-hover table-striped mt-3">
        <thead>
        <tr>
          <th>#</th>
          <th>URL</th>
          <th>Status</th>
          <th>Address</th>
          <th>Followed To</th>
          <th>Complete</th>
          <th>TLS</th>
        </tr>
        </thead>
        <tbody>
        {report.hops.map((h, i) => {
          return (
            <tr key={i}>
              <td>{numeral(h.hop_index).format("0,0")}</td>
              <td title={h.url}>{truncate(h.url, 50, false)}</td>
              <td className="machine-data">{h.status}</td>
              <td><L4Address address={h.resolved_address} hidePort={true} /></td>
              <td title={h.followed_to ? h.followed_to : null}>{h.followed_to ? truncate(h.followed_to, 50, false) : <span className="text-muted">n/a</span>}</td>
              <td>{h.completeness}</td>
              <td>{h.tls ? <i className="text-success fa fa-solid fa-check-square" title="Valid TLS" /> : <i className="text-danger fa fa-solid fa-warning" title="No TLS" />}</td>
            </tr>
          )
        })}
        </tbody>
      </table>
    )
  }

  if (report === null) {
    return <LoadingSpinner />
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-10">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><a href={ApiRoutes.ETHERNET.PORTAL_INTEGRITY.INDEX}>Portal Integrity</a></li>
              <li className="breadcrumb-item">Reports</li>
              <li className="breadcrumb-item active" aria-current="page">{uuid}</li>
            </ol>
          </nav>
        </div>
        <div className="col-2">
          <a href={ApiRoutes.ETHERNET.PORTAL_INTEGRITY.INDEX} className="btn btn-primary float-end">
            Back
          </a>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <h1>Portal Integrity Report {<FullCopyShortenedId value={uuid} />}</h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Probe Name</dt>
                <dd>{report.probe_name}</dd>
                <dt>Control URL</dt>
                <dd>{report.control_url}</dd>
                <dt>Hop Count</dt>
                <dd>{numeral(report.hop_count).format("0,0")}</dd>
                <dt>Probed At</dt>
                <dd title={moment(report.probed_at).fromNow()}>
                  {moment(report.probed_at).format()}
                </dd>
                <dt>Verdict</dt>
                <dd><PortalIntegrityVerdict verdict={report.verdict} /></dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Network" />

              <dl className="mb-0">
                <dt>Probe Interface</dt>
                <dd className="machine-data">{report.probe_interface}</dd>
                <dt>Probe MAC Address</dt>
                <dd className="machine-data">{report.probe_mac}</dd>
                <dt>IP Address</dt>
                <dd className="machine-data">{report.assigned_address}</dd>
                <dt>Gateway Address</dt>
                <dd className="machine-data">
                  {report.gateway_address ? report.gateway_address :
                    <span className="text-muted">n/a</span>}
                </dd>
                <dt>DHCP Server Address</dt>
                <dd className="machine-data">
                  {report.dhcp_server_address ? report.dhcp_server_address :
                    <span className="text-muted">n/a</span>}
                </dd>
                <dt>DNS Servers</dt>
                <dd className="machine-data">
                  <ul className="mb-0 p-0" style={{listStyleType: "none"}}>
                  {report.dns_servers.map((dns, i) => {
                    return (
                      <li key={i}>{dns}</li>
                    )
                  })}
                  </ul>
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
              <CardTitleWithControls title="Presented Portal" />

              <dl>
                <dt>URL</dt>
                <dd>{lastHopUrl()}</dd>
              </dl>

              <h4>Content</h4>

              {lastHopRaw()}
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Hops" />

              <strong>Total: {numeral(report.hops.length).format("0,0")}</strong>

              {hopsTable()}
            </div>
          </div>
        </div>
      </div>

    </React.Fragment>
  )

}