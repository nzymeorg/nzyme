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

export default function ReconTargetDetailsPage() {

  const {address} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);
  const [target, setTarget] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [revision, setRevision] = useState(new Date());

  usePageTitle(`Probed Target: ${address}`);

  useEffect(() => {
    setTarget(null);
    setNotFound(false);
    reconService.findProbedTarget(address, organizationId, tenantId, timeRange, setTarget, () => setNotFound(true));
  }, [address, organizationId, tenantId, timeRange, revision]);

  if (notFound) {
    return (
      <div className="alert alert-warning">
        No probes of this host in the selected time range.{' '}
        <Link to={ApiRoutes.ETHERNET.RECON.TARGETS.INDEX}>Back to Targets</Link>
      </div>
    )
  }

  if (!target) {
    return <LoadingSpinner />
  }

  const t = target.target;

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.RECON.SCANS.INDEX}>Recon</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.RECON.TARGETS.INDEX}>Targets</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{t.address}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>Probed Target <span className="machine-data">{t.address}</span></h1>
        </div>

        <div className="col-4 text-end">
          <Link to={ApiRoutes.ETHERNET.RECON.TARGETS.INDEX} className="btn btn-primary">Back to Targets</Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Target"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange}
                                     doNotPersistTimeRange={true}
                                     refreshAction={() => setRevision(new Date())} />

              <dl className="mb-0">
                <dt>Address</dt>
                <dd>
                  {t.address_data ? <L4Address address={t.address_data} hidePort={true} withAssetName />
                    : <span className="machine-data">{t.address}</span>}
                </dd>
                <dt>Probing Devices</dt>
                <dd>{numeral(t.source_count).format("0,0")}</dd>
                <dt>Probed Ports</dt>
                <dd>
                  {numeral(t.port_count).format("0,0")}, of which{' '}
                  <strong>{numeral(t.failed_port_count).format("0,0")}</strong> never answered any device
                </dd>
                <dt>First Seen</dt>
                <dd title={moment(t.first_seen).format()}>{moment(t.first_seen).fromNow()}</dd>
                <dt>Last Seen</dt>
                <dd className="mb-0" title={moment(t.last_seen).format()}>{moment(t.last_seen).fromNow()}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Probing Devices" />

              <p className="text-muted">
                Devices that probed this host, with the ports each one touched. Many devices probing a few ports
                each is the shape of a distributed scan.
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
                  {target.sources.map((s) => {
                    return (
                      <tr key={s.address + "/" + (s.mac || "")}>
                        <td>
                          {s.address_data ? <L4Address address={s.address_data} hidePort={true} withAssetName />
                            : <span className="machine-data">{s.address}</span>}
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
                Ports that were probed on this host and how many devices probed each one. A port counts as
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
                  {target.ports.map((p) => {
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
