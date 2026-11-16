import React, {useEffect, useState} from "react";
import SegmentationService from "../../../../services/ethernet/SegmentationService";
import LoadingSpinner from "../../../misc/LoadingSpinner";
import FormSubmitErrorMessage from "../../../misc/FormSubmitErrorMessage";
import CardTitleWithControls from "../../../shared/CardTitleWithControls";
import {apiErrorMessage} from "../../../../util/Tools";
import {POLICY_STATUSES} from "../SegmentationFormat";
import {emptyRule, namesNothing, ruleIsEmpty, sameRule, scopeIsValid} from "./PolicyDefinition";
import PolicyRuleEditor from "./PolicyRuleEditor";
import PolicyRuleText from "./PolicyRuleText";
import PolicyAllowedCrossings from "./PolicyAllowedCrossings";
import PolicyObservedTraffic from "./PolicyObservedTraffic";
import PolicyTemplatesDialog from "./PolicyTemplatesDialog";

const segmentationService = new SegmentationService();

const STATUSES = ["AUDIT", "ACTIVE", "PAUSED"];

const defaults = () => {
  return {
    name: "",
    description: "",
    status: "AUDIT",
    alert_on_connected: false,
    alert_on_attempted: false,
    scope: emptyRule(),
    allowed: []
  };
}

