import React, {useContext, useEffect, useState} from "react";
import {Link, Navigate, useParams} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import {toast} from "react-toastify";
import ApiRoutes from "../../../../util/ApiRoutes";
import usePageTitle from "../../../../util/UsePageTitle";
import useSelectedTenant from "../../../system/tenantselector/useSelectedTenant";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import DDTimestamp from "../../../misc/DDTimestamp";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import Filters from "../../../shared/filtering/Filters";
import {Presets} from "../../../shared/timerange/TimeRange";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import {UserContext} from "../../../../App";
import {apiErrorMessage, filtersApplied, userHasPermission} from "../../../../util/Tools";
import {POLICY_BACKFILL_NOTICE, POLICY_STATES, POLICY_STATUSES} from "../SegmentationFormat";
import {isBehindLastPage} from "../SegmentationBulkActions";
import useBackfillPoll from "../useBackfillPoll";
import {alertSettingDescription, normalizeRule, policyRequest} from "./PolicyDefinition";
import PolicyRuleText from "./PolicyRuleText";
import PolicyState from "./PolicyState";
import PolicyAllowedCrossings from "./PolicyAllowedCrossings";
import PolicyObservedTraffic from "./PolicyObservedTraffic";
import SegmentationFindingsTable from "../findings/SegmentationFindingsTable";
import {SEGMENTATION_POLICY_FINDINGS_FILTER_FIELDS} from "../findings/SegmentationFindingsFilterFields";

const segmentationService = new SegmentationService();

const STATUS_ACTIONS = [
  {status: "ACTIVE", title: "Activate", done: "Policy activated.", className: "btn-success"},
  {status: "AUDIT", title: "Set to Audit", done: "Policy set to audit.", className: "btn-secondary"},
  {status: "PAUSED", title: "Pause", done: "Policy paused.", className: "btn-warning"}
];

const DELETE_HINT = "Deletes the policy and its findings.";

