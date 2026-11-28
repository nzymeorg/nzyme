import React, {useContext, useEffect, useMemo, useRef, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import moment from "moment";
import {toast} from "react-toastify";
import usePageTitle from "../../../util/UsePageTitle";
import {UserContext} from "../../../App";
import useSelectedTenant from "../../system/tenantselector/useSelectedTenant";
import PortalIntegrityService from "../../../services/ethernet/PortalIntegrityService";
import ApiRoutes from "../../../util/ApiRoutes";
import CardTitleWithControls from "../../shared/CardTitleWithControls";
import LoadingSpinner from "../../misc/LoadingSpinner";
import {Presets} from "../../shared/timerange/TimeRange";
import {useUrlTimeRange} from "../../shared/timerange/TimeRangeSelector";
import {queryParametersToFilters} from "../../shared/filtering/FilterQueryParameters";
import {truncate, userHasPermission} from "../../../util/Tools";
import IPAddressLink from "../shared/IPAddressLink";
import TLSCertificateFingerprint from "../tls/TLSCertificateFingerprint";
import {PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS} from "./PortalIntegrityReportsFilterFields";
import PortalIntegrityReportsTable from "./PortalIntegrityReportsTable";
import PortalIntegrityExpectationForm, {draftValues, expectationDraft} from "./PortalIntegrityExpectationForm";
import PortalIntegrityObservedState from "./PortalIntegrityObservedState";
import PortalIntegrityResult, {portalExpectationToName, portalReasonsToText} from "./PortalIntegrityResult";

const portalIntegrityService = new PortalIntegrityService();

const valueList = (values) => {
  if (!values || values.length === 0) {
    return <span className="text-muted">Not checked</span>
  }

  return (
    <ul className="mb-0 p-0" style={{listStyleType: "none"}}>
      {values.map((v) => <li key={v}><span className="machine-data text-break">{v}</span></li>)}
    </ul>
  )
}

export default function PortalIntegrityControlUrlDetailsPage() {

  usePageTitle("Portal Integrity Control URL");

  const { uuid } = useParams();

  const user = useContext(UserContext);
  const canManage = userHasPermission(user, "ethernet_monitoring_manage");

  const [organizationId, tenantId] = useSelectedTenant();

  const [controlUrl, setControlUrl] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const [timeRange, setTimeRange] = useUrlTimeRange(Presets.RELATIVE_HOURS_24);
  const [revision, setRevision] = useState(new Date());

  // The expectation while it is edited. Null shows the saved one.
  const [draft, setDraft] = useState(null);
  const [imports, setImports] = useState(0);
  const [forgetting, setForgetting] = useState(false);
  const [forgotten, setForgotten] = useState(false);
  const expectationCard = useRef(null);

  // The page stays mounted when the control URL in the address changes. Answers for the previous one are dropped.
  const shown = useRef(null);
  shown.current = uuid + "/" + organizationId + "/" + tenantId;

  useEffect(() => {
    setControlUrl(null);
    setNotFound(false);
    setLoadError(null);
    setDraft(null);
    setForgetting(false);
    setForgotten(false);
  }, [uuid, organizationId, tenantId]);

  useEffect(() => {
    const requested = shown.current;

    portalIntegrityService.findOneControlUrl(uuid, organizationId, tenantId, (response) => {
      if (shown.current === requested) {
        setControlUrl(response);
      }
    }, (error) => {
      if (shown.current !== requested) {
        return;
      }

      if (error.response.status === 404) {
        setNotFound(true);
      } else {
        setLoadError(error.response.status);
      }
    });
  }, [uuid, organizationId, tenantId, revision]);

  // After the form of an import has rendered. The card is much taller before than after for long lists.
  useEffect(() => {
    if (imports > 0 && expectationCard.current) {
      expectationCard.current.scrollIntoView({behavior: "smooth", block: "nearest"});
    }
  }, [imports]);

  const probeName = controlUrl ? controlUrl.probe_name : null;
  const url = controlUrl ? controlUrl.control_url : null;
  const ipVersion = controlUrl ? controlUrl.ip_version : null;
  const tapUuid = controlUrl ? controlUrl.tap_uuid : null;

  const reportFilters = useMemo(() => {
    if (!probeName) {
      return null;
    }

    return queryParametersToFilters(JSON.stringify({
      probe_name: [{field: "probe_name", operator: "equals", value: probeName}],
      control_url: [{field: "control_url", operator: "equals", value: url}],
      ip_version: [{field: "ip_version", operator: "equals_numeric", value: ipVersion}]
    }), PORTAL_INTEGRITY_REPORTS_FILTER_FIELDS);
  }, [probeName, url, ipVersion]);

  const reportTaps = useMemo(() => tapUuid ? [tapUuid] : [], [tapUuid]);

  const savedValues = () => {
    return {
      expectation: controlUrl.expectation,
      urls: controlUrl.expected_final_urls,
      addresses: controlUrl.expected_addresses,
      certificates: controlUrl.expected_certificates
    }
  }

  /*
   * Puts what was observed into the form and saves nothing. A control URL without an expected portal takes
   * everything of it. One that has an expected portal keeps a property unchecked that it does not check: an
   * address added to an empty list would turn the portals that are expected already into wrong ones.
   */
  const onImport = (outcomes) => {
    const merge = (current, added) => [...new Set([...current, ...added.filter((v) => v)])];

    const base = draft ? draftValues(draft) : savedValues();

    let imported;
    if (outcomes[0].kind === "NO_PORTAL") {
      imported = expectationDraft("NO_PORTAL", base.urls, base.addresses, base.certificates, true);
    } else {
      const fresh = base.expectation !== "PORTAL" || base.urls.length === 0;
      const urls = merge(fresh ? [] : base.urls, outcomes.map((o) => o.final_url));
      const addresses = fresh || base.addresses.length > 0
        ? merge(fresh ? [] : base.addresses, outcomes.map((o) => o.final_address)) : [];
      const certificates = fresh || base.certificates.length > 0
        ? merge(fresh ? [] : base.certificates, outcomes.map((o) => o.final_certificate)) : [];

      const limit = controlUrl.expected_values_limit;
      if (urls.length > limit || addresses.length > limit || certificates.length > limit) {
        toast.error("An expectation takes up to " + limit + " values per list. Import fewer captive portals.");
        return;
      }

      imported = expectationDraft("PORTAL", urls, addresses, certificates, true);
    }

    setDraft(imported);
    setImports((n) => n + 1);
  }

  const onEdit = (e) => {
    e.preventDefault();

    const saved = savedValues();
    setDraft(expectationDraft(saved.expectation, saved.urls, saved.addresses, saved.certificates));
  }

  const onSubmit = (expectation, onError) => {
    const requested = shown.current;

    portalIntegrityService.setExpectation(controlUrl.uuid, organizationId, tenantId, expectation, (response) => {
      toast.success("Expectation saved.");

      if (shown.current === requested) {
        setControlUrl(response.data);
        setDraft(null);
      }
    }, (error) => {
      if (shown.current === requested) {
        onError(error);
      }
    });
  }

  const onForget = (e) => {
    e.preventDefault();

    if (forgetting) {
      return;
    }

    if (!confirm("Really forget this control URL and its expectation? Reports are kept. It is recorded again, not " +
        "monitored, when the captive portal probe requests it again.")) {
      return;
    }

    const requested = shown.current;

    setForgetting(true);
    portalIntegrityService.deleteControlUrl(controlUrl.uuid, organizationId, tenantId, () => {
      toast.success("Control URL forgotten.");

      if (shown.current === requested) {
        setForgotten(true);
      }
    }, (error) => {
      toast.error("Could not forget the control URL." + (error.response ? " (HTTP " + error.response.status + ")" : ""));

      if (shown.current === requested) {
        setForgetting(false);
      }
    });
  }

  if (forgotten) {
    return <Navigate to={ApiRoutes.ETHERNET.PORTAL_INTEGRITY.INDEX} />
  }

  if (notFound || loadError) {
    return (
      <div className="alert alert-warning">
        {notFound ? "Control URL not found." : "Could not load the control URL. (HTTP " + loadError + ")"}{' '}
        <Link to={ApiRoutes.ETHERNET.PORTAL_INTEGRITY.INDEX}>Back to Portal Integrity</Link>
      </div>
    )
  }

  if (!controlUrl) {
    return <LoadingSpinner />
  }

  const c = controlUrl;

  const presentedPortal = () => {
    if (c.last_error) {
      return <span className="text-muted">n/a</span>
    }

    if (!c.last_portal_presented) {
      return "No Captive Portal"
    }

    return <span className="machine-data text-break">{c.last_final_url}</span>
  }

  const expectation = () => {
    if (draft) {
      return <PortalIntegrityExpectationForm draft={draft}
                                             setDraft={setDraft}
                                             saved={savedValues()}
                                             limit={c.expected_values_limit}
                                             onSubmit={onSubmit}
                                             onCancel={() => setDraft(null)} />
    }

    return (
      <>
        <dl className={canManage ? "" : "mb-0"}>
          <dt>Expectation</dt>
          <dd>{portalExpectationToName(c.expectation)}</dd>
          {c.expectation === "PORTAL" ?
            <>
              <dt>Final URLs</dt>
              <dd>{valueList(c.expected_final_urls)}</dd>
              <dt>Addresses</dt>
              <dd>{valueList(c.expected_addresses)}</dd>
              <dt>Certificate Fingerprints</dt>
              <dd>{valueList(c.expected_certificates)}</dd>
            </> : null}
        </dl>

        {canManage ?
          <button type="button" className="btn btn-sm btn-outline-secondary" onClick={onEdit}>
            Edit Expectation
          </button> : null}
      </>
    )
  }

  const shortUrl = truncate(c.control_url, 100, false);

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-8">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.PORTAL_INTEGRITY.INDEX}>Portal Integrity</Link></li>
              <li className="breadcrumb-item">Control URLs</li>
              <li className="breadcrumb-item text-break">{c.probe_name}</li>
              <li className="breadcrumb-item active text-break" aria-current="page" title={c.control_url}>{shortUrl}</li>
            </ol>
          </nav>
        </div>
        <div className="col-4 text-end">
          {canManage ?
            <a href="#" className={"btn btn-danger me-1" + (forgetting ? " disabled" : "")} onClick={onForget}>
              Forget Control URL
            </a> : null}
          <Link to={ApiRoutes.ETHERNET.PORTAL_INTEGRITY.INDEX} className="btn btn-primary">
            Back
          </Link>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <h1 className="text-break" title={c.control_url}>
            Control URL &quot;{shortUrl}&quot; of Captive Portal Probe &quot;{c.probe_name}&quot;
          </h1>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details"
                                     helpLink="https://go.nzyme.org/portal-integrity-control-url" />

              <dl className="mb-0">
                <dt>Captive Portal Probe</dt>
                <dd className="text-break">{c.probe_name}</dd>
                <dt>Tap</dt>
                <dd>{c.tap_name ? c.tap_name : <span className="text-muted">n/a</span>}</dd>
                <dt>Control URL</dt>
                <dd className="machine-data text-break">{c.control_url}</dd>
                <dt>IP Version</dt>
                <dd>IPv{c.ip_version}</dd>
                <dt>In Tap Configuration</dt>
                <dd>
                  {c.in_tap_configuration === null || c.in_tap_configuration === undefined ?
                    <span className="text-muted" title="The tap has not reported which captive portal probes it runs.">n/a</span>
                    : (c.in_tap_configuration ? "Yes" : "No")}
                </dd>
                <dt>First Report</dt>
                <dd title={moment(c.created_at).fromNow()}>{moment(c.created_at).format()}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-xl-6 mt-3 mt-xl-0">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Last Report"
                                     internalLink={c.last_report_uuid
                                         ? ApiRoutes.ETHERNET.PORTAL_INTEGRITY.REPORT_DETAILS(c.last_report_uuid) : undefined} />

              <dl className="mb-0">
                <dt>Status</dt>
                <dd>
                  <PortalIntegrityResult controlUrl={c} />
                  {c.result === "PORTAL_WRONG" ?
                    <span className="text-muted"> (differs in {portalReasonsToText(c.reasons)})</span> : null}
                </dd>
                <dt>Received</dt>
                <dd title={moment(c.last_report_at).format()}>{moment(c.last_report_at).fromNow()}</dd>
                {c.last_error ?
                  <>
                    <dt>Error</dt>
                    <dd className="text-break">{c.last_error}</dd>
                  </> : null}
                <dt>Presented Portal</dt>
                <dd>{presentedPortal()}</dd>
                <dt>Address</dt>
                <dd>
                  {c.last_final_address ? <IPAddressLink ip={c.last_final_address} full />
                    : <span className="text-muted">n/a</span>}
                </dd>
                <dt>Certificate</dt>
                <dd>
                  {c.last_final_certificate ? <TLSCertificateFingerprint sha256={c.last_final_certificate} />
                    : <span className="text-muted">n/a</span>}
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
              <CardTitleWithControls title="Observed State"
                                     helpLink="https://go.nzyme.org/portal-integrity-observed-state"
                                     timeRange={timeRange}
                                     setTimeRange={setTimeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <PortalIntegrityObservedState controlUrl={c}
                                            timeRange={timeRange}
                                            revision={revision}
                                            onImport={canManage ? onImport : undefined} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card" ref={expectationCard}>
            <div className="card-body">
              <CardTitleWithControls title="Expectation"
                                     helpLink="https://go.nzyme.org/portal-integrity-expectations" />

              {expectation()}
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Reports"
                                     timeRange={timeRange}
                                     refreshAction={() => setRevision(new Date())} />

              <PortalIntegrityReportsTable timeRange={timeRange}
                                           filters={reportFilters}
                                           taps={reportTaps}
                                           revision={revision} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
