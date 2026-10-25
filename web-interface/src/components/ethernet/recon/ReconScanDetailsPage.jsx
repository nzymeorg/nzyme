import React, {useContext, useEffect, useState} from "react";
import {Link, Navigate, useLocation, useParams} from "react-router-dom";
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
import ReconService from "../../../services/ethernet/ReconService";
import ReconScanType from "./ReconScanType";
import ReconScanEnrichment from "./ReconScanEnrichment";
import ReconScanTargetsTable from "./ReconScanTargetsTable";
import ReconScanFindings from "./ReconScanFindings";
import ReconScanTimeline from "./ReconScanTimeline";
import ReconScanTargetBreakdown from "./ReconScanTargetBreakdown";
import Filters from "../../shared/filtering/Filters";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import {RECON_SCAN_TARGETS_FILTER_FIELDS} from "./ReconScanTargetsFilterFields";
import L4Address from "../shared/L4Address";
import EthernetMacAddress from "../../shared/context/macs/EthernetMacAddress";

const reconService = new ReconService();

const useQuery = () => {
  return new URLSearchParams(useLocation().search);
}

export default function ReconScanDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [scan, setScan] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);

  const urlQuery = useQuery();
  const location = useLocation();
  const [targetsFilters, setTargetsFilters] = useState(
    queryParametersToFilters(urlQuery.get("filters"), RECON_SCAN_TARGETS_FILTER_FIELDS)
  );
  const [targets, setTargets] = useState(null);
  const [targetsOrderColumn, setTargetsOrderColumn] = useState("outcome");
  const [targetsOrderDirection, setTargetsOrderDirection] = useState("DESC");
  const [targetsPage, setTargetsPage] = useState(1);
  const targetsPerPage = 25;

  const [summary, setSummary] = useState(null);

  const [revision, setRevision] = useState(new Date());

  /*
   * Fingerprinting attempts are scans with their own tab and URL. Until the scan is loaded, the URL decides which
   * tab this page belongs to; afterwards the scan type does, so links using either URL end up consistent.
   */
  const fingerprinting = scan
    ? scan.type === "OS_FINGERPRINTING"
    : location.pathname.startsWith(ApiRoutes.ETHERNET.RECON.FINGERPRINTING.INDEX);
  const listRoute = fingerprinting ? ApiRoutes.ETHERNET.RECON.FINGERPRINTING.INDEX : ApiRoutes.ETHERNET.RECON.SCANS.INDEX;
  const listName = fingerprinting ? "Fingerprinting" : "Scans";

  usePageTitle(scan
    ? `${fingerprinting ? "Fingerprinting" : "Scan"}: ${scan.type_human_readable} from ${scan.source}`
    : "Ethernet Recon " + (fingerprinting ? "Fingerprinting" : "Scan") + " Details");

  useEffect(() => {
    setScan(null);
    reconService.findScan(uuid, organizationId, tenantId, setScan, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, revision]);

  useEffect(() => {
    setSummary(null);
    reconService.findScanSummary(uuid, organizationId, tenantId, setSummary);
  }, [uuid, organizationId, tenantId, revision]);

  useEffect(() => {
    setTargets(null);
    reconService.findScanTargets(uuid, organizationId, tenantId, targetsFilters, targetsOrderColumn,
      targetsOrderDirection, targetsPerPage, (targetsPage-1)*targetsPerPage, setTargets);
  }, [uuid, organizationId, tenantId, targetsFilters, targetsOrderColumn, targetsOrderDirection, targetsPage, revision]);

  const applyTargetsFilters = (newFilters) => {
    setTargetsPage(1);
    setTargetsFilters(newFilters);
  }

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this scan? It will be detected again as a new scan if the device keeps probing.")) {
      return;
    }

    reconService.deleteScan(uuid, organizationId, tenantId, () => {
      toast.success("Scan deleted.");
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={listRoute} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Scan not found. <Link to={listRoute}>Back to {listName}</Link>
      </div>
    )
  }

  if (!scan) {
    return <LoadingSpinner />
  }

  const share = (count) => {
    if (!scan.probe_count) {
      return "";
    }

    return " (" + numeral(count / scan.probe_count).format("0%") + ")";
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.RECON.SCANS.INDEX}>Recon</Link></li>
              <li className="breadcrumb-item"><Link to={listRoute}>{listName}</Link></li>
              <li className="breadcrumb-item active" aria-current="page">
                {scan.type_human_readable} from {scan.source}
              </li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>
            <ReconScanType type={scan.type} /> from <span className="machine-data">{scan.source}</span>
          </h1>
        </div>

        <div className="col-4 text-end">
          {canEdit ?
            <React.Fragment>
              <Link to={ApiRoutes.ETHERNET.RECON.SCANS.EXCLUSIONS.CREATE + "?scan=" + scan.uuid}
                 className="btn btn-secondary me-1"
                 title="Create an exclusion so this device never shows up as a scanner again">
                Exclude
              </Link>
              <a href="#" className="btn btn-danger me-1" onClick={onDelete}>Delete Scan</a>
            </React.Fragment> : null}
          <Link to={listRoute} className="btn btn-primary">Back to {listName}</Link>
        </div>
      </div>

      {scan.matching_exclusions && scan.matching_exclusions.length > 0 ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-warning mb-0">
              <strong>Excluded.</strong> This scan matches the{' '}
              {scan.matching_exclusions.length === 1 ? "exclusion" : "exclusions"}{' '}
              {scan.matching_exclusions.map((ex, i) => {
                return (
                  <React.Fragment key={ex.uuid}>
                    {i > 0 ? ", " : ""}
                    <Link to={ApiRoutes.ETHERNET.RECON.SCANS.EXCLUSIONS.DETAILS(ex.uuid)} className="alert-link">&quot;{ex.name}&quot;</Link>
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
              <CardTitleWithControls title="Scanner" />

              <dl className="mb-0">
                <dt>Address</dt>
                <dd>
                  {scan.source_address_data ?
                    <L4Address address={scan.source_address_data} hidePort={true} withAssetName />
                    : <span className="text-muted">Unknown</span>}
                </dd>
                <dt>MAC Address</dt>
                <dd className="mb-0">
                  {scan.source_mac_data ?
                    <EthernetMacAddress addressWithContext={scan.source_mac_data} withAssetLink withAssetName showOui />
                    : <span className="text-muted">Unknown</span>}
                </dd>
              </dl>
            </div>
          </div>

          <div className="card mt-3">
            <div className="card-body">
              <CardTitleWithControls title="Probe Details" />

              <ReconScanEnrichment scan={scan} />
            </div>
          </div>
        </div>

        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Type</dt>
                <dd><ReconScanType type={scan.type} /></dd>
                <dt>Status</dt>
                <dd>
                  {scan.recent ?
                    <span className="text-danger">
                      Probed new targets in the last {scan.detection_window_minutes} minutes. Further targets
                      probed by this device are added to this scan.
                    </span>
                    : <span className="text-muted">
                      No new targets probed in the last {scan.detection_window_minutes} minutes. Repeated probes
                      of known targets do not count. Further targets probed by this device would start a new scan.
                    </span>}
                </dd>
                <dt>Target</dt>
                <dd>
                  {scan.target_address_data ?
                    <L4Address address={scan.target_address_data} hidePort={true} withAssetName />
                    : <span>{numeral(scan.host_count).format("0,0")} hosts</span>}
                </dd>
                {scan.type !== "ARP_SWEEP" && scan.type !== "ICMP_SWEEP" && scan.type !== "OS_FINGERPRINTING" ?
                  <React.Fragment>
                    <dt>Distinct Ports</dt>
                    <dd>{numeral(scan.port_count).format("0,0")}</dd>
                  </React.Fragment> : null}
                <dt>Probes</dt>
                <dd>
                  {numeral(scan.probe_count).format("0,0")}
                  {scan.targets_truncated ?
                    <span className="text-muted"> (very large scan, only a sample of the unanswered targets is stored)</span>
                    : null}
                </dd>
                <dt>Answered</dt>
                <dd className="text-success">{numeral(scan.open_count).format("0,0")}{share(scan.open_count)}</dd>
                {scan.type.startsWith("TCP") || scan.closed_count > 0 ?
                  <React.Fragment>
                    <dt>Refused</dt>
                    <dd className="text-warning">
                      {numeral(scan.closed_count).format("0,0")}{share(scan.closed_count)}
                      {scan.type.startsWith("UDP") ? <span className="text-muted"> (confirmed by ICMP port unreachable)</span> : null}
                    </dd>
                  </React.Fragment> : null}
                <dt>Unanswered</dt>
                <dd>{numeral(scan.unanswered_count).format("0,0")}{share(scan.unanswered_count)}</dd>
                <dt>Observed by Taps {scan.taps.length > 1 ? <span className="text-muted">({scan.taps.length})</span> : null}</dt>
                <dd>
                  {scan.taps.length === 0 ? <span className="text-muted">n/a</span> :
                    <ul className="list-unstyled mb-0" style={{maxHeight: 120, overflowY: "auto"}}>
                      {scan.taps.map((t) => {
                        return (
                          <li key={t.uuid}>
                            <Link to={ApiRoutes.SYSTEM.TAPS.DETAILS(t.uuid)}>{t.name ? t.name : t.uuid}</Link>
                          </li>
                        )
                      })}
                    </ul>}
                </dd>
                <dt>First Seen</dt>
                <dd title={moment(scan.first_seen).format()}>{moment(scan.first_seen).fromNow()}</dd>
                <dt>Last Seen</dt>
                <dd title={moment(scan.last_seen).format()} className="mb-0">{moment(scan.last_seen).fromNow()}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="What the Scanner Found"
                                     refreshAction={() => setRevision(new Date())} />

              <ReconScanFindings scan={scan} summary={summary} />
            </div>
          </div>
        </div>

        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title={scan.is_port_scan ? "Port Ranges" : "Targeted Networks"}
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                {scan.is_port_scan ?
                  "Which port ranges were tried. A top-ports check stays in the well-known range, a full range scan covers all three."
                  : "Networks the sweep covered, with how many hosts were probed and how many answered in each."}
              </p>

              <ReconScanTargetBreakdown scan={scan} summary={summary} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Scan Activity"
                                     refreshAction={() => setRevision(new Date())} />

              <ReconScanTimeline scan={scan} summary={summary} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Targets"
                                     refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">
                Every host and port the device probed and how the target reacted.
              </p>

              <div className="mb-4">
                <Filters filters={targetsFilters}
                         setFilters={applyTargetsFilters}
                         fields={RECON_SCAN_TARGETS_FILTER_FIELDS}
                         onMonitorsReady={() => {}} />
              </div>

              <ReconScanTargetsTable scan={scan}
                                     targets={targets}
                                     page={targetsPage}
                                     setPage={setTargetsPage}
                                     perPage={targetsPerPage}
                                     orderColumn={targetsOrderColumn}
                                     setOrderColumn={(c) => { setTargetsPage(1); setTargetsOrderColumn(c); }}
                                     orderDirection={targetsOrderDirection}
                                     setOrderDirection={(d) => { setTargetsPage(1); setTargetsOrderDirection(d); }}
                                     filterFields={RECON_SCAN_TARGETS_FILTER_FIELDS}
                                     setFilters={applyTargetsFilters} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