export default function SegmentationPolicyDetailsPage() {

  const {uuid} = useParams();
  const [organizationId, tenantId] = useSelectedTenant();
  const user = useContext(UserContext);
  const canEdit = userHasPermission(user, "ethernet_monitoring_manage");

  const [timeRange, setTimeRange] = useState(Presets.RELATIVE_HOURS_24);

  const [policy, setPolicy] = useState(null);
  const [options, setOptions] = useState(null);
  // The filters of the findings are not the filters of the page, so they are not kept in the URL.
  const [findings, setFindings] = useState(null);
  const [findingsFilters, setFindingsFilters] = useState({});
  const [findingsOrderColumn, setFindingsOrderColumn] = useState("last_seen");
  const [findingsOrderDirection, setFindingsOrderDirection] = useState("DESC");
  const [findingsPage, setFindingsPage] = useState(1);
  const [unusedAllowed, setUnusedAllowed] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [redirect, setRedirect] = useState(false);
  const [revision, setRevision] = useState(new Date());

  const findingsPerPage = 25;

  const findingsFiltered = filtersApplied(findingsFilters);

  usePageTitle(policy ? `Segmentation Policy: ${policy.name}` : "Segmentation Policy Details");

  const applyTimeRange = (newTimeRange) => {
    setFindingsPage(1);
    setTimeRange(newTimeRange);
  }

  const applyFindingsFilters = (newFilters) => {
    setFindingsPage(1);
    setFindingsFilters(newFilters ? newFilters : {});
  }

  // The policy is not reset while it is loaded again: a new time range should not blank the whole page.
  useEffect(() => {
    segmentationService.findPolicy(uuid, organizationId, tenantId, timeRange, setPolicy, () => setNotFound(true));
  }, [uuid, organizationId, tenantId, timeRange, revision]);

  useBackfillPoll(JSON.stringify([uuid, organizationId, tenantId, timeRange]),
    Boolean(policy && policy.backfill_pending), (received) => {
      segmentationService.findPolicy(uuid, organizationId, tenantId, timeRange, (updated) => {
        if (!received()) return;

        if (updated.backfill_pending) {
          setPolicy(updated);
        } else {
          setRevision(new Date());
        }
      }, (error) => {
        if (received() && error.response.status === 404) {
          setNotFound(true);
        }
      });
    });

  useEffect(() => {
    segmentationService.findSelectorOptions(organizationId, tenantId, setOptions);
  }, [organizationId, tenantId, revision]);

  useEffect(() => {
    setFindings(null);
    segmentationService.findAllFindings(organizationId, tenantId, findingsFilters, timeRange, uuid,
      findingsOrderColumn, findingsOrderDirection, findingsPerPage, (findingsPage-1)*findingsPerPage, (response) => {
        if (isBehindLastPage(response.findings, response.total, findingsPage)) {
          setFindingsPage(Math.ceil(response.total / findingsPerPage));
        } else {
          setFindings(response);
        }
      });
  }, [uuid, organizationId, tenantId, findingsFilters, timeRange, findingsOrderColumn, findingsOrderDirection,
    findingsPage, revision]);

  const onSetStatus = (e, action) => {
    e.preventDefault();

    segmentationService.updatePolicy(uuid, organizationId, tenantId, policyRequest(policy, {status: action.status}), () => {
      toast.success(action.done);
      setRevision(new Date());
    }, (error) => {
      toast.error(apiErrorMessage(error, "Could not change the status of the policy."));
    });
  }

  const onDelete = (e) => {
    e.preventDefault();

    if (!confirm("Really delete this policy? Its findings are deleted too. This cannot be undone.")) {
      return;
    }

    segmentationService.deletePolicy(uuid, organizationId, tenantId, () => {
      toast.success("Policy deleted.");
      setRedirect(true);
    });
  }

  if (redirect) {
    return <Navigate to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.INDEX} />
  }

  if (notFound) {
    return (
      <div className="alert alert-warning">
        Policy not found. <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.INDEX}>Back to Policies</Link>
      </div>
    )
  }

  if (!policy || !options) {
    return <LoadingSpinner />
  }

  const scope = normalizeRule(policy.scope);
  const allowed = policy.allowed ? policy.allowed.map(normalizeRule) : [];

  const findingsBulk = canEdit ? {
    organizationId: organizationId,
    tenantId: tenantId,
    filters: findingsFilters,
    timeRange: timeRange,
    policy: uuid,
    countAll: (onCount, onError) => {
      segmentationService.findAllFindings(organizationId, tenantId, null, timeRange, uuid, findingsOrderColumn,
        findingsOrderDirection, 1, 0, (response) => onCount(response.total), onError);
    },
    onDone: () => setRevision(new Date())
  } : null;

  const state = POLICY_STATES[policy.state];
  const status = POLICY_STATUSES[policy.status];
  const alerts = policy.alert_on_connected || policy.alert_on_attempted;

  return (
    <React.Fragment>
      <div className="row">
        <div className="col-md-12">
          <nav aria-label="breadcrumb">
            <ol className="breadcrumb">
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.OVERVIEW}>Ethernet</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SEGMENTATION.INDEX}>Segmentation</Link></li>
              <li className="breadcrumb-item"><Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.INDEX}>Policies</Link></li>
              <li className="breadcrumb-item active" aria-current="page">{policy.name}</li>
            </ol>
          </nav>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-6">
          <h1>Policy &quot;{policy.name}&quot;</h1>
        </div>

        <div className="col-6 text-end">
          {canEdit ?
            <React.Fragment>
              {STATUS_ACTIONS.filter((action) => action.status !== policy.status).map((action) => {
                return (
                  <a href="#" key={action.status} className={"btn me-1 " + action.className}
                     title={POLICY_STATUSES[action.status].description}
                     onClick={(e) => onSetStatus(e, action)}>
                    {action.title}
                  </a>
                )
              })}
              <a href="#" className="btn btn-danger me-1" onClick={onDelete} title={DELETE_HINT}>Delete Policy</a>
              <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.EDIT(policy.uuid)} className="btn btn-primary me-1">
                Edit
              </Link>
            </React.Fragment> : null}
          <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.INDEX} className="btn btn-secondary">Back</Link>
        </div>
      </div>

      {policy.backfill_pending ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-info mb-0">
              <i className="fa-solid fa-circle-notch fa-spin"></i> {POLICY_BACKFILL_NOTICE}
            </div>
          </div>
        </div> : null}

      {policy.findings_limit_reached ?
        <div className="row mt-3">
          <div className="col-12">
            <div className="alert alert-warning mb-0">
              Limit of {numeral(policy.findings_limit).format("0,0")} findings reached. New sources are not
              recorded until findings are dismissed or age out.
            </div>
          </div>
        </div> : null}

      <div className="row mt-3">
        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Definition" />

              <dl className="mb-0">
                <dt>Description</dt>
                <dd>
                  {policy.description ?
                    <span style={{whiteSpace: "pre-wrap"}}>{policy.description}</span>
                    : <span className="text-muted">No description.</span>}
                </dd>
                <dt>Scope</dt>
                <dd title="The connections this policy judges">
                  <PolicyRuleText rule={scope} options={options} scope={true} />
                </dd>
                <dt>Allowed Crossings</dt>
                <dd className="mb-0">
                  <PolicyAllowedCrossings allowed={allowed} options={options} unused={unusedAllowed} />
                </dd>
              </dl>
            </div>
          </div>
        </div>

        <div className="col-6">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="State"
                                     timeRange={timeRange}
                                     setTimeRange={applyTimeRange}
                                     doNotPersistTimeRange={true}
                                     refreshAction={() => setRevision(new Date())} />

              <dl className="mb-0">
                <dt>State</dt>
                <dd title="State in the selected time range">
                  <PolicyState state={policy.state} />{' '}
                  {state ? <span className="text-muted">{state.description}</span> : null}
                </dd>
                <dt>Status</dt>
                <dd>
                  {status ? status.title : policy.status}{' '}
                  {status ? <span className="text-muted">{status.description}</span> : null}
                </dd>
                <dt>Alerts</dt>
                <dd>
                  {alerts ? alertSettingDescription(policy) : <span className="text-muted">{alertSettingDescription(policy)}</span>}
                  {alerts && policy.status !== "ACTIVE" ?
                    <span className="text-muted"> None are raised while the policy is not active.</span> : null}
                </dd>
                <dt>Findings in Time Range</dt>
                <dd>
                  <span className={policy.crossed_findings > 0 ? "text-danger" : ""}>
                    {numeral(policy.crossed_findings).format("0,0")} crossed
                  </span>
                  {policy.last_crossed ?
                    <span className="text-muted" title={moment(policy.last_crossed).format()}> (last {moment(policy.last_crossed).fromNow()})</span>
                    : null}
                  , {numeral(policy.attempted_findings).format("0,0")} attempted
                  {policy.last_attempted ?
                    <span className="text-muted" title={moment(policy.last_attempted).format()}> (last {moment(policy.last_attempted).fromNow()})</span>
                    : null}
                </dd>
                <dt>Created</dt>
                <DDTimestamp timestamp={policy.created_at} />
                <dt>Last Updated</dt>
                <dd className="mb-0" title={moment(policy.updated_at).format()}>{moment(policy.updated_at).fromNow()}</dd>
              </dl>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Findings"
                                     timeRange={timeRange}
                                     setTimeRange={applyTimeRange}
                                     doNotPersistTimeRange={true}
                                     refreshAction={() => setRevision(new Date())} />

              <div className="mb-4">
                <Filters filters={findingsFilters}
                         setFilters={applyFindingsFilters}
                         fields={SEGMENTATION_POLICY_FINDINGS_FILTER_FIELDS}
                         embedded={true}
                         onMonitorsReady={() => {}} />
              </div>

              {findings ? <p className="mb-3"><strong>Total:</strong> {numeral(findings.total).format("0,0")}</p> : null}

              <SegmentationFindingsTable findings={findings}
                                         page={findingsPage}
                                         setPage={setFindingsPage}
                                         perPage={findingsPerPage}
                                         orderColumn={findingsOrderColumn}
                                         setOrderColumn={(c) => { setFindingsPage(1); setFindingsOrderColumn(c); }}
                                         orderDirection={findingsOrderDirection}
                                         setOrderDirection={(d) => { setFindingsPage(1); setFindingsOrderDirection(d); }}
                                         filterFields={SEGMENTATION_POLICY_FINDINGS_FILTER_FIELDS}
                                         filters={findingsFilters}
                                         setFilters={applyFindingsFilters}
                                         bulk={findingsBulk}
                                         policyFixed={true}
                                         emptyMessage={findingsFiltered
                                           ? "No findings of this policy match the current filters and time range."
                                           : "No findings of this policy in the selected time range."
                                             + (policy.state === "NOT_OBSERVABLE" ? " " + POLICY_STATES.NOT_OBSERVABLE.description : "")} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <PolicyObservedTraffic organizationId={organizationId}
                                     tenantId={tenantId}
                                     scope={scope}
                                     allowed={allowed}
                                     description={"Recorded traffic in scope, evaluated against the current " +
                                       "definition. Includes traffic from before the policy existed."}
                                     onResult={(result) => setUnusedAllowed(result ? result.unused_allowed : null)} />
            </div>
          </div>
        </div>
      </div>
    </React.Fragment>
  )

}
