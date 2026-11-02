import React, {useEffect, useState} from "react";
import AssetsService from "../../../../../services/ethernet/AssetsService";
import DHCPFingerprintExclusionImpact from "./DHCPFingerprintExclusionImpact";

const assetsService = new AssetsService();

export const TRANSACTION_TYPES = ["Initial", "Renew", "Reboot", "Rebind"];

const EMPTY = {
  name: "",
  description: "",
  enabled: true,
  mac: "",
  transaction_types: []
};

const criteriaOf = (values) => {
  return {
    mac: values.mac,
    transaction_types: values.transaction_types
  }
}

const hasCriteria = (values) => {
  return values.mac.trim() !== "";
}

export default function DHCPFingerprintExclusionForm({organizationId, tenantId, initialValues = undefined, submitText,
                                                       onSubmit}) {

  const [values, setValues] = useState({...EMPTY, ...(initialValues ? initialValues : {})});

  const [impact, setImpact] = useState(null);
  const [criteriaError, setCriteriaError] = useState(null);
  const [previewPending, setPreviewPending] = useState(false);

  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  const criteriaKey = JSON.stringify(criteriaOf(values));

  useEffect(() => {
    if (!hasCriteria(values)) {
      setImpact(null);
      setCriteriaError(null);
      setPreviewPending(false);
      return;
    }

    setPreviewPending(true);
    const timer = setTimeout(() => {
      assetsService.previewDHCPFingerprintExclusion(organizationId, tenantId, criteriaOf(values), (result) => {
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

  const toggleTransactionType = (type) => (e) => {
    const selected = values.transaction_types.filter((t) => t !== type);
    if (e.target.checked) {
      selected.push(type);
    }

    setValues({...values, transaction_types: TRANSACTION_TYPES.filter((t) => selected.includes(t))});
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
        <div className="form-text">Why the fingerprint of this asset is expected to change, for example a dual-boot machine.</div>
      </div>

      <div className="form-check mb-4">
        <input className="form-check-input" type="checkbox" id="exclusion-enabled"
               checked={values.enabled} onChange={update("enabled")} />
        <label className="form-check-label" htmlFor="exclusion-enabled">Enabled</label>
      </div>

      <h5>Criteria</h5>

      <div className="row">
        <div className="col-md-6 mb-3">
          <label htmlFor="exclusion-mac" className="form-label">MAC Address or Vendor Prefix</label>
          <input type="text" className="form-control" id="exclusion-mac"
                 placeholder="AA:BB:CC:DD:EE:FF"
                 value={values.mac} onChange={update("mac")} />
          <div className="form-text">
            A full MAC address like <code>AA:BB:CC:DD:EE:FF</code> excludes one asset. A vendor prefix like{' '}
            <code>AA:BB:CC</code> excludes every device of that vendor, which is useful for a device class whose
            firmware randomizes DHCP options.
          </div>
        </div>

        <div className="col-md-6 mb-3">
          <label className="form-label">Transaction Types <small>Optional</small></label>
          {TRANSACTION_TYPES.map((type) => {
            return (
              <div className="form-check" key={type}>
                <input className="form-check-input" type="checkbox" id={"exclusion-type-" + type}
                       checked={values.transaction_types.includes(type)}
                       onChange={toggleTransactionType(type)} />
                <label className="form-check-label" htmlFor={"exclusion-type-" + type}>{type}</label>
              </div>
            )
          })}
          <div className="form-text">
            Limit the exclusion to fingerprint changes in these DHCP transaction types. Leave all unchecked to
            cover every type.
          </div>
        </div>
      </div>

      <h5 className="mt-2">Covered Assets</h5>

      <div className="mb-3">
        {!hasCriteria(values) ?
          <div className="alert alert-secondary mb-0">Fill in a MAC address or vendor prefix above.</div>
          : criteriaError ?
            <div className="alert alert-danger mb-0">{criteriaError}</div>
            : previewPending ?
              <div className="alert alert-secondary mb-0">
                <i className="fa-solid fa-circle-notch fa-spin"></i> Checking known assets ...
              </div>
              : <DHCPFingerprintExclusionImpact impact={impact} />}
      </div>

      {submitError ? <div className="alert alert-danger">{submitError}</div> : null}

      <button className="btn btn-primary" type="submit" disabled={!formIsReady()}>
        {isSubmitting ? <span><i className="fa-solid fa-circle-notch fa-spin"></i> Saving ...</span> : submitText}
      </button>
    </form>
  )

}
