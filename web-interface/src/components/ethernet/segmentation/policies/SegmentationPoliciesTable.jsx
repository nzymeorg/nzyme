import React from "react";
import {Link} from "react-router-dom";
import numeral from "numeral";
import moment from "moment";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import Paginator from "../../../misc/Paginator";
import ColumnSorting from "../../../shared/ColumnSorting";
import FilterValueIcon from "../../../shared/filtering/FilterValueIcon";
import ApiRoutes from "../../../../util/ApiRoutes";
import useRowSelection from "../../assets/identity/useRowSelection";
import {POLICY_BACKFILL_NOTICE, POLICY_STATUSES, policyFindingsLink} from "../SegmentationFormat";
import SegmentationBulkActions from "../SegmentationBulkActions";
import {
  alertSettingDescription,
  alertSettingLabel,
  EVERYTHING_IN_SCOPE_NOTE,
  isEverythingInScope,
  normalizeRule
} from "./PolicyDefinition";
import {policyBulkActions} from "./PolicyBulkActions";
import PolicyRuleText from "./PolicyRuleText";
import PolicyState from "./PolicyState";

const NOUNS = {one: "policy", many: "policies"};

const allowedText = (policy) => {
  if (!policy.allowed || policy.allowed.length === 0) {
    return <span className="text-muted">Nothing</span>
  }

  if (policy.allowed.some((rule) => isEverythingInScope(normalizeRule(rule)))) {
    return <span title={EVERYTHING_IN_SCOPE_NOTE}>Everything</span>
  }

  return numeral(policy.allowed.length).format("0,0");
}

const lastFinding = (policy) => {
  const times = [policy.last_crossed, policy.last_attempted].filter((t) => !!t).map((t) => moment(t));
  return times.length === 0 ? null : moment.max(times);
}

export default function SegmentationPoliciesTable(props) {

  if (!props.policies || !props.options) {
    return <LoadingSpinner />
  }

  return <LoadedPoliciesTable {...props} />

}

/*
 * A component of its own, so that it is mounted anew with every list that was loaded: what was checked does not
 * survive another page, other filters, another sorting or another time range.
 */
function LoadedPoliciesTable({policies, options, filtered, page, setPage, perPage, orderColumn, setOrderColumn,
                              orderDirection, setOrderDirection, filterFields, setFilters, bulk}) {

  const selection = useRowSelection(policies.policies);

  const columnSorting = (columnName) => {
    return <ColumnSorting thisColumn={columnName}
                          orderColumn={orderColumn}
                          setOrderColumn={setOrderColumn}
                          orderDirection={orderDirection}
                          setOrderDirection={setOrderDirection} />
  }

  const filterIcon = (field, value) => {
    return <FilterValueIcon setFilters={setFilters} fields={filterFields} field={field} value={value} />
  }

  if (policies.policies.length === 0 && filtered) {
    return <div className="alert alert-info mb-0">No policies match the current filters.</div>
  }

  if (policies.policies.length === 0) {
    return <div className="alert alert-info mb-0">No policies found.</div>
  }

  const findings = (policy, findingClass, count, className) => {
    if (count === 0) {
      return <span className="text-muted">0</span>
    }

    return (
      <Link to={policyFindingsLink(policy.name, findingClass)} className={className}
            title={"Show the " + findingClass.toLowerCase() + " findings of this policy"}>
        {numeral(count).format("0,0")}
      </Link>
    )
  }

  return (
    <React.Fragment>
      {bulk ?
        <SegmentationBulkActions nouns={NOUNS}
                                 actions={policyBulkActions(bulk)}
                                 selection={selection}
                                 total={policies.total}
                                 filters={bulk.filters}
                                 countAll={bulk.countAll}
                                 onDone={bulk.onDone} /> : null}

      <table className="table table-sm table-hover table-striped">
        <thead>
        <tr>
          {bulk ?
            <th>
              <input className="form-check-input" type="checkbox" title="Check all rows of this page"
                     checked={selection.allSelected} onChange={selection.toggleAll} />
            </th> : null}
          <th>Name {columnSorting("name")}</th>
          <th title="State in the selected time range">State</th>
          <th>Status {columnSorting("status")}</th>
          <th>Scope</th>
          <th title="Number of allowed crossings">Allowed</th>
          <th>Alerts On</th>
          <th title="Crossed findings that were active in the time range">Crossed {columnSorting("crossed_findings")}</th>
          <th title="Attempted findings that were active in the time range">Attempted {columnSorting("attempted_findings")}</th>
          <th>Last Finding {columnSorting("last_finding")}</th>
          <th>Created {columnSorting("created_at")}</th>
          <th>Updated {columnSorting("updated_at")}</th>
        </tr>
        </thead>
        <tbody>
        {policies.policies.map((policy) => {
          const status = POLICY_STATUSES[policy.status];
          const last = lastFinding(policy);

          return (
            <tr key={policy.uuid}>
              {bulk ?
                <td>
                  <input className="form-check-input" type="checkbox" checked={selection.selected.includes(policy.uuid)}
                         onChange={() => selection.toggle(policy.uuid)} />
                </td> : null}
              <td title={policy.description ? policy.description : undefined}>
                <Link to={ApiRoutes.ETHERNET.SEGMENTATION.POLICIES.DETAILS(policy.uuid)}>{policy.name}</Link>{' '}
                {filterIcon("name", policy.name)}
              </td>
              <td>
                <PolicyState state={policy.state} />
                {policy.backfill_pending ?
                  <React.Fragment>
                    {' '}<i className="fa-solid fa-circle-notch fa-spin text-muted" title={POLICY_BACKFILL_NOTICE}></i>
                  </React.Fragment> : null}
              </td>
              <td title={status ? status.description : undefined}>
                {status ? status.title : policy.status} {filterIcon("status", policy.status)}
              </td>
              <td><PolicyRuleText rule={normalizeRule(policy.scope)} options={options} scope={true} /></td>
              <td>{allowedText(policy)}</td>
              <td title={alertSettingDescription(policy)
                + (policy.status === "ACTIVE" ? "" : " Alerts are only raised while the policy is active.")}>
                {policy.alert_on_connected || policy.alert_on_attempted ?
                  alertSettingLabel(policy) : <span className="text-muted">{alertSettingLabel(policy)}</span>}
              </td>
              <td>{findings(policy, "CROSSED", policy.crossed_findings, "text-danger")}</td>
              <td>{findings(policy, "ATTEMPTED", policy.attempted_findings, undefined)}</td>
              <td>
                {last ? <span title={last.format()}>{last.fromNow()}</span> : <span className="text-muted">n/a</span>}
              </td>
              <td title={moment(policy.created_at).format()}>{moment(policy.created_at).fromNow()}</td>
              <td title={moment(policy.updated_at).format()}>{moment(policy.updated_at).fromNow()}</td>
            </tr>
          )
        })}
        </tbody>
      </table>

      <Paginator itemCount={policies.total} perPage={perPage} setPage={setPage} page={page} />
    </React.Fragment>
  )

}
