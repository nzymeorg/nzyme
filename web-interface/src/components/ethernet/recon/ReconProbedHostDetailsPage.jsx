import React, {useEffect, useState} from "react";
import {Link, useParams} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../misc/LoadingSpinner";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import usePageTitle from "../../../util/UsePageTitle";
import ReconService from "../../../services/ethernet/ReconService";
import L4Address from "../shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";
import {Presets} from "../../shared/timerange/TimeRange";

const reconService = new ReconService();

export default function ReconProbedHostDetailsPage() {

  const {address} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [includeScanners, setIncludeScanners] = useState(false);
  const [host, setHost] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(`Probed Host: ${address}`);

  useEffect(() => {
    setHost(null);
    setNotFound(false);
    reconService.findProbedHost(
      address, organizationId, tenantId, timeRange, includeScanners, setHost, () => setNotFound(true)
    );
  }, [address, organizationId, tenantId, timeRange, includeScanners, revision]);

  if (notFound) {
    return (
      <div className="alert alert-warning">
        No probes of this host in the selected time range. Probes from devices already detected as scanners
        only count when included.{' '}
        <Link to={ApiRoutes.ETHERNET.RECON.HOSTS.INDEX}>Back to Probed Hosts</Link>

        <div className="form-check mt-2">
          <input className="form-check-input" type="checkbox" id="probed-host-include-scanners-empty"
                 checked={includeScanners}
                 onChange={(e) => setIncludeScanners(e.target.checked)} />
          <label className="form-check-label" htmlFor="probed-host-include-scanners-empty">
            Include probes from devices already detected as scanners
          </label>
        </div>
      </div>
    )
  }

  if (!host) {
    return <LoadingSpinner />
  }

  const t = host.host;

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.RECON.SCANS.INDEX}>Recon</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.RECON.HOSTS.INDEX}>Probed Hosts</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{t.address}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Probed Host <span className="machine-data">{t.address}</span></h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.RECON.HOSTS.INDEX} className="btn btn-primary">Back to Probed Hosts</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Host"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange}
                                     doNotPersistTimeRange={true}
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                The time range applies to everything on this page.
              </p>

              <div className="form-check mb-3">
                <input className="form-check-input" type="checkbox" id="probed-host-include-scanners"
                       checked={includeScanners}
                       onChange={(e) => setIncludeScanners(e.target.checked)} />
                <label className="form-check-label" htmlFor="probed-host-include-scanners">
                  Include probes from devices already detected as scanners
                </label>
              </div>

              <dl className="mb-0">
                <dt>Address</dt>
                <dd>
                  {t.address_data ? <L4Address address={t.address_data} hidePort={true} withAssetName />
                    : <span className="machine-data">{t.address}</span>}
                </dd>
                <dt>Probing Devices</dt>
                <dd>{numeral(t.source_count).format("0,0")}</dd>
                <dt>Probed Ports</dt>
                <dd className="mb-0">
                  {numeral(t.port_count).format("0,0")}, of which{' '}
                  <strong>{numeral(t.failed_port_count).format("0,0")}</strong> never answered any device
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Metadata" />

              <dl className="mb-0">
                <dt>First Seen</dt>
                <dd title={moment(t.first_seen).format()}>{moment(t.first_seen).fromNow()}</dd>
                <dt>Last Seen</dt>
                <dd className="mb-0" title={moment(t.last_seen).format()}>{moment(t.last_seen).fromNow()}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Probing Devices" />

              <p className="text-muted">
                Devices that tried to reach this host, with the ports each one touched. Many devices probing a
                few ports each is the shape of a distributed scan. A device marked as a scanner is already listed
                on the Scans tab.
              </p>

              <div style={{maxHeight: 400, overflowY: "auto"}}>
                <table className="table table-sm table-hover table-striped mb-0">
                  <thead>
                  <tr>
                    <th>Device</th>
                    <th>MAC Address</th>
                    <th>Ports</th>
                    <th>Unanswered</th>
                    <th>Last Seen</th>
                  </tr>
                  </thead>
                  <tbody>
                  {host.sources.map((s) => {
                    return (
                      <tr key={s.address + "/" + (s.mac || "")}>
                        <td>
                          {s.address_data ? <L4Address address={s.address_data} hidePort={true} withAssetName />
                            : <span className="machine-data">{s.address}</span>}
                          {s.scanner ? <span className="classification classification-suspicious ms-1" title="Attributed to a detected scan">Scanner</span> : null}
                        </td>
                        <td>
                          {s.mac_data ? <EthernetMacAddress addressWithContext={s.mac_data} withAssetLink withAssetName />
                            : <span className="text-muted">Unknown</span>}
                        </td>
                        <td>{numeral(s.port_count).format("0,0")}</td>
                        <td>{numeral(s.failed_port_count).format("0,0")}</td>
                        <td title={moment(s.last_seen).format()}>{moment(s.last_seen).fromNow()}</td>
                      </tr>
                    )
                  })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Probed Ports" />

              <p className="text-muted">
                Ports that devices tried on this host and how many devices tried each one. A port counts as
                answered when at least one device got a reply.
              </p>

              <div style={{maxHeight: 400, overflowY: "auto"}}>
                <table className="table table-sm table-hover table-striped mb-0">
                  <thead>
                  <tr>
                    <th>Port</th>
                    <th>Protocol</th>
                    <th>Probing Devices</th>
                    <th>Answered</th>
                    <th>First Seen</th>
                    <th>Last Seen</th>
                  </tr>
                  </thead>
                  <tbody>
                  {host.ports.map((p) => {
                    return (
                      <tr key={p.l4_type + "/" + p.port}>
                        <td className="machine-data">{p.port}</td>
                        <td>{p.l4_type}</td>
                        <td>{numeral(p.source_count).format("0,0")}</td>
                        <td>{p.answered ? <span className="text-success">Yes</span> : <span className="text-muted">No</span>}</td>
                        <td title={moment(p.first_seen).format()}>{moment(p.first_seen).fromNow()}</td>
                        <td title={moment(p.last_seen).format()}>{moment(p.last_seen).fromNow()}</td>
                      </tr>
                    )
                  })}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