export default function PolicyForm({organizationId, tenantId, initialValues = undefined, submitText, onSubmit,
                                     templatesOpen = false, onTemplatesClose = undefined}) {

  const [values, setValues] = useState({...defaults(), ...(initialValues ? initialValues : {})});
  const [options, setOptions] = useState(null);

  const [unusedAllowed, setUnusedAllowed] = useState(null);

  const [unfinishedCrossing, setUnfinishedCrossing] = useState(false);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  useEffect(() => {
    segmentationService.findSelectorOptions(organizationId, tenantId, setOptions);
  }, [organizationId, tenantId]);

  const update = (field) => (e) => {
    const value = e.target.type === "checkbox" ? e.target.checked : e.target.value;
    setValues({...values, [field]: value});
  }

  const applyTemplate = (definition) => {
    if ((!ruleIsEmpty(values.scope) || values.allowed.length > 0)
        && !confirm("Replace the scope and the allowed crossings entered so far?")) {
      return false;
    }

    setValues({
      ...values,
      name: values.name.trim() === "" ? definition.name : values.name,
      scope: definition.scope,
      allowed: definition.allowed
    });

    return true;
  }

  const allow = (rules) => {
    setValues((current) => {
      const added = [];
      rules.forEach((rule) => {
        if (!current.allowed.some((a) => sameRule(a, rule)) && !added.some((a) => sameRule(a, rule))) {
          added.push(rule);
        }
      });

      return {...current, allowed: [...current.allowed, ...added]};
    });
  }

  const emptyCrossing = values.allowed.some(namesNothing);

  const formIsReady = () => {
    return values.name.trim().length > 0 && scopeIsValid(values.scope) && !emptyCrossing && !unfinishedCrossing
      && !isSubmitting;
  }

  const submit = (e) => {
    e.preventDefault();

    if (!formIsReady()) {
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);

    const payload = {
      name: values.name.trim(),
      description: values.description.trim() === "" ? null : values.description.trim(),
      status: values.status,
      alert_on_connected: values.alert_on_connected,
      alert_on_attempted: values.alert_on_attempted,
      scope: values.scope,
      allowed: values.allowed
    };

    onSubmit(payload, (error) => {
      setIsSubmitting(false);
      setSubmitError(apiErrorMessage(error, "Could not save the policy."));
    });
  }

  if (!options) {
    return <LoadingSpinner />
  }

  // Not a form element: the time range selector of the observed traffic has inputs of its own, and Enter in one
  // of them must not save the policy.
  return (
    <React.Fragment>
      {templatesOpen ?
        <PolicyTemplatesDialog options={options}
                               organizationId={organizationId}
                               tenantId={tenantId}
                               onApply={applyTemplate}
                               onClose={onTemplatesClose} /> : null}

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Policy" />

              <div className="mb-3">
                <label htmlFor="policy-name" className="form-label">Name</label>
                <input type="text" className="form-control" id="policy-name" maxLength={64}
                       value={values.name} onChange={update("name")} />
                <div className="form-text">Shown in findings and alerts.</div>
              </div>

              <div>
                <label htmlFor="policy-description" className="form-label">Description <small>Optional</small></label>
                <textarea className="form-control" id="policy-description" rows={2}
                          value={values.description} onChange={update("description")} />
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Scope" />

              <p className="text-muted">
                The connections this policy judges. <strong>Every attempted or successful connection in scope
                that is not listed under <em>Allowed Crossings</em> below will be reported as a
                finding.</strong>
              </p>

              <PolicyRuleEditor rule={values.scope}
                                scope={true}
                                onChange={(scope) => setValues({...values, scope: scope})}
                                options={options}
                                organizationId={organizationId}
                                tenantId={tenantId}
                                idPrefix="policy-scope" />

              <div className="alert alert-info mb-0">
                <strong>In scope:</strong>{' '}
                {scopeIsValid(values.scope) ?
                  <PolicyRuleText rule={values.scope} options={options} scope={true} />
                  : "Nothing yet. Add at least one source or destination."}
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          <div className="card">
            <div className="card-body">
              <CardTitleWithControls title="Allowed Crossings" />

              <p className="text-muted">Connections in scope that are allowed.</p>

              <PolicyAllowedCrossings allowed={values.allowed}
                                      onChange={(allowed) => setValues({...values, allowed: allowed})}
                                      options={options}
                                      organizationId={organizationId}
                                      tenantId={tenantId}
                                      unused={unusedAllowed}
                                      onUnfinished={setUnfinishedCrossing} />
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
                                     scope={values.scope}
                                     allowed={values.allowed}
                                     description={"Recorded traffic in scope, evaluated against the definition as " +
                                       "entered. Nothing is saved until the policy is saved."}
                                     onAllow={allow}
                                     onResult={(result) => setUnusedAllowed(result ? result.unused_allowed : null)} />
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-xl-6">
          <div className="card h-100">
            <div className="card-body">
              <CardTitleWithControls title="Status" />

              {STATUSES.map((status) => {
                return (
                  <div className="form-check" key={status}>
                    <input className="form-check-input" type="radio" name="policy-status"
                           id={"policy-status-" + status}
                           checked={values.status === status}
                           onChange={() => setValues({...values, status: status})} />
                    <label className="form-check-label" htmlFor={"policy-status-" + status}>
                      {POLICY_STATUSES[status].title}{' '}
                      <span className="text-muted">{POLICY_STATUSES[status].description}</span>
                    </label>
                  </div>
                )
              })}
            </div>
          </div>
        </div>

        <div className="col-xl-6 mt-3 mt-xl-0">
          <div className="card h-100">
            <div className="card-body">
              <CardTitleWithControls title="Alerts" />

              <div className="form-check">
                <input className="form-check-input" type="checkbox" id="policy-alert-connected"
                       checked={values.alert_on_connected} onChange={update("alert_on_connected")} />
                <label className="form-check-label" htmlFor="policy-alert-connected">
                  Alert on accepted connections
                </label>
              </div>
              <div className="form-check">
                <input className="form-check-input" type="checkbox" id="policy-alert-attempted"
                       checked={values.alert_on_attempted} onChange={update("alert_on_attempted")} />
                <label className="form-check-label" htmlFor="policy-alert-attempted">
                  Alert on rejected or timed out connections
                </label>
              </div>
              <div className="form-text">
                With neither checked, the policy raises no alerts. Alerts are only raised while the policy is
                active. A finding for traffic to a broadcast or multicast address raises the alert for accepted
                connections: such traffic is delivered and cannot be rejected.
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="row mt-3">
        <div className="col-12">
          {emptyCrossing ?
            <div className="text-danger small mb-2">
              An allowed crossing names no source, destination or service.
            </div> : null}

          {unfinishedCrossing ?
            <div className="text-danger small mb-2">
              A new allowed crossing was not added yet.
            </div> : null}

          <button className="btn btn-primary" type="button" disabled={!formIsReady()} onClick={submit}>
            {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Saving ...</span> : submitText}
          </button>

          <FormSubmitErrorMessage message={submitError} />
        </div>
      </div>
    </React.Fragment>
  )

}
