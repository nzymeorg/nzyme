import React, {useEffect, useState} from "react";
import SpoofingService from "../../../../services/ethernet/SpoofingService";
import {ALLOWED_SPOOFING_TYPE_VALUES} from "../../../shared/filtering/validators/SpoofingTypeValidator";
import {spoofingTypeToName} from "../SpoofingType";
import SpoofingExclusionImpact from "./SpoofingExclusionImpact";

const spoofingService = new SpoofingService();

const EMPTY = {
  name: "",
  description: "",
  enabled: true,
  types: [],
  address_cidr: "",
  mac: ""
};

const criteriaOf = (values) => {
  return {
    types: values.types,
    address_cidr: values.address_cidr,
    mac: values.mac
  }
}

const hasCriteria = (values) => {
  return values.types.length > 0 || values.address_cidr.trim() !== "" || values.mac.trim() !== "";
}

export default function SpoofingExclusionForm({organizationId, tenantId, initialValues = undefined, submitText,
                                                onSubmit, isCreate = false}) {

  const [values, setValues] = useState({...EMPTY, ...(initialValues ? initialValues : {})});
  const [applyToExisting, setApplyToExisting] = useState(true);

  const [impact, setImpact] = useState(null);
  const [criteriaError, setCriteriaError] = useState(null);
  const [previewPending, setPreviewPending] = useState(false);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  const criteriaKey = JSON.stringify(criteriaOf(values));

  // Live preview, debounced.
  useEffect(() => {
    if (!hasCriteria(values)) {
      setImpact(null);
      setCriteriaError(null);
      setPreviewPending(false);
      return;
    }

    setPreviewPending(true);
    const timer = setTimeout(() => {
      spoofingService.previewExclusion(organizationId, tenantId, criteriaOf(values), (result) => {
        setImpact(result);
        setCriteriaError(null);
        setPreviewPending(false);
      }, (error) => {
        setImpact(null);
        setPreviewPending(false);
        if (error && error.response && error.response.status === 422 && error.response.data) {
          setCriteriaError(error.response.data.message);
        } else {
          setCriteriaError("Could not check the criteria.");
        }
      });
    }, 400);

    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [criteriaKey, organizationId, tenantId]);

  const update = (field) => (e) => {
    const value = e.target.type === "checkbox" ? e.target.checked : e.target.value;
    setValues({...values, [field]: value});
  }

  const toggleType = (type) => (e) => {
    const types = e.target.checked
      ? [...values.types, type]
      : values.types.filter((t) => t !== type);
    setValues({...values, types: ALLOWED_SPOOFING_TYPE_VALUES.filter((t) => types.includes(t))});
  }

  const formIsReady = () => {
    return values.name.trim().length > 0 && hasCriteria(values) && !criteriaError && !previewPending && !isSubmitting;
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
      description: values.description,
      enabled: values.enabled,
      ...criteriaOf(values)
    };

    if (isCreate) {
      payload.apply_to_existing_findings = applyToExisting && impact && impact.matching_findings > 0;
    }

    onSubmit(payload, (error) => {
      setIsSubmitting(false);
      if (error && error.response && error.response.status === 422 && error.response.data) {
        setSubmitError(error.response.data.message);
      } else {
        setSubmitError("Could not save the exclusion.");
      }
    });
  }

  return (
    <form onSubmit={submit}>
      <div className="mb-3">
        <label htmlFor="exclusion-name" className="form-label">Name</label>
        <input type="text" className="form-control" id="exclusion-name" maxLength={128}
               value={values.name} onChange={update("name")} />
      </div>

      <div className="mb-3">
        <label htmlFor="exclusion-description" className="form-label">Description <small>Optional</small></label>
        <textarea className="form-control" id="exclusion-description" rows={2}
                  value={values.description} onChange={update("description")} />
        <div className="form-text">Why this is not spoofing, for example a router redundancy protocol.</div>
      </div>

      <div className="form-check mb-4">
        <input className="form-check-input" type="checkbox" id="exclusion-enabled"
               checked={values.enabled} onChange={update("enabled")} />
        <label className="form-check-label" htmlFor="exclusion-enabled">Enabled</label>
      </div>

      <h5>Criteria</h5>
      <p className="text-muted">
        Leave a field empty to not match on it. All filled fields must match.
      </p>

      <div className="row">
        <div className="col-md-4 mb-3">
          <label className="form-label">Types</label>
          <div>
            {ALLOWED_SPOOFING_TYPE_VALUES.map((type) => {
              return (
                <div className="form-check" key={type}>
                  <input className="form-check-input" type="checkbox" id={"exclusion-type-" + type}
                         checked={values.types.includes(type)} onChange={toggleType(type)} />
                  <label className="form-check-label" htmlFor={"exclusion-type-" + type}>
                    {spoofingTypeToName(type)}
                  </label>
                </div>
              )
            })}
          </div>
          <div className="form-text">Leave all unchecked to match every type.</div>
        </div>

        <div className="col-md-4 mb-3">
          <label htmlFor="exclusion-address-cidr" className="form-label">Claimed Address or Network</label>
          <input type="text" className="form-control" id="exclusion-address-cidr"
                 placeholder="192.168.0.1"
                 value={values.address_cidr} onChange={update("address_cidr")} />
          <div className="form-text">
            The address that is being claimed. A single address like <code>192.168.0.1</code> or a network in
            CIDR notation like <code>10.0.0.0/8</code>. Use it for a virtual router address that moves between
            routers on purpose.
          </div>
        </div>

        <div className="col-md-4 mb-3">
          <label htmlFor="exclusion-mac" className="form-label">Device MAC Address</label>
          <input type="text" className="form-control" id="exclusion-mac"
                 placeholder="74:56:3C:43:77:AF"
                 value={values.mac} onChange={update("mac")} />
          <div className="form-text">
            The device doing the claiming. Use it for a failover node or a hypervisor that takes over addresses
            on purpose.
          </div>
        </div>
      </div>

      <h5 className="mt-2">Existing Findings</h5>

      <div className="mb-3">
        {!hasCriteria(values) ?
          <div className="alert alert-secondary mb-0">Fill in at least one criterion above.</div>
          : criteriaError ?
            <div className="alert alert-danger mb-0">{criteriaError}</div>
            : previewPending ?
              <div className="alert alert-secondary mb-0">
                <i className="fa-solid fa-circle-notch fa-spin"></i> Checking existing findings ...
              </div>
              : <SpoofingExclusionImpact impact={impact} />}
      </div>

      {isCreate && impact && impact.matching_findings > 0 && !criteriaError ?
        <div className="form-check mb-3">
          <input className="form-check-input" type="checkbox" id="exclusion-apply"
                 checked={applyToExisting} onChange={(e) => setApplyToExisting(e.target.checked)} />
          <label className="form-check-label" htmlFor="exclusion-apply">
            Delete the matching findings now.
          </label>
        </div> : null}

      {!isCreate && impact && impact.matching_findings > 0 ?
        <p className="text-muted">
          Existing findings are not deleted when saving. Use <em>Delete Matching Findings</em> on the exclusion
          page to remove them.
        </p> : null}

      {submitError ? <div className="alert alert-danger">{submitError}</div> : null}

      <button className="btn btn-primary" type="submit" disabled={!formIsReady()}>
        {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Saving ...</span> : submitText}
      </button>
    </form>
  )

}
