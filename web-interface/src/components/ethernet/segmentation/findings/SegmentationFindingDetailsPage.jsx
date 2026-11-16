import React, {useContext, useEffect, useRef, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import numeral from "numeral";
import {toast} from "react-toastify";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import DDTimestamp from "../../../misc/DDTimestamp";
import ApiRoutes from "../../../../util/ApiRoutes";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import usePageTitle from "../../../../util/UsePageTitle";
import {UserContext} from "../../../../App";
import {apiErrorMessage, userHasPermission} from "../../../../util/Tools";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import TapsService from "../../../../services/TapsService";
import {FINDING_CLASSES, tapName, targetCountNote, targetCountText} from "../SegmentationFormat";
import FindingClass, {AUDIT_NOTE} from "./FindingClass";
import {DISMISS_NOTE, FindingSegment, FindingSource} from "./SegmentationFindingsTable";
import SegmentationFindingTargetsTable from "./SegmentationFindingTargetsTable";
import {SEGMENTATION_FINDING_TARGETS_FILTER_FIELDS} from "./SegmentationFindingTargetsFilterFields";
import AllowCrossingDialog from "./AllowCrossingDialog";

const segmentationService = new SegmentationService();
const tapsService = new TapsService();

export default function SegmentationFindingDetailsPage() {

  const {uuid} = useParams();

  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [finding, setFinding] = useState(null);
  const [taps, setTaps] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [allowTarget, setAllowTarget] = useState(null);
  const [revision, setRevision] = useState(new Date());

  // The filters of the targets are not the filters of the page, so they are not kept in the URL.
  const [targets, setTargets] = useState(null);
  const [targetsFilters, setTargetsFilters] = useState({});
  const [targetsOrderColumn, setTargetsOrderColumn] = useState("last_seen");
  const [targetsOrderDirection, setTargetsOrderDirection] = useState("DESC");
  const [targetsPage, setTargetsPage] = useState(1);
  const targetsPerPage = 25;

  const allowedTarget = useRef(false);

  usePageTitle(finding ? `Segmentation Finding: ${finding.source_label}` : "Ethernet Segmentation Finding Details");

  useEffect(() => {
    setFinding(null);
    segmentationService.findFinding(uuid, organizationId, tenantId, setFinding, () => {
      if (allowedTarget.current) {
        toast.info("All targets are allowed. Finding removed.");
        setRedirect(true);
      } else {
        setNotFound(true);
      }
    });
  }, [uuid, organizationId, tenantId, revision]);

  useEffect(() => {
    setTargets(null);
    segmentationService.findFindingTargets(uuid, organizationId, tenantId, targetsFilters, targetsOrderColumn,
      targetsOrderDirection, targetsPerPage, (targetsPage-1)*targetsPerPage, setTargets, (error) => {
        // If the finding is gone, the request for the finding itself says so.
        if (error.response.status !== 404) {
          toast.error(apiErrorMessage(error, "Could not load the targets of the finding."));
        }
      });
  }, [uuid, organizationId, tenantId, targetsFilters, targetsOrderColumn, targetsOrderDirection, targetsPage, revision]);

  useEffect(() => {
    tapsService.findAllTapsHighLevel(organizationId, tenantId, (r) => setTaps(r.data.taps));
  }, [organizationId, tenantId]);

  const applyTargetsFilters = (newFilters) => {
    setTargetsPage(1);
    setTargetsFilters(newFilters ? newFilters : {});
  }

  const onDismiss = (e) => {
    e.preventDefault();

    if (!confirm("Really dismiss this finding? " + DISMISS_NOTE)) {
      return;
    }

    segmentationService.deleteFinding(uuid, organizationId, tenantId, () => {
      toast.success("Finding dismissed.");
      setRedirect(true);
    });
  }

  const onAllowed = () => {
    toast.success("Allowed crossing added to the policy.");
    setAllowTarget(null);
    allowedTarget.current = true;

    setTargetsPage(1);
    setRevision(new Date());
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.SEGMENTATION.FINDINGS.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Finding not found. <Link to={ApiRoutes.ETHERNET.SEGMENTATION.FINDINGS.INDEX}>Back to Findings</Link>
      </div>
    )
  }

  if (!finding) {
    return <LoadingSpinner />
  }

  const f = finding;
  const findingClass = FINDING_CLASSES[f.finding_class];

  const tapNames = () => {
    if (!f.tap_uuids || f.tap_uuids.length === 0) {
      return <span className="text-muted">n/a</span>
    }

    return f.tap_uuids.map((tapUuid) => tapName(taps, tapUuid, "Unknown tap")).join(", ");
  }

  const sourceRows = [
    {title: "Source", value: <FindingSource finding={f} />},
    {title: "Segment", value: <FindingSegment name={f.source_segment_name} internal={f.source_internal} />}
  ];

  if (f.source_asn) {
    sourceRows.push({title: "Autonomous System", value: "AS" + f.source_asn});
  }

  if (f.source_country_code) {
    sourceRows.push({title: "Country", value: f.source_country_code});
  }

  if (!f.source_address) {
    sourceRows.push({
      title: "Sample Addresses",
      hint: "Sample of the addresses seen, " + f.source_addresses_limit + " at most.",
      value: f.source_addresses && f.source_addresses.length > 0
        ? f.source_addresses.map((a) => <div key={a}><span className="machine-data">{a}</span></div>)
        : <span className="text-muted">None recorded</span>
    });
  }

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SEGMENTATION.INDEX}>Segmentation</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SEGMENTATION.FINDINGS.INDEX}>Findings</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{f.source_label} against {f.policy_name}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-8">
          <h1>
            <span className="machine-data">{f.source_label}</span>{' '}
            {f.finding_class === "CROSSED" ? "crossed" : "attempted to cross"} policy &quot;{f.policy_name}&quot;
          </h1>
        </div>

        <div className="col-4 text-end">
          {canEdit ?
            <a href="#" className="btn btn-danger me-1" onClick={onDismiss}
               title={"Deletes the finding. " + DISMISS_NOTE}>
              Dismiss
            </a> : null}
          <Link to={ApiRoutes.ETHERNET.SEGMENTATION.FINDINGS.INDEX} className="btn btn-primary">
            Back to Findings
          </Link>
        </div>
      </div>

      {f.audit ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-info mb-0">
              <strong>Audit.</strong> {AUDIT_NOTE}
            </div>
          </div>
        </div> : null}

      <div className="row mt-3">
        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Details" />

              <dl className="mb-0">
                <dt>Policy</dt>
                <dd>
                  <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(f.policy_uuid)}>{f.policy_name}</Link>
                </dd>
                <dt>Class</dt>
                <dd>
                  <FindingClass findingClass={f.finding_class} audit={f.audit} />{' '}
                  {findingClass ? <span className="text-muted">{findingClass.description}</span> : null}
                </dd>
                <dt>Sessions</dt>
                <dd>{numeral(f.session_count).format("0,0")}</dd>
                <dt>Targets</dt>
                <dd className="mb-0" title={targetCountNote(f)}>{targetCountText(f)}</dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Source" />

              <dl className="mb-0">
                {sourceRows.map((row, i) => {
                  return (
                    <React.Fragment key={row.title}>
                      <dt>{row.title}</dt>
                      <dd className={i === sourceRows.length - 1 ? "mb-0" : undefined} title={row.hint}>{row.value}</dd>
                    </React.Fragment>
                  )
                })}
              </dl>
            </div>
          </div>
        </div>

        <div className="col-4">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Metadata" />

              <dl className="mb-0">
                <dt>First Seen</dt>
                <DDTimestamp timestamp={f.first_seen} />
                <dt>Last Seen</dt>
                <DDTimestamp timestamp={f.last_seen} />
                <dt>Observed by Taps</dt>
                <dd className="mb-0">{tapNames()}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Targets" refreshAction={() => setRevision(new Date())} />

              <p className="text-muted">Targets the source reached, sent to or tried to reach.</p>

              {f.target_count > f.targets_limit ?
                <div className="alert alert-warning">
                  Limited to the {numeral(f.targets_limit).format("0,0")} most recent targets.
                </div> : null}

              <div className="mb-4">
                <Filters filters={targetsFilters}
                         setFilters={applyTargetsFilters}
                         fields={SEGMENTATION_FINDING_TARGETS_FILTER_FIELDS}
                         embedded={true}
                         onMonitorsReady={() => {}} />
              </div>

              <SegmentationFindingTargetsTable finding={f}
                                               targets={targets}
                                               page={targetsPage}
                                               setPage={setTargetsPage}
                                               perPage={targetsPerPage}
                                               orderColumn={targetsOrderColumn}
                                               setOrderColumn={(c) => { setTargetsPage(1); setTargetsOrderColumn(c); }}
                                               orderDirection={targetsOrderDirection}
                                               setOrderDirection={(d) => { setTargetsPage(1); setTargetsOrderDirection(d); }}
                                               filterFields={SEGMENTATION_FINDING_TARGETS_FILTER_FIELDS}
                                               filters={targetsFilters}
                                               setFilters={applyTargetsFilters}
                                               onAllow={canEdit ? setAllowTarget : undefined} />
            </div>
          </div>
        </div>
      </div>

      {allowTarget ?
        <AllowCrossingDialog organizationId={organizationId}
                             tenantId={tenantId}
                             finding={f}
                             target={allowTarget}
                             onClose={() => setAllowTarget(null)}
                             onComplete={onAllowed} /> : null}
    </React.Fragment>
  )

}
